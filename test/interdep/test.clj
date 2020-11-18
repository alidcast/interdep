(ns interdep.test
  (:require [interdep.deps.util :as util]))

(defmacro with-mock-deps
  "Mock root and subdir deps.edn configs."
  [{:keys [root-deps subdirs-deps]} x]
  `(with-redefs [util/read-root-deps (constantly ~root-deps)
                 util/read-sub-deps  (fn [k#] (get ~subdirs-deps k#))]
     ~x))
