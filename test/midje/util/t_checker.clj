(ns midje.util.t-checker
  (:use [midje.sweet])
  (:use [midje.test-util]))

(facts "about function-aware equality"
  (function-aware-= 1 2) => falsey
  (function-aware-= 1 odd?) => truthy

  (let [checker (fn [expected] (chatty-checker [actual] (> (inc actual) expected)))]
    (function-aware-= 5 ((checker 5) 4)) => falsey))

(facts "about truthy"
  true => truthy
  1 => truthy
  (truthy false) => false
  (truthy nil) => false)

(facts "about falsey"
  false => falsey
  nil => falsey
  (falsey true) => false
  (falsey 1) => false)

(facts "about anything"
  true => anything
  false => anything
  even? => anything)

(facts "about exactly"
  true => (exactly true)
  ( (exactly 2) 2) => truthy
  ( (exactly 1) 2) => falsey
  even? => (exactly even?))

(facts "about in-any-order"
  [] => (in-any-order [])
  [1] => (in-any-order [1])
  '(2 1) => (in-any-order [1 2])
  [ {:a 1} {:b 2} ] => (in-any-order [{:b 2} {:a 1}])

  ( (in-any-order [1 2]) [1 2 3]) => falsey
  ( (in-any-order [1 2]) [1]) => falsey
  ( (in-any-order [1 2]) [1 3]) => falsey
  
  ( (in-any-order [1 2 2 3]) [1 2 3 3]) => falsey
  ( (in-any-order [2 1 3 2]) [1 2 3 3]) => falsey)

(facts "about map-containing"
  {:a 1 :b 2} => (map-containing {:a 1 :b 2})
  {:a 1 :b 2 :c 3} => (map-containing {:a 1 :b 2})

  ( (map-containing {:a 1 :b 2})  {:a 1}) => falsey
  ( (map-containing {:a 1 :b 2})  {:a 1 :b 3}) => falsey)

(facts "about only-maps-containing-test"
  ( (only-maps-containing {:a 1 :b 2}) [{:a 1 :b 2} {:extra true}]) => falsey
  ( (only-maps-containing {:a 1 :b 2}  {:extra true}) [{:a 1 :b 2}]) => falsey

  [{:a 1 :b 2} {:extra 1}] => (only-maps-containing {:extra 1} {:a 1})
  [{:a 1 :b 2} {:a 1 :b 22}] => (only-maps-containing {:b 2} {:b 22})
  [{:a 1 :b 2} {:a 1 :b 22}] => (only-maps-containing [{:b 2} {:b 22}])
  ( (only-maps-containing {:b 2} {:b 22}) [{:b 2} {:b 33}]) => falsey)

(facts "about maps-containing"
  ( (maps-containing {:a 1 :b 2}  {:extra true}) [{:a 1 :b 2}]) => falsey

  [{:a 1 :b 2} {:extra 1}] => (maps-containing {:extra 1} {:a 1})
  [{:a 1 :b 2} {:a 1 :b 22}] => (maps-containing {:b 2} {:b 22})
  [{:a 1 :b 2} {:a 1 :b 22} {:a 1 :b 33}] => (maps-containing {:b 2} {:b 22})
  [{:a 1 :b 2} {:a 1 :b 22} {:a 1 :b 33}] => (maps-containing [{:b 2} {:b 22}])
  ( (maps-containing {:b 2} {:b 22}) [{:b 2} {:b 33}]) => falsey)


(defn throw-exception
  ([] (throw (NullPointerException.)))
  ([message] (throw (Error. message)))
)

(facts "about throws"
  (throw-exception) => (throws NullPointerException)
  (throw-exception "hi") => (throws Error "hi"))
(after-silently 
 (fact 
   (throw-exception "throws Error") => (throws NullPointerException)
   (throw-exception "throws Error") => (throws Error "bye"))
 (fact 
   @reported => (two-of checker-fails)))

;; Unexpected exceptions
(after-silently
 (facts
   (throw-exception "throws Error") => anything
   (throw-exception "throws Error") => falsey
   (throw-exception "throws Error") => truthy)
 (fact
   @reported => (three-of checker-fails)))

