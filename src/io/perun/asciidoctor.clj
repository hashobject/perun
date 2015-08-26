;;
;; Author:: Nico Rikken (<nico@nicorikken.eu>)
;; Copyright:: Copyright (c) 2015 Mpare B.V.
;; License:: Eclipse Public License 1.0
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.
;;

;; Based on similar markdown parser

(ns io.perun.asciidoctor
  (:require [boot.util       :as u]
            [io.perun.core   :as perun]
            [clojure.java.io :as io]
            [clojure.string  :as str]
            [boot.core       :as boot :refer [deftask]]
            [clj-yaml.core   :as yaml]
            [boot.jruby      :refer [jruby make-jruby]];fixme: can be removed
            [boot.pod        :as pod])
  (:import java.util.Properties))

(defn substr-between
  "Find string that is nested in between two strings. Return first match.
  Copied from https://github.com/funcool/cuerdas"
  [s prefix suffix]
  (cond
    (nil? s) nil
    (nil? prefix) nil
    (nil? suffix) nil
    :else
    (some-> s
            (str/split prefix)
            second
            (str/split suffix)
            first)))

(defn change-ext [filename input-ext output-ext]
  (-> filename
      str/reverse
      (str/replace-first (str/reverse input-ext) (str/reverse output-ext))
      str/reverse))

(defn parse-file-metadata [fileset file options]
  "Parses the asciidoc file and retrieves metadata, from the yaml-header and
  from the file content itself. The function returns a meta-map to be added to
  the fileset metadata"
  (let [file-content (slurp file)
        filename     (.getName file)
        htmlname     (change-ext filename ".adoc" ".html")
        absfilename  (->> fileset
                          boot/output-files
                          (boot/by-name [htmlname])
                          (mapv #(.getAbsolutePath (boot/tmp-file %)))
                          first)
        metadata     (if-let [metadata-str (substr-between file-content
                                                           #"////\n" #"////\n")]
                       (if-let [parsed-yaml (yaml/parse-string metadata-str)]
                         (assoc parsed-yaml :original true)
                         {:original true})
                       {:original true})
;Fixme: resolve the metadata handling based on the options, either using
;       `include` or `content`. Also handling unknown values, and too long strs.
;        _ (println (:transfer-mode options))
        metadata     (assoc metadata :include absfilename)]
    {filename metadata}))

(defonce ^:private clj-rb-version
  (let [props (doto (Properties.)
                (.load (-> "META-INF/maven/clj.rb/clj.rb/pom.properties"
                         io/resource
                         io/reader)))]
    (.getProperty props "version")))

(def ^:private asciidoctor-deps ;fixme maybe create pod in main perun.clj
  [['clj.rb clj-rb-version]])

(defn- create-pod [deps]
  (-> (boot/get-env)
      (update-in [:dependencies] into deps)
      pod/make-pod
      ;future
      ))

(defn asciidoc-to-html [fileset file options]
  "Converts the asciidoc file into a fileset consisting of the html file and
  additional output files."
  ;This function is based off of jruby-boot.
  (let [pod (create-pod asciidoctor-deps)
        tgt (boot/tmp-dir!)
        rsc (boot/tmp-dir!)]
    (boot/empty-dir! tgt)
    (u/info "Processing Asciidoc: %s\n" (.getName file))
    (pod/with-eval-in pod
      (require
       '[clj.rb          :as rb]
       '[clojure.java.io :as io])
      (let [rt   (rb/runtime {:preserve-locals? true})
            gems [["asciidoctor"]
                  ["asciidoctor-diagram"]]]
        (try
          (rb/setenv      rt "BOOT_TGT_PATH" ~(.getAbsolutePath tgt))
          (rb/setenv      rt "BOOT_RSC_PATH" ~(.getAbsolutePath rsc))
          (doseq [[name version] gems]
            (rb/install-gem rt name version))
          (rb/setvar      rt "$file" ~(.getAbsolutePath file))
          (rb/eval-file   rt (-> "asciidoctor/render-asciidoc.rb"
                                  io/resource
                                  io/file))
          (finally
            (rb/shutdown-runtime rt)))))
    (-> fileset
        (boot/add-resource tgt)
        (boot/add-resource rsc)
        boot/commit!)))

(defn process-file [fileset file options]
  "Processes a single asciidoc file and returns a fileset which includes
  the metadata."
  (let [prev-meta    (perun/get-meta                fileset)
        html-fileset (asciidoc-to-html              fileset file options)
        meta-map     (parse-file-metadata      html-fileset file options)
        fileset (perun/set-meta html-fileset (merge prev-meta meta-map))]
    fileset))

(defn parse-asciidoc [fileset asciidoc-files options]
  "Parses a set of asciidoc files and returns a new fileset."
  (let [fileset-result (reduce
                        #(process-file %1 (io/file %2) options)
                        fileset  asciidoc-files)]
    (u/info "Parsed %s asciidoc files\n" (count asciidoc-files))
    fileset-result))
