(ns interdep.unit-tests.multi-alias-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [interdep.test :as t]
   [interdep.multi-repo :as mr]
   [interdep.multi-alias :as ma]))

(deftest mutli-alias-test
  (testing "does not match any aliases if passed profile keys is empty"
    (is (= []
           (::ma/matched-aliases
            (ma/with-profiles
             {::mr/root-deps {::ma/profiles {:pro1 {}}}
              ::mr/main-deps {:aliases {:sub1/env1 :alias}}}
             [])))))

  (testing "does not allow final profile to not have any alias filter."
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Combined profiles must have at least one alias matcher"
         (t/without-err-boundary
          (ma/with-profiles
           {::mr/root-deps  {::ma/profiles {:pro1 {}}}
            ::mr/main-deps  {:aliases {:sub1/env1 :alias}}}
           [:pro1])))))

  (testing "matches aliases based on profile alias-ns* filter"
    (is (= [:sub1/env1]
           (::ma/matched-aliases
            (ma/with-profiles
             {::mr/root-deps  {::ma/profiles {:pro1 {:alias-ns* [:sub1]}}}
              ::mr/main-deps  {:aliases {:sub1/env1 :alias}}}
             [:pro1])))))

  (testing "matches aliases based on profile alias-name* filter"
    (is (= [:sub1/env1]
           (::ma/matched-aliases
            (ma/with-profiles
             {::mr/root-deps  {::ma/profiles {:pro1 {:alias-name* [:env1]}}}
              ::mr/main-deps  {:aliases {:sub1/env1 :alias}}}
             [:pro1])))))

  (testing "matches aliases based on profile with combined alias-ns* and alias-name* filters"
    (is (= [:sub1/env1 :sub2/env1]
           (::ma/matched-aliases
            (ma/with-profiles
             {::mr/root-deps {::ma/profiles {:pro1 {:alias-ns* [:sub1 :sub2]
                                                    :alias-name* [:env1]}}}
              ::mr/main-deps {:aliases {:sub1/env1 :alias
                                        :sub1/env2 :alias
                                        :sub2/env1 :alias
                                        :sub2/env2 :alias
                                        :sub3/env1 :alias}}}
             [:pro1])))))

  (testing "matches aliases for specified subrepo path "
    (is (= [:sub1/env1]
           (::ma/matched-aliases
            (ma/with-profiles
             {::mr/root-deps {::ma/profiles {:pro1 {:path "subrepo1" :alias-name* [:env1]}}}
              ::mr/subrepo-deps {"subrepo1"
                                 {:aliases {:sub1/env1 :alias}}}}
             [:pro1]))))))


