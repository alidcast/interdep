(ns interdep.plugins.multi-repo
  (:require
   [interdep.deps :as deps]
   [interdep.deps.util :as util]))

(def ^:dynamic opts
  {:root-dir "./"})

(defn- cleanse-deps
  [deps]
  (dissoc deps
          :interdep.multi-repo/registry
          :interdep.multi-repo/includes))

(defn- guard-sub-deps!
  [dir deps]
  ;; todo do not allow local deps in sub projects.
  (when (or (:paths deps)
            (:deps deps))
    (throw (ex-info "Only aliases paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in nested repos."
                    {:dir dir :alias-key k}))))

(defn- merge-subrepo-includes
  "Merge any includes in subrepo."
  [deps includes]
  (reduce
   (fn [deps include-dir]
     (let [_ (util/read-sub-deps include-dir)]
     ;; todo haven't handled this yet, includes are only for per-project builds.
       deps))
   deps
   includes))

(defn- qualify-subrepo-aliases-paths
  "Have alias extra-paths be relative to root project dir."
  [deps subdir]
  (update deps :aliases
          (fn [aliases]
            (into {} (for [[k v] aliases]
                       [k (update v :extra-paths
                                  (fn [paths] (mapv #(util/join-path (:root-dir opts) subdir %) paths)))])))))

(defn- parse-sub-deps
  "Parse subrepo directory's deps config."
  [subdir]
  (let [{:interdep.multi-repo/keys [includes] :as deps} (util/read-sub-deps subdir)
        out-deps  (cleanse-deps deps)]
    (guard-sub-deps! subdir out-deps)
    (-> out-deps
        (qualify-subrepo-aliases-paths subdir)
        (merge-subrepo-includes includes))))

(defn combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn process
  [{::deps/keys [process-opts out-deps]
    :as ctx}]
  (binding [opts (merge opts (::opts process-opts))]
    ;; todo output main deps with all includes and per project deps includes
    (let [{:interdep.multi-repo/keys [registry]} out-deps
          out-deps (cleanse-deps out-deps)]
      (assoc ctx ::deps/out-deps
             (reduce
              combine-deps
              out-deps
              (mapv parse-sub-deps registry))))))
