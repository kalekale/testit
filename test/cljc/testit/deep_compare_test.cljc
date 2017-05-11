(ns testit.deep-compare-test
  (:require [clojure.test :refer [deftest]]
            [clojure.string :as str]
            [testit.core :refer [facts truthy falsey =>]]))

(def deep-compare #'testit.core/deep-compare)

(deftest basics-tests
  (facts
    (deep-compare {:a 1} {:a 1}) => truthy
    (deep-compare {:a 1} {:a 2}) => falsey
    (deep-compare {:a {:b {:c 1, :d 2}}} {:a {:b {:c 1, :d 2}}}) => truthy
    (deep-compare {:a {:b {:c 1, :d 2}}} {:a {:b {:c 1}}}) => falsey))

