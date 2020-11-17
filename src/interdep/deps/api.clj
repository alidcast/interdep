(ns interdep.deps.api
  "Shared api for processing deps."
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.edn :as edn]))

(def ^:private cfg
  {:build-dir ".repo/main"
   :deps-file "deps.edn"})

(defn- join-path
  "Join path strings separated by single forward slash."
  [& paths]
  (str/replace
   (apply str (interpose "/" paths))
   #"\/\/" "/"))

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
