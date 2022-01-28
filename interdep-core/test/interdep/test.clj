(ns interdep.test
  (:require
   [interdep.impl.cli :as cli]
   [interdep.multi-repo :as mr]))

(defmacro with-mock-deps
  "Mock root and subdirs configs."
  [{:keys [root-deps subdirs-deps]} x]
  `(with-redefs [mr/read-root-config (constantly ~root-deps)
                 mr/read-sub-config  (fn [k#] (get ~subdirs-deps k#))]
     ~x))

(defmacro without-err-boundary
  "Have cli errors be thrown rather than printed."
  [x]
  `(binding [cli/*print-err?* false]
     ~x))

