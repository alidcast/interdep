(ns interdep.impl.cli
  (:require
   [clojure.string :as str])
  (:import
   [clojure.lang ExceptionInfo]))

(def ^:dynamic *print-err?* true)

(defn err
  "Throw an cli invariant error. Intended to be caught and printed."
  [& msg]
  (ex-info (str/join " " msg) {::invariant true}))

(defmacro with-err-boundary
  "Catchs any cli errors and prints them.
   No need to show error location since these are invariants, not errors in user's source code."
  [msg f]
  `(try
     ~f
     (catch ExceptionInfo e#
       (if (and *print-err?* (-> e# ex-data ::invariant))
         (binding [*out* *err*]
           (println ~msg)
           (println (.getMessage e#)))
         (throw e#)))))
