(ns io.perun.word-count
  (:require [clojure.java.io :as io])
  (:import [org.apache.lucene.analysis.charfilter HTMLStripCharFilter]
           [org.apache.lucene.analysis.standard StandardTokenizer]
           [org.apache.lucene.analysis.tokenattributes CharTermAttribute]))

(defn word-count [{:keys [entry]}]
  (let [tokenizer (StandardTokenizer.)
        char-attr (.getAttribute tokenizer CharTermAttribute)
        content (slurp (io/file (:full-path entry)))
        html-filter (HTMLStripCharFilter. (java.io.StringReader. content))]
    (.setReader ^StandardTokenizer tokenizer html-filter)
    (.reset tokenizer)
    (let [ct (loop [c 0]
               (if-not (.incrementToken tokenizer)
                 c
                 (recur (inc c))))]
      (.close tokenizer)
      (assoc entry :word-count ct))))
