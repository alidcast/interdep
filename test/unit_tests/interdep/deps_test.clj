(ns interdep.unit-tests.deps-test
  (:require [clojure.test :refer [deftest testing]]))

(deftest interdep-test
  ;; todo
  (testing "only allows deps aliases")
  (testing "only allows namespace alias keys")
  (testing "properly merges deps property")
  (testing "ouputs final deps file properly"))
