(ns io.perun.core-test
  (:require [clojure.test :refer :all]
            [io.perun.core :refer :all]
            [clojure.java.io :as io]))

(deftest absolutize-url-test
  (is (= "/index.html" (absolutize-url "index.html")))
  (is (= "/index.html" (absolutize-url "/index.html"))))

(deftest relativize-url-test
  (is (= "index.html" (relativize-url "index.html")))
  (is (= "index.html" (relativize-url "/index.html"))))

(deftest create-filepath-test
  (is (= (.getPath (io/file "index.html"))
         (create-filepath "index.html")))
  (is (= (.getPath (io/file "public" "index.html"))
         (create-filepath "public" "index.html"))))

(deftest url-to-path-test
  (is (= (.getPath (io/file "index.html")) (url-to-path "index.html")))
  (is (= (.getPath (io/file "public" "index.html")) (url-to-path "public/index.html")))
  (is (= (.getPath (io/file "public" "index.html")) (url-to-path "public//index.html"))))
