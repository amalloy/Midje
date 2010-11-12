(ns midje.sweet.sweet-background
  (use [midje.sweet.sweet-to-semi-sweet-rewrite
	:only [one-fake-body-content
	       make-fake
	       ]]))

(defn is-arrow-form? [forms]
  (= (str (second forms)) "=>"))

(defn make-background [fake]
  (concat fake '(:type :background)))

(defn expand [forms]
  (loop [expanded []
	 in-progress forms]
    (cond (empty? in-progress)
	  expanded

	  (is-arrow-form? in-progress)
	  (let [content (one-fake-body-content in-progress)]
	    (recur (conj expanded (-> content make-fake make-background))
		   (nthnext in-progress (count content))))
	  
	  :else
	  (throw (Error. "This doesn't look like part of a background:" in-progress)))))

(defn form-first? [form desired]
  (and (list? form)
       (symbol? (first form))
       (= (name (first form)) desired)))

(defn background-form? [form]
  (form-first? form "against-background"))

(defn separate-fact [fact-forms]
  [ (apply concat (map rest (filter background-form? fact-forms)))
    (filter (comp not background-form?) fact-forms) ])
  
