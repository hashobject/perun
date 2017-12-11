(ns io.perun.highlight
  (:require [clojure.java.io :as io]
            [clygments.core :as pygments]
            [net.cgrand.enlive-html :as enlive]))

;; adapted from
;; http://cjohansen.no/building-static-sites-in-clojure-with-stasis/

(defn- extract-code
  [highlighted]
  (some-> highlighted
          java.io.StringReader.
          enlive/html-resource
          (enlive/select [:pre])
          first
          :content))

(defn- highlight [node]
  (let [code (->> node :content (apply str))
        lang (-> node :attrs :class (clojure.string/replace #"language-" "") keyword)]
    (assoc node :content (-> code
                             (pygments/highlight lang :html)
                             extract-code))))

(defn highlight-code-blocks [{:keys [entry]} cls]
  (let [content (-> entry :full-path io/file slurp)]
    (assoc entry :rendered (enlive/sniptest
                            content
                            [:pre [:code (enlive/attr? :class)]] highlight
                            [:pre [:code (enlive/attr? :class)]] #(assoc-in % [:attrs :class] cls)))))
