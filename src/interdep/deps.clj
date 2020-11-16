(ns interdep.deps
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.pprint :as pr]))

(def ^:private cfg
  {:build-dir ".repo/main"
   :deps-file "deps.edn"})

(defn- join-path
  "Join path strings separated by slash."
  [& paths]
  (apply str (interpose "/" paths)))

(defn- join-path-keys
  "Joins paths of key-values in passed map.
   Makes it easy to join config paths."
  [m & keys]
  (apply join-path (mapv #(m %) keys)))

(defn- read-root-deps
  "Get root deps cofig loaded by clojure cli."
  []
  (-> "deps.edn" io/file slurp edn/read-string))

(defn- read-module-deps
  "A a module's deps config."
  [dir]
  (-> (str dir "/deps.edn") io/file slurp edn/read-string))

(defn- cleanse-root-deps
  "Remove custom keys from root deps."
  [deps]
  (-> deps
      (dissoc :repo/modules)
      (dissoc :repo/commands)))

(defn- cleanse-module-deps
  "Remove custom keys from modules deps."
  [deps]
  (dissoc deps :repo/includes))


(defn- guard-module-deps!
  [dir deps]
  (when (or (:paths deps)
            (:deps deps))
    (throw (ex-info "Only aliases paths and deps are allowed in monorepo."
                    {:dir dir})))
  (when-let [k (some (fn [[k]] (when (not (namespace k)) k)) (:aliases deps))]
    (throw (ex-info "Only namespaced alias keys are allowed in monorepo."
                    {:dir dir :alias-key k}))))

(defn- pr-deps
  "Convert deps map to a printable string.
   Note: pr-str does output data with newlines characters, so we use output of pprint instead."
  [deps]
  (with-out-str
    ;; disable namespaced maps shorthands, #:{}, since they're typically not used in deps.edn files 
    ;; and for this use-case make the output less readable
    (binding [*print-namespace-maps* false]
      (pr/pprint deps))))

(defn- merge-module-include-deps
  "Merge deps of any `dir` found in module's deps :repo/includes property."
  [clj-deps dir]
  (let [{:keys [paths deps]} (read-module-deps dir)]
    ;; todo this is only when deploying (?)
    ; (-> clj-deps
    ;     (update :paths into (mapv #(join-path module-dir %) paths))
    ;     (update :deps into deps))
    clj-deps))

(defn- parse-module-clj-deps
  "Gets a module directory's parsed deps.edn output.
   Actions: 
     - Guards against improperly configured modules.
     - Merges deps of any :repo/includes directories."
  [dir]
  (let [{:repo/keys [includes] :as repo-deps} (read-module-deps dir)
        repo-clj-deps  (cleanse-module-deps repo-deps)]
    (guard-module-deps! dir repo-clj-deps)
    (reduce merge-module-include-deps repo-clj-deps includes)))

(defn- combine-deps
  "Combines `d2` aliases into `d1` deps map."
  [d1 d2]
  (update d1 :aliases merge (get d2 :aliases)))

;; todo expose behavior as plugins 

(defn -main
  ":repo/modules - registers modules in monorepo that'll be parsed in build step.
   :repo/includes - includes a module in a monorepo project."
  [_opts]
  ;; todo have .main includes all merged deps  and .deploys only project includes
  (let [{:repo/keys [modules] :as root-deps} (read-root-deps)
        root-clj-deps (cleanse-root-deps root-deps)
        output-path   (join-path-keys cfg :build-dir :deps-file)]
    (io/make-parents output-path)
    (spit output-path
          (pr-deps
           (reduce
            combine-deps
            root-clj-deps
            (mapv parse-module-clj-deps modules))))))

