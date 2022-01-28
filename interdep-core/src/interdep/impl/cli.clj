(ns interdep.impl.cli
  "Commad line integration helpers."
  (:require
   [clojure.string :as str])
  (:import
   [clojure.lang ExceptionInfo]))

(def ^:dynamic *print-err?* true)

(defn err
  "Throw an invariant error, intended to be caught and printed."
  [& msg]
  (ex-info (str/join " " msg) {::invariant true}))

(defmacro with-err-boundary
  "Catches invariant errors and prints them.
   There's no need show full error msg as these are config errors."
  [msg f]
  ;; note: below logic is similar to how errors are handled by Clojure's cli.
  ;; https://github.com/clojure/brew-install/blob/1.10.1/src/main/clojure/clojure/run/exec.clj#L139-L154
  `(try
     ~f
     (catch ExceptionInfo e#
       (if (and *print-err?* (-> e# ex-data ::invariant))
         (binding [*out* *err*]
           (println ~msg)
           (println (.getMessage e#)))
         (throw e#)))))
