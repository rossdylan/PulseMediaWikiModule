(defproject pulsemediawikimodule "0.1.0-SNAPSHOT"
  :description "MediaWiki ingestion module for Pulse"
  :url "https://github.com/rossdylan/PulseMediaWikiModule"
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.json "0.2.6"]
                 [interval-metrics "1.0.0"]
                 [clj-time "0.11.0"]
                 [http-kit "2.1.18"]
                 [com.google.protobuf/protobuf-java "2.6.1"]]
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-protobuf "0.4.3"]]
  :main net.digitalbebop.pulsemediawikimodule.core)
