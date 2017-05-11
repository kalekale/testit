(ns testit.core
  #?(:cljs (:require-macros [testit.core :refer [fact facts facts-for]]))
  #?(:clj (:require [clojure.test :refer [assert-expr testing do-report]]))
  #?(:clj (:import (clojure.lang ILookup Associative Seqable))))


;;
;; Common predicates:
;;

(def any (constantly true))
(defn truthy [v] (if v true false))
(defn falsey [v] (if-not v true false))

;;
;; Utils:
;;
#?(:clj
   (defn- seqable? [v]
     (instance? Seqable v)))

(defn deep-compare [actual expected]
  (cond
    ; they are equal?
    (= actual expected)
    true
    ; expected is fn?
    (or (symbol? expected)
        (fn? expected))
    (expected actual)
    ; both are map(like):
    (and (associative? actual)
         (associative? expected))
    (and (= (keys actual) (keys expected))
         (reduce-kv (fn [match? k expected-v]
                      (and match?
                           (contains? actual k)
                           (deep-compare (get actual k) expected-v)))
                    true
                    expected))
    ; both are sequable:
    (and (seqable? actual)
         (seqable? expected))
    (->> (map deep-compare actual expected)
         (every? truthy))
    ; none of the above, fail:
    :else false))

;;
;; fact and facts macros:
;;

(defn- name-and-body [form]
  (if (and (-> form first string?)
           (-> form count (mod 3) (= 1)))
    ((juxt first rest) form)
    [nil form]))

(defmulti assert-arrow :arrow)

