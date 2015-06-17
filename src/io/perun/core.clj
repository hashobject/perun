(ns io.perun.core
  "Utilies which can be used in base JVM and pods."
  (:require [clojure.java.io :as io]))

(defn write-to-file [out-file content]
  (doto out-file
    io/make-parents
    (spit content)))

(defn create-file [tmp filepath content]
  (let [file (io/file tmp filepath)]
    (write-to-file file content)))

;;;; map for kv collections

;; These are like ones in medley

;; borrowed from https://github.com/metosin/potpuri/blob/master/src/potpuri/core.cljx#L203-L240

(defn- editable? [coll]
  (instance? clojure.lang.IEditableCollection coll))

(defn- reduce-map [f coll]
  (if (editable? coll)
    (persistent! (reduce-kv (f assoc!) (transient (empty coll)) coll))
    (reduce-kv (f assoc) (empty coll) coll)))

(defn map-keys
  "Map the keys of given associative collection using function."
  {:added "0.2.0"}
  [f coll]
  (reduce-map (fn [xf] (fn [m k v]
                         (xf m (f k) v)))
              coll))

(defn map-vals
  "Map the values of given associative collection using function."
  {:added "0.2.0"}
  [f coll]
  (reduce-map (fn [xf] (fn [m k v]
                         (xf m k (f v))))
              coll))

(defn filter-keys
  {:added "0.2.2"}
  [pred coll]
  (reduce-map (fn [xf] (fn [m k v]
                         (if (pred k) (xf m k v) m)))
              coll))

(defn filter-vals
  {:added "0.2.2"}
  [pred coll]
  (reduce-map (fn [xf] (fn [m k v]
                         (if (pred v) (xf m k v) m)))
              coll))