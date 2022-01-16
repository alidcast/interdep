(ns interdep.multi-repo
  "Unify multiple subrepo deps into one config."
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [interdep.impl.tools :as tools]
   [interdep.impl.path :as path]))

;; [Note on dep path handling]:
;; Constraints:
;;  * Interdep will throw error when processsing any :local/root deps that are not registered.
;;  * All registered local deps must be inside root repo.
;; Reasoning: 
;;   We can change local paths by just counting how many deps forward a dep is registered
;;   to see how many dirs back a subrepo dep path should be changed to.

(def ^:dynamic opts
  {:registry {}
   :out-dir "."})

(defn local-dep?
  "Check if x is a local dep map."
  [x]
  (and (map? x) (:local/root x)))

(defn- cleanse-deps
  "Remove any custom multi-repo keys from deps config."
  [deps]
  (dissoc deps ::registry))

;; --- Validations 

(defn- validate-registered-dep-paths!
  "Ensure registered deps are inside root repo"
  [registry]
  (some
   #(when (re-find #"\.\.\/" %)
      (throw (tools/err "Registered subrepo path must be inside root repo:" %)))
   registry)
  :valid)

(defn- validate-subrepo-deps-config!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (tools/err "Only aliased paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (tools/err "Only namespaced alias keys are allowed in nested repos:" k)))
  (doseq [[_ alias] (:aliases deps)
          [_ dep]  (:extra-deps alias)]
    (when (and (local-dep? dep)
               (not-any? #(= (-> dep :local/root path/strip-back-dirs) %) (:registry opts)))
      (throw (tools/err "Only registered subrepos are allowed as :local/root dep:" (:local/root dep)))))
  :valid)

;; --- Deps processing 

(defn read-root-config
  "Get root deps cofig loaded by clojure cli."
  []
  (-> "deps.edn" io/file slurp edn/read-string))

(defn read-sub-config
  "Read a subrepo's deps config."
  [dir]
  (-> (str dir "/deps.edn") io/file slurp edn/read-string))

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

(defn- qualify-alias-extra-deps
  "Make any alias local deps be relative to :out-dir."
  [alias-map]
  (if-let [deps (:extra-deps alias-map)]
    (assoc alias-map :extra-deps
           (into {} (for [[k v] deps]
                      [k (if-let [local-path (local-dep? v)]
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
                                (qualify-alias-extra-deps))]))))))

(defn- combine-aliases
  "Combines dep aliases, with `d2` taking precedence over `d1`."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn process-deps
  "Process root deps and registered subrepo deps.
   
   Returns map of:
      ::main-deps    - Unified root and nested deps config.
      ::nested-deps  - Unified nested deps config.
      ::root-deps    - Root deps.edn config that served as basis for processing.
      ::subrepo-deps - Map of registered subrepos paths to their respective deps configs."
  ([] (process-deps {}))
  ([-opts]
   (tools/with-err-boundary "Error processing Interdep repo dependencies."
     (let [{::keys [registry] :as root-deps} (read-root-config)]
       (binding [opts (-> opts (merge -opts) (assoc :registry registry))]
         (validate-registered-dep-paths! registry)
         (let [subrepo-deps (atom {})
               nested-deps (reduce
                            (fn [deps subdir]
                              (let [sd (read-sub-config subdir)]
                                (swap! subrepo-deps assoc subdir sd)
                                (combine-aliases deps (parse-subdeps subdir sd))))
                            {}
                            registry)]
           {::main-deps    (-> root-deps
                               cleanse-deps
                               (combine-aliases nested-deps))
            ::root-deps    root-deps
            ::nested-deps  nested-deps
            ::subrepo-deps @subrepo-deps}))))))
