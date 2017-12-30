(ns io.perun.manifest
  (:require [cheshire.core :refer [generate-string]]))

(defn manifest
  [{:keys [icons site-title theme-color display scope input-paths] :as data}]
  (let [manifest {:name site-title
                  :icons (for [{:keys [permalink width height mime-type]} icons]
                           {:src permalink
                            :sizes (str width "x" height)
                            :type mime-type})
                  :theme_color theme-color
                  :display display
                  :scope scope}]
    {:rendered (generate-string manifest)
     :input-paths input-paths}))
