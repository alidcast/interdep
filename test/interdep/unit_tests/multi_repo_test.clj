(ns interdep.unit-tests.multi-repo-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [interdep.test :as t]
   [interdep.multi-repo :as mr]))

(deftest multi-repo-test
  (testing "merges multiple registered subdir configs"
    (t/with-mock-deps {:root-deps {:interdep.multi-repo/registry ["subrepo1" "subrepo2"]}
                       :subdirs-deps {"subrepo1" {:aliases {:sub1/test :it-works}}
                                      "subrepo2" {:aliases {:sub2/test :it-works}}}}
      (is (= {:aliases {:sub1/test :it-works
                        :sub2/test :it-works}}
             (mr/process)))))

  (testing "qualifies paths in aliases"
    (t/with-mock-deps {:root-deps {:interdep.multi-repo/registry ["subrepo"]}
                       :subdirs-deps {"subrepo" {:aliases {:sub1/main {:extra-paths ["src"]}}}}}
      (is (= {:aliases {:sub1/main {:extra-paths ["./subrepo/src"]}}}
             (mr/process)))))

  (testing "only allows nested alias configs"
    (t/with-mock-deps {:root-deps {:interdep.multi-repo/registry ["subrepo"]}
                       :subdirs-deps {"subrepo" {:paths []}}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Only aliased paths and deps are allowed"
           (mr/process)))))

  (testing "only allows nested namespaced alias keys"
    (t/with-mock-deps {:root-deps {:interdep.multi-repo/registry ["subrepo"]}
                       :subdirs-deps {"subrepo" {:aliases {:sub1 :should-fail}}}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Only namespaced alias keys are allowed"
           (mr/process))))))

