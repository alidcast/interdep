(ns interdep.multi-repo
  (:require
   [interdep.impl.deps :as deps]
   [interdep.impl.path :as path]))

(def ^:dynamic opts
  {:registry {}
   :out-dir ""})

(defn- cleanse-deps
  "Remove any custom multi-repo keys from deps config."
  [deps]
  (dissoc deps
          :interdep.multi-repo/registry
          :interdep.multi-repo/includes))

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
    (when (and (deps/local-dep? dep)
               (not-any? #(= (-> dep :local/root path/strip-back-dirs) %) (:registry opts)))
      (throw (ex-info "Only registered subrepos are allowed as :local/root dep." {:dep dep})))))

;;;; Deps processing 

(defn- qualify-alias-extra-paths
  "Make any alias extra-paths be relative to their subrepo and :out-dir."
  [alias-map subdir]
  (if-let [paths (:extra-paths alias-map)]
    (assoc alias-map :extra-paths
           (mapv #(path/join (path/make-back-dirs (path/count-foward-dirs (:out-dir opts)))
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
                      [k (if-let [local-path (deps/local-dep? v)]
                           (assoc v :local/root
                                  (path/join (path/make-back-dirs (path/count-foward-dirs (:out-dir opts)))
                                             (path/strip-back-dirs local-path)))
                           v)])))
    alias-map))

(defn- parse-sub-deps
  "Parse subrepo directory's deps config."
  [subdir]
  (let [deps (deps/read-sub-config subdir)
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
  "Process root deps and subdeps config and merge them together."
  ([] (process {}))
  ([-opts]
   (let [{:interdep.multi-repo/keys [registry] :as out-deps} (deps/read-root-config)]
    (binding [opts (-> opts (merge -opts) (assoc :registry registry))]
      (validate-registry-dep-paths! registry)
      (reduce
       combine-deps
       (cleanse-deps out-deps)
       (mapv parse-sub-deps registry))))))
