(ns interdep.unit-tests.multi-repo-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [interdep.test :as t]
   [interdep.multi-repo :as mr]))

(deftest multi-repo-test
  (testing "unifies multiple registered subdir configs"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1" "subrepo2"]}
                       :subdirs-deps {"subrepo1" {:aliases {:sub1/test :it-works}}
                                      "subrepo2" {:aliases {:sub2/test :it-works}}}}
      (is (= {::mr/main-deps {:aliases {:sub1/test :it-works
                                        :sub2/test :it-works}}
              ::mr/sub-deps {:aliases {:sub1/test :it-works
                                       :sub2/test :it-works}}
              ::mr/subrepo-deps {"subrepo1" {:aliases {:sub1/test :it-works}}
                                 "subrepo2" {:aliases {:sub2/test :it-works}}}}
             (select-keys (mr/process-deps)
                          [::mr/main-deps ::mr/sub-deps ::mr/subrepo-deps])))))

  (testing "qualifies subrepo alias extra-paths"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1"]}
                       :subdirs-deps {"subrepo1" {:aliases {:sub1/main {:extra-paths ["src"]}}}}}
      (is (= {:aliases {:sub1/main {:extra-paths ["../subrepo1/src"]}}}
             (::mr/main-deps (mr/process-deps {:out-dir ".main"}))))))

  (testing "qualifies subrepo :local/root paths when relative out-dir path is same depth"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1" "subrepo2"]}
                       :subdirs-deps {"subrepo1"
                                      {:aliases {:sub1/main
                                                 {:extra-deps {'t/dep {:local/root "../subrepo2"}}}}}}}
      (is (= {:aliases {:sub1/main {:extra-deps {'t/dep {:local/root "../subrepo2"}}}}}
             (::mr/main-deps (mr/process-deps {:out-dir ".main"}))))))
  

  (testing "qualifies subrepo :local/root paths when out-dir path is root"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1" "subrepo2"]}
                       :subdirs-deps {"subrepo1"
                                      {:aliases {:sub1/main
                                                 {:extra-deps {'t/dep {:local/root "../subrepo2"}}}}}}}
      (is (= {:aliases {:sub1/main {:extra-deps {'t/dep {:local/root "./subrepo2"}}}}}
             (::mr/main-deps (mr/process-deps {:out-dir "."}))))))

  (testing "qualifies subrepo :local/root paths when relative out-dir path is nested"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1" "subrepo2"]}
                       :subdirs-deps {"subrepo1"
                                      {:aliases {:sub1/main
                                                 {:extra-deps {'t/dep {:local/root "../subrepo2"}}}}}}}
      (is (= {:aliases {:sub1/main {:extra-deps {'t/dep {:local/root "../../../subrepo2"}}}}}
             (::mr/main-deps (mr/process-deps {:out-dir ".builds/repos/main"}))))))

  (testing "does not allow registering subrepos outside of project"
    (t/with-mock-deps {:root-deps {::mr/registry ["../subrepo1"]}}
      (is
       (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Registered subrepo path must be inside root repo:"
        (t/without-err-boundary (mr/process-deps))))))

  (testing "does not allows subrepo to have non-aliased dep configs"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1"]}
                       :subdirs-deps {"subrepo1" {:paths []}}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Only aliased paths and deps are allowed"
           (t/without-err-boundary (mr/process-deps))))))

  (testing "does not allow subrepos to have non-namespaced alias keys"
    (t/with-mock-deps {:root-deps {::mr/registry ["subrepo1"]}
                       :subdirs-deps {"subrepo1" {:aliases {:sub1 :should-fail}}}}
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Only namespaced alias keys are allowed"
           (t/without-err-boundary (mr/process-deps)))))))

