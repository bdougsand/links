(ns links.processing
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]))

(defn to-node [html]
  (-> html
      (.getBytes java.nio.charset.StandardCharsets/UTF_8)
      (java.io.ByteArrayInputStream.)
      (html/html-resource)))

(defn to-text [nodes]
  (apply str (map html/text nodes)))

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
   "twitter:creator" :twitter-handle})

;; TODO: Check for oEmbed 

(defn process-body [body-string]
  (let [body (to-node body-string)]
    (merge
     {:title (-> body (html/select [:title]) to-text)}
     (into {} (keep (fn [{{p :property, n :name, c :content} :attrs}]
                      (when-let [k (meta-tags (or p n))]
                        [k c])))
           (html/select body [:meta])))))

(declare process-url)
(defn process-response [url response]
  (let [headers (:headers response)]
    (if (= (:status response) 302)
      (assoc (process-url (:location headers)) :url url)

      (let [content-type (:content-type headers)
            image? (boolean (re-find #"^image/" content-type))]
        (cond-> {:url url
                 :image? image?
                 :image (when image? url)
                 :content-type content-type
                 :modified (:last-modified headers)
                 :size (when-let [size (:content-length headers)]
                         (Long/parseLong size))}

          (not image?) (merge (process-body (:body response))))))))

(defn do-print [x] (println x) x)

(defn process-url [url]
   (println ";; Processing" url)
  (try
    (process-response url @(http/get url {:method :get
                                          :follow-redirects true
                                          :max-redirects 10}))
    (catch Exception err
      (println ";; Error during processing" (prn-str err)))))

;; TODO: Set this up to start when the system starts
(defonce in-chan (async/chan (async/dropping-buffer 50)))
(defonce out-chan (async/chan (async/dropping-buffer 50)))


(async/pipeline 10 out-chan (map process-url) in-chan)

(defn process [url]
  (async/put! in-chan url))
