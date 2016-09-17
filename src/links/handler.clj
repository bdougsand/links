(ns links.handler
  (:require [compojure.core :refer [defroutes DELETE GET POST]]
            [compojure.route :as route]
            [clojure.data.json :as json]

            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]

            [clojure.string :as str]
            [clojure.spec :as spec]

            [links.db :as db]
            [links.views :as views]))


(defn wrap-mongo [handler mongo]
  (fn [req]
    (handler (assoc req :db (:db mongo)
                    :conn (:conn mongo)))))

(defn wrap-list
  "Wraps a handler so that requests containing a :list value in :params will
  have a :list set on the request map."
  [handler]
  (fn [req]
    (handler (assoc req :list
                    (when-let [list-key (get-in req [:params :list])]
                      (db/get-list (:db req) list-key))))))

;; TODO: move this to the db!
(spec/def ::url string?)
(spec/def ::title (spec/nilable string?))
(spec/def ::date inst?)
(spec/def ::data (spec/nilable map?))
(spec/def ::link-data (spec/keys :req-un [::url ::added]
                                 :opt-un [::data ::title]))

(defn post-to-list [{:keys [db params]}]
  (if-let [list-key (:list params)]
    (let [link-data {:title (:title params)
                     :url (:url params)
                     :added (java.util.Date.)
                     :data (:data params)}]
      (if-let [conform-error (spec/explain-data ::link-data link-data)]
        (prn-str conform-error)

        (do
          (db/add-to-list db list-key link-data)
          "ok")))))

(defn delete-from-list [{:keys [db params]}]
  (let [list-key (:list params)
        url (:url params)]
    (if (and list-key url)
      (prn-str (db/delete-from-list db list-key url)))))

(defn with-list [view]
  (fn [req]
    (if-let [list-key (get-in req [:params :list])]
      (if-let [list-info (db/get-list (:db req) list-key)]
        (view list-info)

        (views/not-found-view list-key)))))

(defroutes app-routes
  (GET "/" []  "Nothing to see here!")
  (GET "/:list" _ (with-list views/list-view))
  (POST "/:list" _ post-to-list)
  (DELETE "/:list" _ delete-from-list))

(def app
  (wrap-defaults #'app-routes (assoc-in site-defaults
                                        [:security :anti-forgery] false)))

(def wrapped-app (partial wrap-mongo app))
