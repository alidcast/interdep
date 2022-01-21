(ns interdep.impl.path
  "Path helpers."
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

(defn make-back-dirs
  "Generate a path with given number of back dirs, e.g. '../'."
  [n]
  (if (= n 0)
    "./"
    (apply join (for [_ (range n)] ".."))))

(defn count-foward-dirs
  "Count how many dirs forward a path is.
   Note: no need to account for backward paths here since paths outside of root dir are not allowed."
  [path]
  (if (= path root-path)
    0
    (-> path
        strip-back-dirs
        (str/split #"\/")
        count)))

(defn append-base-dirs
  "Append missing base-path dirs to rel-path. 
   This is computed based on extra dirs the base-path has over rel-path.
   The count isn't an assurance of correct path but this replacement is sufficient for use-case so far."
  [base-path rel-path]
  (let [base-dirs (str/split base-path #"/")
        rel-dir-count  (count-foward-dirs rel-path)
        target-path (strip-back-dirs rel-path)]
    (if (> (count base-dirs) rel-dir-count)
      (str/join "/"
                (conj (subvec (str/split base-path #"/") 0 (count-foward-dirs rel-path))
                      target-path))
      target-path)))
