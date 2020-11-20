(ns interdep.multi-repo
  "Process multiple, local subrepo deps into a unified config."
  (:require
   [interdep.impl.cli :as cli]
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
      (throw (cli/err "Registered subrepo path must be inside root repo:" %)))
   registry)
  :valid)

(defn- validate-subrepo-deps-config!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (cli/err "Only aliased paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (cli/err "Only namespaced alias keys are allowed in nested repos:" k)))
  (doseq [[_ alias] (:aliases deps)
          [_ dep]  (:extra-deps alias)]
    (when (and (deps/local-dep? dep)
               (not-any? #(= (-> dep :local/root path/strip-back-dirs) %) (:registry opts)))
      (throw (cli/err "Only registered subrepos are allowed as :local/root dep:" (:local/root dep)))))
  :valid)

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

(defn- parse-subdeps
  "Parse subrepo deps config."
  [subdir deps]
  (let [out-deps  (cleanse-deps deps)]
    (validate-subrepo-deps-config! subdir out-deps)
    (update out-deps :aliases
            (fn [aliases]
              (into {} (for [[k v] aliases]
                         [k (-> v
                                (qualify-alias-extra-paths subdir)
                                (quality-alias-extra-deps))]))))))

(defn- combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn process
  "Process root deps and registered subrepo deps.
   
   Returns map of:
      :out-deps      - Processed deps config.
      :root-deps     - Root deps.edn config that served as basis for processing.
      :subrepo-deps  - Map of registered subrepos paths to their respective deps configs."
  ([] (process {}))
  ([-opts]
   (cli/with-err-boundary "Error processing multi-repo dependencies."
     (let [{::keys [registry] :as root-deps} (deps/read-root-config)
           subdeps (atom {})]
       (binding [opts (-> opts (merge -opts) (assoc :registry registry))]
         (validate-registry-dep-paths! registry)
         (let [out-deps (reduce
                         (fn [deps subdir]
                           (let [sd (deps/read-sub-config subdir)]
                             (swap! subdeps assoc subdir sd)
                             (combine-deps deps (parse-subdeps subdir sd))))
                         (cleanse-deps root-deps)
                         registry)]
           {:out-deps     out-deps
            :root-deps    root-deps
            :subrepo-deps @subdeps}))))))
