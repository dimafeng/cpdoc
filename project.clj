(defproject cpdoc "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot [cpdoc.core]
  :main cpdoc.core
  :uberjar-name "cpdoc.jar"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [markdown-clj "0.9.80"]
                 [me.raynes/fs "1.4.6"]
                 [pathetic "0.5.1"]
                 [org.clojure/tools.cli "0.3.3"]])
