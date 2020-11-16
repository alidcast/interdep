(ns interdep.plugins.multi-repo
  (:require
   [interdep.deps.api :as api]))

(defn- cleanse-out-deps
  [deps]
  (dissoc deps :interdep.multi-repo/registry))

(defn- cleanse-sub-deps
  [deps]
  (dissoc deps :interdep.multi-repo/includes))

(defn- guard-module-deps!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (ex-info "Only aliases paths and deps are allowed in monorepo."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in monorepo."
                    {:dir dir :alias-key k}))))

(defn- merge-included-sub-dep
  "Merge deps config of `included-dir`, found in ::includes property."
  [clj-deps include-dir]
  ;; todo cache loaded deps. two outputs: ns-compiles and build-compiles
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
        out-deps  (cleanse-sub-deps deps)]
    (guard-module-deps! sub-dir out-deps)
    (reduce merge-included-sub-dep out-deps includes)))

(defn combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases) d1))

(defn plugin
  ":repo/modules - registers modules in monorepo that'll be parsed in build step.
   :repo/includes - includes a module in a monorepo project."
  [{:keys [out-deps] :as ctx}]
  ;; todo have .main includes all merged deps  and .deploys only project includes
  (let [{:interdep.multi-repo/keys [registry]} out-deps
        out-deps    (cleanse-out-deps out-deps)]
    (assoc ctx ::deps
           (reduce
            combine-deps
            out-deps
            (mapv parse-sub-deps registry)))))
