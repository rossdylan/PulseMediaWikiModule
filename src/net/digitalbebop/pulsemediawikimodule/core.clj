(ns net.digitalbebop.pulsemediawikimodule.core
  (:gen-class)
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-time.coerce :as cljtc]
            [org.httpkit.client :as http]
            [interval-metrics.measure :as im-measure]
            [interval-metrics.core  :as im-core])
  (:import net.digitalbebop.ClientRequests$IndexRequest))


(def module-name "wiki")
(def index-server "http://localhost:8080/api/index")
(def ingest-rate (im-core/rate))


(defn cpmap
  "Partition pmap data into chunks to more efficiently parallelize it"
  [f step & colls]
  (let [pcolls (map (partial partition-all step) colls)]
    (apply concat
      (apply
        map
        (partial pmap f)
        pcolls))))


(defn extract-text
  "Extract textual data from an xml zipper"
  [loc & pred]
  (map zip-xml/text (apply zip-xml/xml-> loc pred)))


(defn clean-title
  "Clean the title of bad characters"
  [title]
  (clojure.string/replace title #" " "_"))


(defn create-wikimap
  "Take a wiki title and its body and turn it into a map"
  [title text ts]
  (let [ct (clean-title title)]
    {:title ct
     :text text
     :ts (cljtc/to-long ts)
     :url (str "https://wiki.csh.rit.edu/wiki/" ct)
     :id (str "wiki_" ct)
    }))


(defn wiki-parse
  "cpmap across 3 zippers and combine all the wiki information we care about"
  [wxml]
  (cpmap
    (fn [title text ts]
      (create-wikimap title text ts))
    100
    (extract-text wxml :page :title)
    (extract-text wxml :page :revision :text)
    (extract-text wxml :page :revision :timestamp)))


(defn create-index-req
  "Serialize our wiki page data into a IndexRequest protobuf. This is done
  using the java protobuf library since the clojure bindings appear to be
  broken at this time. We return a ByteBuffer which is understood by http-kit"
  [wmap]
  (let [builder (ClientRequests$IndexRequest/newBuilder)]
    (doto builder
      (.setIndexData (str (:title wmap) " " (clojure.string/replace (:text wmap) #"(\n|\r)" "")))
      (.setMetaTags (json/write-str {:format "text" :title (:title wmap)}))
      (.addTags "wiki")
      (.addTags (:title wmap))
      (.setLocation (:url wmap))
      (.setTimestamp (:ts wmap))
      (.setModuleId (:id wmap))
      (.setModuleName module-name))
    (-> builder
        .build
        .toByteString
        .asReadOnlyByteBuffer)))


(defn send-to-pulse
  "Using http-kit we post the ByteBuffer containing the wiki data to Pulse. We
  also increment the ingest-rate so we can get some statistics."
  [pbuf-data]
  (do
    (im-core/update! ingest-rate 1)
    (http/post
      index-server
      {:body pbuf-data})))


(defn ingest
  "Combine the protobuf serialization and http post steps into a cpmap call."
  [wmaps]
  (dorun
    (cpmap
      (fn [wmap]
        (->> wmap
             create-index-req
             send-to-pulse))
      10
      wmaps)))


(defn parse-ingest
  "Full parsing and ingestion pipeline built using a threading macro"
  [file]
  (->> file
      io/file
      xml/parse
      zip/xml-zip
      wiki-parse
      ingest))


(defn -main
  "Main function, takes 1 argument, the path to the wiki dump"
  [& args]
  (let [poller (im-measure/periodically
                 5
                 (println (format "%.0f Pages Per Second" (/ (im-core/snapshot! ingest-rate) 2))))]
    (do
      (parse-ingest (first args))
      (println "Ingestion Complete")
      (poller))))
