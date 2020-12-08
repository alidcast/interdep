(ns interdep.impl.path
  (:require
   [clojure.string :as str]))

(def root-path ".")

(defn join
  "Joins path strings, each separated by a single forward slash."
  [& paths]
  (-> (apply str (interpose "/" (filter #(not (str/blank? %)) paths)))
      (str/replace #"\/\/" "/")))

(defn strip-back-dirs
  "Remove any back dirs from a path string."
  [path]
  (str/replace path #"\.\.\/" ""))

(defn count-foward-dirs
  "Count how many dirs forward a path is.
   Note: no need to account for backward paths here since paths outside of root dir are not allowed."
  [path]
  (if (= path root-path)
    0
    (count (str/split path #"\/"))))

(defn make-back-dirs
  "Generate a path with given number of back dirs, e.g. '../'."
  [n]
  (if (= n 0)
    "./"
    (apply join (for [_ (range n)] ".."))))