(facts "about chatty-checking utility functions"
  (tag-as-chatty-falsehood [5]) => chatty-checker-falsehood?

  (chatty-worth-reporting-on? 1) => falsey 
  (chatty-worth-reporting-on? '()) => falsey
  (chatty-worth-reporting-on? '(f)) => truthy
  (chatty-worth-reporting-on? ''(f)) => truthy
  (chatty-worth-reporting-on? '[f]) => falsey

  (chatty-untease 'g-101 '()) => [[] []]
  
  (chatty-untease 'g-101 '(1 (f) 33 (+ 1 2))) =>
                [ '( (f) (+ 1 2))  '(1 (g-101 0) 33 (g-101 1))  ])
  

;; The form of chatty checkers

(def actual-plus-one-equals-4 (chatty-checker [actual] (= (inc actual) 4)))
(def no-longer-limited-form (chatty-checker [actual] (= (inc actual) 4 (+ 2 actual))))

(facts "about the form of chatty-checkers"
  actual-plus-one-equals-4 => chatty-checker?
  no-longer-limited-form => chatty-checker?)

(facts "about what chatty-checkers return"
  (actual-plus-one-equals-4 3) => true
   
  (let [result (actual-plus-one-equals-4 4)]
    result => chatty-checker-falsehood?
    result => {:actual 4
  	      :intermediate-results [ ['(inc actual) 5] ] })

  (let [result (no-longer-limited-form 4)]
    result => chatty-checker-falsehood?
    result => {:actual 4
  	      :intermediate-results [ ['(inc actual) 5] ['(+ 2 actual) 6] ]}))
    
(facts "about of-functions"
  [ 33 33 ] => (two-of 33)
  
  [ 1 3 ] => (n-of odd? 2)
  ( (n-of odd? 1) [1 3]) => chatty-checker-falsehood?
  ( (n-of odd? 3) [1 2 3]) => chatty-checker-falsehood?

  [1 1 3 3 5 5 7 7 9 9] => (ten-of odd?)
  [1 1 3 3 5 5 7 7 9] => (nine-of odd?)
  [1 1 3 3 5 5 7 7] => (eight-of odd?)
  [1 1 3 3 5 5 7] => (seven-of odd?)
  [1 1 3 3 5 5] => (six-of odd?)
  [1 1 3 3 5] => (five-of odd?)
  [1 1 3 3] => (four-of odd?)
  [1 1 3] => (three-of odd?)
  [1 1] => (two-of odd?)
  [1] => (one-of odd?))

(facts "about contains"
  "maps"
  {} => (contains {})
  {:k :v} => (contains {})
  {:k :v, 1 2} => (contains {:k :v})
  {:k :v, 1 2} => (contains {1 even?})
  ( (contains {:k :v}) {}) => falsey

  "lists"
  '() => (contains '())
  '(1) => (contains '()) 
  '(1) => (contains '(1)) 
  '(1 2 3) => (contains '(1))
  '(1 2 3) => (contains '(2))
  '(1 2 3) => (contains '(3))
  '(1 2 3) => (contains '(2 3))
  '(3 2 1) => (contains '(1))
  '(1 3 1 2) => (contains '(1 2))
  '(1 3 2 3 1 2) => (contains '(1 2))
  ( (contains '(1 2)) '(1 3 2 3)) => falsey
  ( (contains '(1)) '()) => falsey
  ( (contains '(1 2)) '(1)) => falsey
  ( (contains '(1)) '(2)) => falsey

  '(1 2 3) => (contains (list odd? even? odd?))
  ( (contains '(1 2)) '(3 2 1)) => falsey ; order matters
  ( (contains '(1 2 2 1)) '(1 2 1)) => falsey ; duplicates matter
  ( (contains '(1 2 1)) '(1 2 2 1)) => falsey ; duplicates matter

  "vectors"
  [3 2 1] => (contains [1])
  ( (contains [1 2]) [3 2 1]) => falsey ; order matters
  ( (contains [2 2]) [2]) => falsey ; duplicates matter

  "mixtures"
  [3 2 1] => (contains '(1))
  [3 2 1] => (contains '(1))

  "strings"
  "abc" => (contains "bc")
  "ab" => (contains "ab")
  ( (contains "ab") "ba") => falsey
  ( (contains "ab") "a") => falsey

  "strings can match expressions"
  ["a" "bc" "c"] => (contains "bc")

  "regexp"
  "abc" => (contains #"bc")
  "ab" => (contains #"ab")
  "ab" => (contains #"..")
  "ab" => (contains #".")
  ( (contains #"ab") "ba") => falsey
  ( (contains #"ab") "a") => falsey

  ;; Since #"regexp" is not = to #"regexp", no point in following:
  ;; [#"a" #"bc" #"c"] => (contains #"bc")

  ;; "sets"
  ;; #{3 2 1} => (contains #{1})
  ;; #{3 2 1} => (contains [1])   ; expected needn't be set
  ;; #{3 2 1} => (contains [odd?])

  "individual elements"
  [1 2 3] => (contains 2)
  [1 2 3] => (contains even?)
  )

