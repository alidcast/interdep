(ns interdep.test
  (:require
   [interdep.impl.cli :as cli]
   [interdep.impl.deps :as deps]))

(defmacro with-mock-deps
  "Mock root and subdir deps.edn configs."
  [{:keys [root-deps subdirs-deps]} x]
  `(with-redefs [deps/read-root-config (constantly ~root-deps)
                 deps/read-sub-config  (fn [k#] (get ~subdirs-deps k#))]
     ~x))

(defmacro without-err-boundary
  "Have cli errors be thrown rather than printed."
  [x]
  `(binding [cli/*print-err?* false]
     ~x))

