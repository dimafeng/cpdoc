(ns cpdoc.core-test
  (:require [clojure.test :refer :all]
            [cpdoc.core :refer :all]))

(deftest get-header-test
  (testing "Header retriver"
    (is (= "test" (get-header "# test\ntext")))
    (is (= nil (get-header "test\ntext")))
    ))
