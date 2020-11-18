(ns interdep.deps.util
  "Shared api for processing deps."
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.edn :as edn]))

(defn join-path
  "Join path strings separated by single forward slash."
  [& paths]
  (str/replace
   (apply str (interpose "/" paths))
   #"\/\/" "/"))

(defn read-root-deps
  "Get root deps cofig loaded by clojure cli."
  []
  (-> "deps.edn" io/file slurp edn/read-string))

(defn read-sub-deps
  "Read a sub-directory's deps config."
  [dir]
  (-> (str dir "/deps.edn") io/file slurp edn/read-string))
