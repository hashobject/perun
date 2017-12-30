(ns io.perun.manifest
  (:require [cheshire.core :refer [generate-string]]))

(defn manifest
  [{:keys [icons site-title short-title theme-color background-color display scope input-paths] :as data}]
  (let [manifest {:name site-title
                  :short_name short-title
                  :icons (for [{:keys [permalink width height mime-type]} icons]
                           {:src permalink
                            :sizes (str width "x" height)
                            :type mime-type})
                  :theme_color theme-color
                  :background_color background-color
                  :display display
                  :scope scope}]
    {:rendered (generate-string manifest)
     :input-paths input-paths}))
