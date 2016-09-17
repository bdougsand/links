(ns links.views
  (:require [hiccup.core :refer [html]]
            [clojure.string :as str]))

(def date-format
  (java.text.SimpleDateFormat. "hh:mm aa 'on' MMM dd, yyyy"))

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
  [:div [:strong (str (readable-key k (name k)) " — ")] v])

(defn render-datum [pair]
  [:li (render-datum-type pair)])

(defn render-link [{:keys [title url added data description image
                           site-name video twitter-handle]}]
  [:div.row [:div.col-md-3
             (when image [:span.thumbnail [:img {:src image}]])]
   [:div.col-md-9
    [:a {:href url} [:h3.title (or title url)]]
    [:div
     (when site-name
       [:span.text-muted.glyphicon.glyphicon-globe site-name])
     (when (and site-name twitter-handle) " — ")
     (when twitter-handle
       [:a {:href (str "https://twitter.com/" (subs twitter-handle 1))}
        twitter-handle])]
    [:em.small.text-muted url]
    (when added [:div (str "Posted " (.format date-format added))])
    (when description [:p description])
    [:ul (map render-datum data)]]])

(defn list-view [list-info]
  (layout {:title (str "Links: " (:key list-info))}
          [:div.list-group
           (map render-link (:links list-info))]))

(defn not-found-view [list-key]
  (layout [:p "Could not find list:" list-key]))

