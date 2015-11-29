(ns cpdoc.core
  (:gen-class)
  (:use clojure.java.io)
  (:require [markdown.core :as mk]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [pathetic.core :as path]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [cheshire.core :as json]))


(def template-resources ["_template/index.html"
                         "_template/css/bootstrap.css"
                         "_template/css/main.css"
                         "_template/css/style.css"
                         "_template/js/lunr.js"
                         "_template/js/main.js"
                         "_template/js/jquery-1.11.3.js"
                         ])

(def md-formats #{".md", ".markdown"})

(defn markdon? [file]
  (contains? md-formats (fs/extension file)))

(defn build-menu-links [root path-relative-to-root]
  (let [files-in-root (last (first (fs/iterate-dir root)))]
    (str/join ""
              (->> files-in-root
                   (filter #(markdon? (file root %)))
                   (map #(str "<a class=\"list-group-item\" href=\""
                              path-relative-to-root
                              "/"
                              (fs/name %)
                              ".html\">"
                              (.toUpperCase (fs/name %))
                              "</a>"))))
    ))

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
        (str/replace replacement value (str name ".html"))

        ; Return without modification
        replacement))
    nil))

(defn links-processor [text state]
  [(let [matcher (re-matcher #"<a([^>]*?)href='([^']*?)'([^>]*?)>" text)]
     (loop [result text]
       (let [match (re-find matcher)]
         (if-not match
           result
           (recur (str/replace result (get match 0) (url-replacement match)))))))
   state])

(defn get-header [content]
  "Retrives the header value from the markdown content"
  (let [first-line (get (str/split content #"\n") 0)]
    (if (.startsWith first-line "# ")
      (str/trim (subs first-line 1))
      nil)))

(defn generate-html-page [{:keys [menu content title root project-name]}]
  (-> (slurp (io/resource "_template/index.html"))

      (str/replace "{{template_path}}" (str root "/_template"))
      (str/replace "{{menu}}" menu)
      ; Content
      (str/replace "{{content}}" content)
      (str/replace "{{title}}" title)
      (str/replace "{{project_name}}" (if project-name project-name "Documentation"))
      (str/replace "{{root}}" root)))

(defn process-html [{:keys [content root path-relative-to-root file-name project-name]}]
  (let [processed-html (mk/md-to-html-string content :custom-transformers [links-processor])
        content-header (get-header content)
        header (if content-header
                 content-header
                 (file-name))]

    (generate-html-page {:menu         (build-menu-links root path-relative-to-root)
                         :content      processed-html
                         :title        header
                         :root         path-relative-to-root
                         :project-name project-name})))

(defn file-handler [input root-path output project-name]
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
                    :file-name             file-name
                    :project-name          project-name}]

          (spit output-file-name (process-html data)))

        ;other file
        (fs/copy input
                 (file output relative-dir-path (.getName input)))
        ))))

(defn create-search-index [files input]
  (->> files
       (filter #(markdon? (file %)))
       (mapv (fn [f] (let [content (slurp f)
                           header (get-header content)
                           relative-path (path/relativize input (fs/absolute (fs/parent f)))]
                       {:file    (str relative-path "/" (fs/name f) ".html")
                        :header  header
                        :content (str/replace content #"[^\w\s\.,]" "")})))))

(defn generate-map-page [input output name]
  (generate-html-page {:menu         (build-menu-links input ".")
                       :content      (str/join "" (fs/walk
                                                    (fn [cur-dir _ files]
                                                      (str "<h1>" (path/relativize input cur-dir) "</h1><ul>"
                                                           (str/join ""
                                                                     (->> files
                                                                          (filter markdon?)
                                                                          (map #(str "<li><a href=\""
                                                                                     (path/relativize input cur-dir)
                                                                                     "/"
                                                                                     (fs/name %)
                                                                                     ".html\">"
                                                                                     (fs/base-name %)
                                                                                     "</a></li>"))))
                                                           "</ul>"))
                                                    input))
                       :title        "Map"
                       :root         "."
                       :project-name name}
                      ))

(def cli-options
  ;; An option with a required argument
  [["-o" "--output Path" "Output path"
    :id :output
    :default "./build"
    :validate [#(fs/writeable? %) "Output directory must be writable"]]

   ["-i" "--input Path" "Directory with doc sources"
    :id :input
    :validate [#(fs/readable? %) "Input directory must be readable"]]

   ["-n" "--name Project Name" ""
    :id :name]

   ["-h" "--help"]])

(defn -main [& args]
  (let [params (cli/parse-opts args cli-options)
        options (:options params)
        output (file (:output options))
        input (:input options)
        name (:name options)]

    (if (:help options)
      (println (:summary params))
      (do
        ; Copy template
        (doseq [res template-resources]
          (let [output-res (file output res)]
            (fs/mkdirs (.getParent output-res))
            (spit output-res (slurp (io/resource res)))))

        ;Generate map
        (spit (file output "__map.html") (generate-map-page input output name))

        (let [files (flatten (fs/walk
                               (fn [cur-dir _ files]
                                 (map #(file cur-dir %) files))
                               input))]
          ; Process all files
          (doseq [file files]
            (file-handler file input output name))

          ; Search index
          (spit (file output "search.js")
                (str "loadSearchIndex("
                     (json/generate-string (create-search-index files input))
                     ");"))
          ; end
          )))))