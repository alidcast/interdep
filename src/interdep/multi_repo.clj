(ns interdep.multi-repo
  (:require
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
    (throw (ex-info "Only aliased paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in nested repos."
                    {:dir dir :alias-key k}))))

(defn- qualify-alias-extra-paths
  "Make any alias extra-paths be relative to root project dir."
  [alias-map subdir]
  (if-let [paths (:extra-paths alias-map)]
    (assoc alias-map :extra-paths
           (mapv #(util/join-path (:root-dir opts) subdir %) paths))
    alias-map))

(defn- quality-alias-extra-deps
  "Make any alias local deps be relative to root project dir."
  [alias-map]
  (if-let [deps (:extra-deps alias-map)]
    (assoc alias-map :extra-deps
           (into {} (for [[k v] deps]
                      [k (if-let [local-path (and (map? v) (:local/root v))]
                           (assoc v :local/root (util/join-path (:root-dir opts) local-path))
                           v)])))
    alias-map))

(defn- parse-sub-deps
  "Parse subrepo directory's deps config."
  [subdir]
  (let [deps (util/read-sub-deps subdir)
        out-deps  (cleanse-deps deps)]
    (guard-sub-deps! subdir out-deps)
    (update out-deps :aliases
            (fn [aliases]
              (into {} (for [[k v] aliases]
                         [k (-> v
                                (qualify-alias-extra-paths subdir)
                                (quality-alias-extra-deps)
                                )]))))))

(defn combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn process
  ([] (process {}))
  ([-opts]
   (binding [opts (merge opts -opts)]
     (let [{:interdep.multi-repo/keys [registry] :as out-deps} (util/read-root-deps)
           out-deps (cleanse-deps out-deps)]
       (reduce
        combine-deps
        out-deps
        (mapv parse-sub-deps registry))))))
