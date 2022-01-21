(ns interdep.multi-repo
  "Unify multiple subrepo deps into one config."
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [interdep.impl.cli :as cli]
   [interdep.impl.path :as path]))

;; [Note on dep path handling]:
;; Constraints:
;;  * Interdep will throw error when processsing any :local/root deps that are not registered.
;;  * All registered local deps must be inside root repo.
;; Reasoning: 
;;   We can change local paths by just counting how many deps forward a registered dep is
;;   to see how many dirs back a subrepo dep path should be changed to.

(def ^:dynamic opts
  {:registry {}
   :out-dir "."})

(defn local-dep?
  "Check if x is a local dep map."
  [x]
  (and (map? x) (:local/root x)))

(defn- cleanse-root-deps
  "Remove any custom multi-repo keys from deps config."
  [deps]
  (dissoc deps ::registry))

;; --- Validations 

(defn- validate-registered-dep-paths!
  "Ensure registered deps are inside root repo"
  [registry]
  (some
   #(when (re-find #"\.\.\/" %)
      (throw (cli/err "Registered subrepo path must be inside root repo:" %)))
   registry)
  :valid)

(defn- validate-subrepo-deps-config!
  [deps]
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (cli/err "Only namespaced alias keys are allowed in nested repos:" k)))
  (doseq [[_ alias] (:aliases deps)
          [_ dep]  (:extra-deps alias)]
    (when (and (local-dep? dep)
               (not-any? #(= (-> dep :local/root path/strip-back-dirs) %) (:registry opts)))
      (throw (cli/err "Only registered subrepos are allowed as :local/root dep:" (:local/root dep)))))
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

(defn update-key
  "Update map only if key already exists."
  [m k f & args]
  (if (get m k)
    (apply (partial update m k f) args)
    m))

(defn map-vals
  "Update each key-value pair in a map."
  [m f]
  (into {} (for [[k v] m]
             (f k v))))

(defn- qualify-subdir-path [subdir path]
  (path/join (path/make-back-dirs (path/count-foward-dirs (:out-dir opts)))
             subdir
             path))

(defn qualify-subdir-local-dep [path]
  (path/join (path/make-back-dirs (path/count-foward-dirs (:out-dir opts)))
             (path/strip-back-dirs path)))

(defn- update-subdir-paths [subdir paths]
  (mapv #(qualify-subdir-path subdir %)
        paths))

(defn- update-subdir-deps [deps]
  (map-vals deps
            (fn [k v]
              [k
               (if-let [path (:local/root v)]
                 (assoc v :local/root (qualify-subdir-local-dep path))
                 v)])))

(defn- parse-subdeps
  "Parse subrepo deps config.
   Updates paths to be relative to output directory."
  [subdir deps]
  (validate-subrepo-deps-config! deps)
  (-> deps
      (update-key :paths #(update-subdir-paths subdir %))
      (update-key :deps update-subdir-deps)
      (update-key :aliases
                  (fn [m]
                    (map-vals
                     m
                     (fn [k v]
                       [k (-> v
                              (update-key :extra-paths #(update-subdir-paths subdir %))
                              (update-key :extra-deps update-subdir-deps))]))))))

(defn- combine-deps
  "Combines dep aliases, with `d2` taking precedence over `d1`."
  [d1 d2]
  (let [{:keys [paths deps aliases]} d2]
   (cond-> d1
     paths (update :paths into paths)
     deps (update :deps merge deps)
     aliases (update :aliases merge aliases))))

(defn process-deps
  "Process root deps and registered subrepo deps.
   
   Returns map of:
      ::main-deps    - Unified root and nested deps config.
      ::nested-deps  - Unified nested deps config.
      ::root-deps    - Root deps.edn config that served as basis for processing.
      ::subrepo-deps - Map of registered subrepos paths to their respective deps configs."
  ([] (process-deps {}))
  ([-opts]
   (cli/with-err-boundary "Error processing Interdep repo dependencies."
     (let [{::keys [registry] :as root-deps} (read-root-config)]
       (binding [opts (-> opts (merge -opts) (assoc :registry registry))]
         (validate-registered-dep-paths! registry)
         (let [subrepo-deps (atom {})
               nested-deps (reduce
                            (fn [deps subdir]
                              (let [sd (read-sub-config subdir)]
                                (swap! subrepo-deps assoc subdir sd)
                                (combine-deps deps (parse-subdeps subdir sd))))
                            {}
                            registry)]
           {::main-deps    (-> root-deps
                               cleanse-root-deps
                               (combine-deps nested-deps))
            ::root-deps    root-deps
            ::nested-deps  nested-deps
            ::subrepo-deps @subrepo-deps}))))))
