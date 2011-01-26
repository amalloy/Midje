;; -*- indent-tabs-mode: nil -*-

(ns midje.util.file-position
  (:require [clojure.zip :as zip]))

(defn user-file-position 
  "Guesses the file position (basename and line number) that the user is
   most likely to be interested in if a test fails."
  []
  (second (map #(list (.getFileName %) (.getLineNumber %))
               (.getStackTrace (Throwable.)))))

(defmacro line-number-known 
  "Guess the filename of a file position, but use the given line number."
  [number]
  `[(first (user-file-position)) ~number])

(defn position-of-form
  "Guess position, using metadata of given form for the line number."
  [form]
  (line-number-known (:line (meta form))))

;; Yeah, it's not tail-recursive. So sue me.
(defn arrow-line-number [arrow-loc]
  (try (or  (-> arrow-loc zip/left zip/node meta :line)
            (-> arrow-loc zip/right zip/node meta :line)
            (inc (arrow-line-number (zip/prev arrow-loc))))
       (catch Throwable ex nil)))

