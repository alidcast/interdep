(ns interdep.test
  (:require [interdep.impl.deps :as deps]))

(defmacro with-mock-deps
  "Mock root and subdir deps.edn configs."
  [{:keys [root-deps subdirs-deps]} x]
  `(with-redefs [deps/read-root-config (constantly ~root-deps)
                 deps/read-sub-config  (fn [k#] (get ~subdirs-deps k#))]
     ~x))
