(ns testit.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            testit.core-test
            testit.deep-compare-test
            testit.eventually-test
            testit.facts-test))

(enable-console-print!)

(doo-tests 'testit.core-test
           'testit.deep-compare-test
           'testit.eventually-test
           'testit.facts-test)