(defmacro fact [& form]
  (let [[name [value arrow expected]] (name-and-body form)
        msg (or name (str (pr-str value) " " arrow " " expected))]
    `(try
       ~(assert-arrow {:arrow arrow
                       :msg msg
                       :actual value
                       :expected expected
                       :form form})
       (catch Throwable t#
         (do-report {:type :error, :message ~msg,
                     :expected '~form, :actual t#})))))

(defmacro facts [& form]
  (let [[name body] (name-and-body form)]
    `(testing ~name
       ~@(for [[value arrow expected] (partition 3 body)]
           `(fact ~value ~arrow ~expected)))))

(defmacro facts-for [& forms]
  (let [[name [form-to-test & fact-forms]] (if (and (-> forms first string?)
                                                    (-> forms count dec (mod 2) (= 1)))
                                             ((juxt first rest) forms)
                                             [nil forms])
        result (gensym)]
    `(testing ~name
       (let [~result ~form-to-test]
         ~@(for [[arrow expected] (partition 2 fact-forms)]
             `(fact ~result ~arrow ~expected))))))

;;
;; Extending clojure.test for =>, =not=> and =throw=>
;;
(defmacro format-expected [expected actual]
  `(if (fn? ~expected) '(~expected ~actual) ~expected))

(defn match-expectation? [expected actual]
  (if (fn? expected)
    (expected actual)
    (= expected actual)))

(declare =>)
(defmethod assert-arrow '=> [{:keys [expected actual msg]}]
  `(let [value# ~actual
         expected# (format-expected ~expected ~actual)]
     (if (match-expectation? ~expected value#)
       (do-report {:type :pass, :message ~msg, :expected expected#, :actual value#})
       (do-report {:type :fail, :message ~msg, :expected expected#, :actual value#}))))

(declare =not=>)
(defmethod assert-arrow '=not=> [{:keys [expected actual msg]}]
  `(let [value# ~actual
         expected# (format-expected ~expected value#)]
     (if-not (match-expectation? ~expected value#)
       (do-report {:type :pass, :message ~msg, :expected expected#, :actual value#})
       (do-report {:type :fail, :message ~msg, :expected expected#, :actual value#}))))

(def ^:dynamic *eventually-polling-ms* 50)
(def ^:dynamic *eventually-timeout-ms* 1000)

#?(:clj
   (defn eventually [expected actual]
     (let [polling *eventually-polling-ms*
           deadline (+ (System/currentTimeMillis) *eventually-timeout-ms*)]
       (loop []
         (let [v (actual)
               r (expected v)]
           (cond
             r r
             (< (System/currentTimeMillis) deadline) (do (Thread/sleep polling)
                                                         (recur))
             :else false))))))

(declare =eventually=>)
(defmethod assert-arrow '=eventually=> [{:keys [msg expected actual]}]
  `(let [expected# (format-expected ~expected ~actual)]
     (if (eventually
          (let [e# ~expected]
            (cond
              (fn? e#) e#
              ;; TODO: add special support for futures, promises, derefs
              :else (partial = e#)))
          (fn [] ~actual))
       (do-report {:type :pass, :message ~msg, :expected expected#, :actual ~actual})
       (do-report {:type :fail, :message ~msg, :expected expected#, :actual ~actual}))))

(defn cause-seq [#?(:clj ^Throwable exception
                    :cljs exception)]
  (if exception
    #?(:clj (lazy-seq
             (cons exception
                   (cause-seq (.getCause exception))))
       :cljs [exception])))

(defn exception-match? [expected exception]
  (cond
    #?(:clj (class? expected)) #?(:clj (instance? expected exception))
    #?(:clj (instance? Throwable expected)) #?(:clj (and (instance? (class expected) exception)
                                                         (= (.getMessage expected)
                                                            (.getMessage exception))))
    #?(:cljs (instance? js/Error expected)) #?(:cljs (= (.-message expected)
                                                        (.-message exception)))
    (fn? expected) (expected exception)
    (map? expected) (deep-compare (ex-data exception) expected)
    (seq expected) (->> (map exception-match? expected (cause-seq exception))
                        (every? truthy))))

(declare =throws=>)
(defmethod assert-arrow '=throws=> [{:keys [expected actual msg]}]
  `(let [expected# (format-expected ~expected ~actual)]
     (try
       ~actual
       (do-report {:type :fail, :message ~msg, :expected expected#, :actual nil})
       (catch Throwable ex#
         (if (exception-match? ~expected ex#)
           (do-report {:type :pass, :message ~msg, :expected expected#, :actual ex#})
           (do-report {:type :fail, :message ~msg, :expected expected#, :actual ex#}))))))

;;
;; contains helper:
;;

;(def ... ::and-then-some)

(defn contains [expected]
  (cond
    (map? expected)
    (fn [actual]
      (and (associative? actual)
           (reduce-kv (fn [match? k expected-v]
                        (and match?
                             (contains? actual k)
                             (deep-compare (get actual k) (contains expected-v))))
                      true
                      expected)))

    (set? expected)
    (fn [actual]
      (and (<= (count expected) (count actual))
           (reduce (fn [match? expected-v]
                     (and match? (true? (some (contains expected-v) actual))))
                   true
                   expected)))

    (vector? expected)
    (fn [actual]
      (and (or (and (= (last expected) :...)
                    (<= (dec (count expected)) (count actual)))
               (= (count actual) (count expected)))
           (reduce (fn [match? [actual-v expected-v]]
                     (if (= expected-v :...)
                       (reduced true)
                       (and match? (deep-compare actual-v (contains expected-v)))))
                   true
                   (map vector actual expected))))

    (fn? expected)
    (fn [actual]
      (expected actual))

    :else
    (fn [actual]
      (= actual expected))))

;;
;; ex-info helper:
;;

(defn ex-info? [message data]
  {:pre [(or (nil? message)
             (string? message)
             (fn? message))
         (or (nil? data)
             (map? data)
             (fn? data))]}
  (let [message-check (if (fn? message)
                        message
                        (partial = message))
        data-check (if (fn? data)
                     data
                     (partial = data))]
    (fn [e]
      (and (data-check (ex-data e))
           (message-check #?(:clj (.getMessage ^Throwable e)
                             :cljs (.-message e)))
           ))))

