(ns links.views
  (:require [hiccup.core :refer [html]]
            [clojure.string :as str])
  (:import [java.time.Duration]))

(def date-format
  (java.text.SimpleDateFormat. "hh:mm aa 'on' MMM dd, yyyy"))

(defn pluralize [n w]
  (if (= n 1)
    (str "a " w)
    (str n " " w "s")))

(defn format-since [diff]
  (let [diff (/ diff 1000)
        m (-> diff (mod 3600) (/ 60) (int))
        h (int (/ diff 3600))]
    (cond
      (< diff 60) "less than a minute ago"

      (> h 0) (str h " hour" (when-not (= h 1) "s")
                   (when-not (zero? m)
                     (str ", " (pluralize m "minute")))
                   " ago")

      :else (str (pluralize m "minute") " ago")

      (zero? h) (str m " minutes ago"))))

(defn format-recent [diff]
  (let [d (int (/ diff (* 3600 1000 24)))]
    (str (pluralize d "day") " ago")))

(defn format-date [dt]
  (let [diff (- (inst-ms (java.util.Date.)) (inst-ms dt))]
    (cond
      (< diff 86400000) (format-since diff)

      (< diff (* 3600000 72)) (format-recent diff)

      :else (.format date-format dt))))

(defn layout [{:keys [title]} & body]
  (html [:html {:lang "en"}
         [:head
          (when title [:title title])
          [:meta {:charset "utf-8"}]
          [:meta {:http-equiv "X-UA-Compatible"
                  :content "IE=edge"}]
          [:link {:href "http://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css"
                  :rel "stylesheet"
                  :type "text/css"}]]
         [:body
          [:div.container.container-fluid
           (when title [:div.page-header [:h1 title]])
           body]]]))

(def readable-key
  {})

(defmulti render-datum-type key)
(defmethod render-datum-type :default [[k v]]
  [:div [:strong (readable-key k (name k))] " — " v])

(defn render-datum [pair]
  [:li (render-datum-type pair)])

(defn title-from-url [url]
  (try
    (-> url
        (java.net.URL.)
        (.getHost))
    (catch Exception _
      "Untitled")))

(defn render-link [{:keys [title url added data description image
                           site-name video twitter-handle type]}]
  [:div.row [:div.col-md-3.col-xs-6
             [:span.thumbnail
              [:img.img-thumbnail
               {:src (or image "https://placeholdit.imgix.net/~text?w=200&h=225")}]]]
   [:div.col-md-9.col-xs-6
    [:a {:href url} [:h3.title (or title (title-from-url url))
                     (when type
                       (cond
                         (re-find #"^video" type) [:span.glyphicon.glyphicon-facetime-video]
                         (re-find #"image" type) [:span.glyphicon.glyphicon-picture]))]]
    [:div
     (when site-name
       [:span [:span.text-muted.glyphicon.glyphicon-be] site-name])
     (when (and site-name twitter-handle) " — ")
     (when twitter-handle
       [:a {:href (str "https://twitter.com/" (subs twitter-handle 1))}
        twitter-handle])]
    [:em.small.text-muted url]
    (when added [:div (str "Posted " (format-date added))])
    (when description [:p description])
    [:ul (map render-datum data)]]])

(defn list-view [list-info]
  (layout {:title (str "Links: " (:key list-info))}
          [:div.list-group
           (map render-link (:links list-info))]))

(defn not-found-view [list-key]
  (layout {:title "List not found"} [:p "Could not find list:" list-key]))

