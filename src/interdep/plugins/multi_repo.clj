(ns interdep.plugins.multi-repo
  (:require
   [interdep.deps :as deps]
   [interdep.deps.api :as api]))

(defn- cleanse-deps
  [deps]
  (dissoc deps
          :interdep.multi-repo/registry
          :interdep.multi-repo/includes))

(defn- guard-sub-deps!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (ex-info "Only aliases paths and deps are allowed in nested repos."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in nested repos."
                    {:dir dir :alias-key k}))))

(defn- merge-included-sub-dep
  "Merge deps config of `included-dir`, found in ::includes property."
  [clj-deps include-dir]
  ;; todo maybe cache loaded deps. two outputs: ns-compiles and build-compiles
  (let [_ (api/read-sub-deps include-dir)]
    ; (-> clj-deps
    ;     (update :paths into (mapv #(join-path module-dir %) paths))
    ;     (update :deps into deps))
    clj-deps))

(defn- parse-sub-deps
  "Parse subdirectory's deps.edn config.
   Actions: 
     - Guards against improperly configured modules.
     - Merges deps of any :repo/includes directories."
  [sub-dir]
  (let [{:interdep.multi-repo/keys [includes] :as deps} (api/read-sub-deps sub-dir)
        out-deps  (cleanse-deps deps)]
    (guard-sub-deps! sub-dir out-deps)
    (reduce merge-included-sub-dep out-deps includes)))

(defn combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

(defn plugin
  [{::deps/keys [out-deps] :as ctx}]
  ;; todo output main deps with all includes and per project deps includes
  (let [{:interdep.multi-repo/keys [registry]} out-deps
        out-deps (cleanse-deps out-deps)]
    (assoc ctx ::deps/out-deps
           (reduce
            combine-deps
            out-deps
            (mapv parse-sub-deps registry)))))
