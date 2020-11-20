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
  "Catches any cli errors and prints them.
   No need to show error msg with source code location, since these are invariants, not bugs in user's code."
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
