(ns interdep.deps.api
  "Shared api for processing deps."
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

(defn deps-build-path
  "Get deps build path."
  []
  (join-path-keys cfg :build-dir :deps-file))

(defn read-root-deps
  "Get root deps cofig loaded by clojure cli."
  []
  (-> "deps.edn" io/file slurp edn/read-string))

(defn read-sub-deps
  "Read a sub-directory's deps config."
  [dir]
  (-> (str dir "/deps.edn") io/file slurp edn/read-string))

(defn ppr-str
  "Convert edn to a readable string.
   Note: pr-str does output data with newlines characters, so we use output of pprint instead."
  [edn]
  (with-out-str
    ;; disable namespaced maps shorthands, #:{}, since they're typically not used in deps.edn files 
    ;; and for this use-case make the output less readable
    (binding [*print-namespace-maps* false]
      (pr/pprint edn))))
