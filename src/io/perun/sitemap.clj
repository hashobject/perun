(set-env!
  :dependencies '[[org.clojure/clojure "1.6.0"]
                  [sitemap "0.2.4"]])

(ns io.perun.sitemap
  {:boot/export-tasks true}
  (:require [boot.core       :as boot]
            [boot.util       :as u]
            [io.perun.utils  :as util]
            [clojure.java.io :as io]
            [sitemap.core    :as sitemap-gen]))


(def ^:private
  +defaults+ {:filename "sitemap.xml"
              :target "public"
              :datafile "meta.edn"})

(defn sitemap-definitions [files options]
  (map
    (fn [file]
      {:loc (str (:url options) (:filename file))
       :lastmod (get file "date_modified")
       :changefreq "weekly"
       :priority 0.8})
    files))

; TODO handle collections
(defn create-sitemap [files options]
  (let [pages (sitemap-definitions files options)]
        pages))

(boot/deftask sitemap
  "Generate sitemap"
  [f filename FILENAME str "Generated sitemap filename"
   o target   OUTDIR   str "The output directory"
   d datafile DATAFILE str "Datafile with all parsed meta information"
   u url      URL      str "Base URL"]
  (let [tmp (boot/temp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [options (merge +defaults+ *opts*)
              files (util/read-files-defs fileset (:datafile options))
              sitemap-filepath (str (:target options) "/" (:filename options))
              sitemap-xml (create-sitemap files options)
              sitemap-string (sitemap-gen/generate-sitemap sitemap-xml)]
          (util/create-file tmp sitemap-filepath sitemap-string)
          (u/info (str "Generate sitemap and save to " sitemap-filepath "\n"))
          (util/commit-and-next fileset tmp next-handler))))))

