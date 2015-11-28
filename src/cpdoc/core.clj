(ns cpdoc.core
  (:gen-class)
  (:use clojure.java.io)
  (:require [markdown.core :as mk]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [pathetic.core :as path]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]))


(def template-resources ["_template/index.html"
                         "_template/css/bootstrap.css"
                         "_template/css/main.css"
                         "_template/css/style.css"])

(def md-formats #{".md", ".markdown"})

(defn markdon? [file]
  (contains? md-formats (fs/extension file)))

(defn build-menu-links [root path-relative-to-root]
  (let [files-in-root (last (first (fs/iterate-dir root)))]
    (str "<ul class=\"nav\">"
         (clojure.string/join ""
                              (->> files-in-root
                                   (filter #(markdon? (file root %)))
                                   (map #(str "<li><a href=\""
                                              path-relative-to-root
                                              "/"
                                              (fs/name %)
                                              ".html\">"
                                              (.toUpperCase (fs/name %))
                                              "</a></li>"))))
         "</ul>"
         )))

(defn url-replacement [match]
  (if match

    (let [replacement (get match 0)
          value (get match 2)
          name (subs value 0 (- (count value) 3))
          ]
      (if (and (.endsWith value ".md")
               (not (and (.startsWith value "http://")
                         (.startsWith value "https://"))))
        ;Modify to url
        (clojure.string/replace replacement value (str name ".html"))

        ; Return without modification
        replacement))
    nil))

(defn links-processor [text state]
  [(let [matcher (re-matcher #"<a([^>]*?)href='([^']*?)'([^>]*?)>" text)]
     (loop [result text]
       (let [match (re-find matcher)]
         (if-not match
           result
           (recur (clojure.string/replace result (get match 0) (url-replacement match)))))))
   state])

(defn get-header [content]
  "Retrives the header value from the markdown content"
  (let [first-line (get (str/split content #"\n") 0)]
    (if (.startsWith first-line "# ")
      (str/trim (subs first-line 1))
      nil)))

(defn process-html [{:keys [content root path-relative-to-root file-name]}]
  (let [processed-html (mk/md-to-html-string content :custom-transformers [links-processor])
        content-header (get-header content)
        header (if content-header
                 content-header
                 (file-name))]

    (-> (slurp (io/resource "_template/index.html"))

        (clojure.string/replace "{{template_path}}" (str path-relative-to-root "/_template"))
        (clojure.string/replace "{{menu}}" (str "<h1>Menu</h1>" (build-menu-links root path-relative-to-root)))
        ; Content
        (clojure.string/replace "{{content}}" processed-html)
        (clojure.string/replace "{{title}}" header)
        )))

(defn file-handler [input root-path output]
  (println (str "File " input "is being processed"))
  (let [relative-file-path (path/relativize root-path input)
        relative-dir-path (subs relative-file-path 0
                                (-
                                  (count relative-file-path)
                                  (count (.getName input))))]
    (fs/mkdirs (file output relative-dir-path))

    (when-not (fs/directory? input)
      (if (markdon? input)

        ; markdown file
        (let [file-name (fs/name input)
              output-file-name (file output relative-dir-path (str file-name ".html"))
              data {:content               (slurp input)
                    :root                  root-path
                    :output-name           output-file-name
                    ; Reverse path (like .. or ../..)
                    :path-relative-to-root (path/relativize (file output relative-dir-path) output)
                    :file-name             file-name}]

          (spit output-file-name (process-html data)))

        ;other file
        (fs/copy input
                 (file output relative-dir-path (.getName input)))
        ))))

(def cli-options
  ;; An option with a required argument
  [["-o" "--output Path" "Output path"
    :id :output
    :default "./build"
    :validate [#(fs/writeable? %) "Output directory must be writable"]]

   ["-i" "--input Path" "Directory with doc sources"
    :id :input
    :validate [#(fs/readable? %) "Input directory must be readable"]]

   ["-h" "--help"]])

(defn -main [& args]
  (let [params (cli/parse-opts args cli-options)
        options (:options params)
        output (file (:output options))
        input (:input options)]

    (if (:help options)
      (println (:summary params))
      (do
        ; Copy template
        (doseq [res template-resources]
          (let [output-res (file output res)]
            (fs/mkdirs (.getParent output-res))
            (spit output-res (slurp (io/resource res)))))

        ; Process all files
        (doseq [file (flatten (fs/walk
                                (fn [cur-dir _ files]
                                  (map #(file cur-dir %) files))
                                input))]
          (file-handler file input output))
        ))))