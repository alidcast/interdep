(ns interdep.multi-repo
  (:require
   [clojure.string :as str]
   [interdep.deps.util :as util]))

(def ^:dynamic opts
  {:registry {}
   :out-dir ""})

(defn- cleanse-deps
  "Remove any custom multi-repo keys from deps config."
  [deps]
  (dissoc deps
          :interdep.multi-repo/registry
          :interdep.multi-repo/includes))

(defn- local-dep?
  "Check whether x is a local dep map."
  [x]
  (and (map? x) (:local/root x)))

(defn strip-path-back-dirs
  "Remove any back dirs from a path string."
  [path]
  (str/replace path #"\.\.\/" ""))

(defn- count-path-foward-dirs
  "Count how many dirs forward a path is.
   Note: no need to account for backward paths here since paths outside of root dir are not allowed."
  [path]
  (count (str/split path #"\/")))

(defn- path-back-dirs 
  "Generate a path with given number of back dirs, e.g. '../'."
  [n]
   (apply util/jpath (for [_ (range n)] "..")))

;;;; Validations 

;; Note: since Interdep does not allow unregistered :local/root deps and registered subrepos can't 
;; be outside root repo, in practice all deps are inside repo itself.
;; This constraint makes it a bit easier to change local paths, since we can count how many 
;; dirs foward a registered dep is to see how many dirs back a path should be changed to.

(defn- validate-registry-dep-paths!
  [registry]
  (some
   #(when (re-find #"\.\.\/" %)
      (throw (ex-info "Registered subrepos must be inside their root repo." {:path %})))
   registry))

(defn- validate-subrepo-deps-config!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (ex-info "Only aliased paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in nested repos."
                    {:dir dir :alias-key k})))
  
  (doseq [[_ alias] (:aliases deps)
          [_ dep]  (:extra-deps alias)]
    (when (and (local-dep? dep)
               (not-any? #(= (-> dep :local/root strip-path-back-dirs) %) (:registry opts)))
      (throw (ex-info "Only registered subrepos are allowed as :local/root dep." {:dep dep})))))

;;;; Deps processing 

(defn- qualify-alias-extra-paths
  "Make any alias extra-paths be relative to their subrepo and :out-dir."
  [alias-map subdir]
  (if-let [paths (:extra-paths alias-map)]
    (assoc alias-map :extra-paths
           (mapv #(util/jpath (path-back-dirs (count-path-foward-dirs (:out-dir opts)))
                              subdir
                              %)
                 paths))
    alias-map))

(defn- quality-alias-extra-deps
  "Make any alias local deps be relative to :out-dir."
  [alias-map]
  (if-let [deps (:extra-deps alias-map)]
    (assoc alias-map :extra-deps
           (into {} (for [[k v] deps]
                      [k (if-let [local-path (local-dep? v)]
                           (assoc v :local/root (util/jpath (path-back-dirs (count-path-foward-dirs (:out-dir opts)))
                                                            (strip-path-back-dirs local-path)))
                           v)])))
    alias-map))

(defn- parse-sub-deps
  "Parse subrepo directory's deps config."
  [subdir]
  (let [deps (util/read-sub-deps subdir)
        out-deps  (cleanse-deps deps)]
    (validate-subrepo-deps-config! subdir out-deps)
    (update out-deps :aliases
            (fn [aliases]
              (into {} (for [[k v] aliases]
                         [k (-> v
                                (qualify-alias-extra-paths subdir)
                                (quality-alias-extra-deps)
                                )]))))))

(defn- combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn process
  ([] (process {}))
  ([-opts]
   (let [{:interdep.multi-repo/keys [registry] :as out-deps} (util/read-root-deps)]
    (binding [opts (-> opts (merge -opts) (assoc :registry registry))]
      (validate-registry-dep-paths! registry)
      (reduce
       combine-deps
       (cleanse-deps out-deps)
       (mapv parse-sub-deps registry))))))
