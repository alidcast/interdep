(ns interdep.impl.deps
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]))

(defn local-dep?
  "Check whether x is a local dep map."
  [x]
  (and (map? x) (:local/root x)))

(defn read-root-config
  "Get root deps cofig loaded by clojure cli."
  []
  (-> "deps.edn" io/file slurp edn/read-string))

(defn read-sub-config
  "Read a subrepo's deps config."
  [dir]
  (-> (str dir "/deps.edn") io/file slurp edn/read-string))
