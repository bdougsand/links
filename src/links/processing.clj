(ns links.processing
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [clojure.string :as str])
  (:import org.apache.tika.metadata.Metadata
           org.apache.tika.metadata.TikaCoreProperties
           org.apache.tika.parser.AutoDetectParser
           org.xml.sax.helpers.DefaultHandler))

(defn metadata-map [meta-data]
  (into {} (map (fn [n] [n (.get meta-data n)])) (.names meta-data)))

(defn meta-map [m key-map]
  (into {} (keep (fn [n]
                   (when-let [k (key-map (str/lower-case n))]
                     [k (.get m n)])))
        (.names m)))

(defn- get-metadata
  "Use Tika to extract metadata from a response body."
  [response]
  (let [content-type (-> response :headers :content-type)
        handler (DefaultHandler.)
        parser (AutoDetectParser.)
        meta-data (doto (Metadata.)
                    (.add TikaCoreProperties/CONTENT_TYPE_HINT content-type))]
    (.parse parser (:body response)
            handler
            meta-data)
    meta-data))



(defn to-node [html]
  (-> html
      (.getBytes java.nio.charset.StandardCharsets/UTF_8)
      (java.io.ByteArrayInputStream.)
      (html/html-resource)))

(defn to-text [nodes]
  (apply str (map html/text nodes)))

;; Processing HTML metadata
(def meta-tags
  {"author" :author
   "keywords" :keywords
   "og:author" :author
   "og:image" :image
   "og:video" :video
   "og:video:width" :video-width
   "og:video:height" :video-height
   "og:video:type" :video-type
   "og:type" :type
   "og:description" :description
   "og:site_name" :site-name
   "article:published_time" :article-published
   "article:author" :author
   "twitter:creator" :twitter-handle})

;; Processing other types of documents
(def shared-tags
  {"author" :author
   "title" :title
   "producer" :pdf-producer
   "creation-date" :created
   "xmptpg:npages" :page-count
   "meta:word-count" :word-count})

(def pdf-tags shared-tags)

(def xls-tags shared-tags)

(def ppt-tags (merge shared-tags
                     {"slide-count" :page-count}))

;; TODO: Check for oEmbed

(defn process-body [body-string]
  (let [body (to-node body-string)]
    (merge
     {:title (-> body (html/select [:title]) to-text)
      :image (-> body (html/select [:img]) (first) :attrs :src)}

     ;; Build up a map of attributes from the meta tags:
     (into {} (keep (fn [{{p :property, n :name, c :content} :attrs}]
                      (when-let [k (meta-tags (or p n))]
                        [k c])))
           (html/select body [:meta])))))

(def types
  {"text/html" ::html
   "image/jpeg" ::jpeg
   "image/jpg" ::jpeg
   "image/gif" ::gif
   "image/png" ::png
   "application/pdf" ::pdf
   "application/vnd.ms-excel" ::excel
   "application/vnd.ms-powerpoint" ::ppt})

(derive ::jpeg ::image)
(derive ::gif ::image)
(derive ::png ::image)

(defn content-type [response]
  (let [[ctype params] (-> response
                           :headers
                           :content-type
                           (str/split #";" 2)) ]
    (types ctype)))

(defmulti process-document content-type)
(defmethod process-document :default [_] nil)

(defmethod process-document ::html [response]
  (process-body (:body response)))

(defmethod process-document ::pdf [response]
  (meta-map (get-metadata response) pdf-tags))

(defmethod process-document ::excel [response]
  (meta-map (get-metadata response) xls-tags))

(defmethod process-document ::ppt [response]
  (meta-map (get-metadata response) ppt-tags))

(defmethod process-document ::image [response]
  {:image (-> response :opts :url)})

(declare process-url)
(defn process-response [url response]
  (let [headers (:headers response)]
    (if (= (:status response) 302)
      (assoc (process-url (:location headers)) :url url)

      (let [content-type (:content-type headers)]
        (merge {:url url
                :content-type content-type
                :modified (:last-modified headers)
                :size (when-let [size (:content-length headers)]
                        (Long/parseLong size))}

               (process-document response))))))

(defn do-print [x] (println x) x)

(defn process-url [url]
   (println ";; Processing" url)
  (try
    (process-response url @(http/get url {:follow-redirects true
                                          :max-redirects 10}))
    (catch Exception err
      (println ";; Error during processing" (prn-str err)))))

;; TODO: Set this up to start when the system starts
(defonce in-chan (async/chan (async/dropping-buffer 50)))
(defonce out-chan (async/chan (async/dropping-buffer 50)))


(async/pipeline 10 out-chan (map process-url) in-chan)

(defn process [url]
  (async/put! in-chan url))
