(ns interdep.impl.path
  (:require
   [clojure.string :as str]))

(defn join
  "Join path strings separated by single forward slash."
  [& paths]
  (str/replace
   (apply str (interpose "/" paths))
   #"\/\/" "/"))

(defn strip-back-dirs
  "Remove any back dirs from a path string."
  [path]
  (str/replace path #"\.\.\/" ""))

(defn count-foward-dirs
  "Count how many dirs forward a path is.
   Note: no need to account for backward paths here since paths outside of root dir are not allowed."
  [path]
  (count (str/split path #"\/")))

(defn make-back-dirs
  "Generate a path with given number of back dirs, e.g. '../'."
  [n]
  (apply join (for [_ (range n)] "..")))
