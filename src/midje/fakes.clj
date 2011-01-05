;; -*- indent-tabs-mode: nil -*-

(ns midje.fakes
  (:use [clojure.contrib.seq-utils :only [find-first]]
        clojure.test
        clojure.contrib.error-kit
        [midje.util report file-position form-utils]
        [midje.checkers.util :only [captured-exception]]
        [midje.checkers.chatty :only [chatty-checker-falsehood? chatty-checker?]]
        [midje.checkers.extended-equality :only [extended-= extended-list-=]])
  (:require [clojure.zip :as zip]))


(defn common-to-all-fakes [var-sym] 
  `{:function (var ~var-sym)
    :count-atom (atom 0)
    :file-position (user-file-position)})

(defn make-fake-map 
  [var-sym special-to-fake-type user-override-pairs]
  (merge
   (common-to-all-fakes var-sym)
   special-to-fake-type
   (apply hash-map-duplicates-ok user-override-pairs)))

(defn unique-function-vars [fakes]
  (distinct (map #(:function %) fakes))
)

(defmulti matches-call? (fn [fake faked-function args]
                          (:type fake)))

(defmethod matches-call? :not-called
  [fake faked-function args]
  (= faked-function (fake :function)))

(defmethod matches-call? :default
  [fake faked-function args]
  (and (= faked-function (fake :function))
       (= (count args) (count (fake :arg-matchers)))
       (extended-list-= args (fake :arg-matchers))))


(defn find-matching-call [faked-function args fakes]
  (find-first #(matches-call? % faked-function args) fakes)
)

(defn call-faker [faked-function args fakes]
  "This is the function that handles all mocked calls."
  (let [found (find-matching-call faked-function args fakes)]
    (if-not found 
      (do 
        (clojure.test/report {:type :mock-argument-match-failure
                 :function faked-function
                 :actual args
                 :position (:file-position (first fakes))}))
      (do 
        (swap! (found :count-atom) inc)
        ((found :result-supplier)))))
  )

(defn binding-map [fakes]
  (reduce (fn [accumulator function-var] 
              (let [faker (fn [& actual-args] (call-faker function-var actual-args fakes))]
                (assoc accumulator function-var faker)))
          {}
          (unique-function-vars fakes))
)

(defn fake-count [fake] (deref (:count-atom fake)))

(defmulti call-count-incorrect? :type)

(defmethod call-count-incorrect? :fake
  [fake]
  (zero? @(fake :count-atom)))

(defmethod call-count-incorrect? :not-called
  [fake]
  (not (zero? @(fake :count-atom))))

(defmethod call-count-incorrect? :background
  [fake]
  false)

(defn check-call-counts [fakes]
  (doseq [fake fakes]
    (if (call-count-incorrect? fake)
      (do
        (report {:type :mock-incorrect-call-count
                 :expected-call (fake :call-text-for-failures)
                 :position (:file-position fake)
                 :expected (fake :call-text-for-failures)}))))
)

;; TODO: I'm not wild about signalling failure in two ways: by report() and by
;; return value. Fix this when (a) we move away from clojure.test.report and
;; (b) we figure out how to make fact() some meaningful unit of reporting.
;;
;; Later note: this doesn't actually work well anyway when facts are nested within
;; larger structures. Probably fact should return true/false based on interior failure
;; counts.
(defn check-result [actual call]
  (cond (extended-= actual (call :expected-result))
        (do (report {:type :pass})
            true)

        (fn? (call :expected-result))
        (do (report (merge {:type :mock-expected-result-functional-failure
                            :position (call :file-position)
                            :expected (call :expected-result-text-for-failures) }
                           (if (chatty-checker? (call :expected-result))
                             (do
;                              (prn call)
;                              (prn actual)
                               (let [chatty-result ((call :expected-result) actual)]
                                 (if (map? chatty-result)
                                   chatty-result
                                   {:actual actual
                                    :notes ["Midje program error. Please report."
                                            (str "A chatty checker returned "
                                                 (pr-str chatty-result)
                                                 " instead of a map.")]})))
                             {:actual actual})))
            false)
        
        :else
        (do 
          (report {:type :mock-expected-result-failure
                   :position (call :file-position)
                   :actual actual
                   :expected (call :expected-result) })
          false))
)

(defmacro capturing-exception [form]
  `(try ~form
        (catch Throwable e#
          (captured-exception e#))))

;; TODO: Making everything into a function is a bit silly, given that
;; extended-= already knows how to deal with functions on the right-hand-side.
(defn arg-matcher-maker [expected]
  "Based on an expected value, generates a function that returns true if the 
   actual value matches it."
;  (prn "arg matcher:" expected)
  (fn [actual] (extended-= actual expected)))

