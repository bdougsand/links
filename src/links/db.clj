(ns links.db
  (:require [environ.core :refer [env]]
            [monger.collection :as mc]
            [monger.core :as mg]
            [monger.operators :refer [$in $pull $push]]
            [monger.query :as mq]
            [monger.result :as mr]

            [links.processing :as pr]

            [clojure.core.async :as async])
  (:import [org.bson.types ObjectId]
           [java.net URL]))


(defn get-db []
  (-> (mg/connect) (mg/get-db (env :mongo-db "links-dev"))))

(def gen (java.util.Random.))

(def chars "ol23456789abcdefghijkmnpqrstvwxyz")

(defn rand-id
  "Generates a new random id."
  []
  (loop [n (.nextLong gen), s "", steps 12]
    (if (not= steps 0)
      (recur (bit-shift-right n 5) (str s (nth chars (bit-and 31 n))) (dec steps))

      s)))

(defn make-filter
  "Transforms a map of values into a transducer that filters out maps whose
  :data entries do not match."
  [opts]
  (reduce comp (map (fn [[k v]]
                      (filter #(= (get-in % [:data k]) v)))
                    opts)))

(defn get-list-raw [db list-key & [opts]]
  (when-let [link-list (mc/find-one-as-map db "links" {:key list-key})]
    (cond-> link-list
      opts (update :links (partial into [] (make-filter opts))))))

(defn get-list [db list-key]
  (when-let [link-list (mc/find-one-as-map db "links" {:key list-key})]
    (let [links (:links link-list)
          urls (mc/find-maps db "url-info" {:url {$in (map :url links)}})
          url-map (into {} (map (juxt :url identity)) urls)]
      (assoc link-list
             :links (->> links
                         (reverse)
                         (map (fn [link-info]
                                   (merge link-info (url-map (:url link-info))))))))))

(defn add-to-list [db list-key link-info]
  (pr/process (:url link-info))
  (let [result (mc/update db "links"
                          {:key list-key}
                          {$push {:links link-info}})]
    (if-not (mr/updated-existing? result)
      (mc/insert db "links" {:links [link-info]
                             :key list-key
                             :created (java.util.Date.)
                             :modified (java.util.Date.)})
      result)))

(defn delete-from-list [db list-key url]
  (mc/update db "links" {:key list-key} {$pull {:links {:url url}}}))

(defn start-save-loop [db]
  (async/go-loop []
    (let [info (async/<! pr/out-chan)]
      (mc/upsert db "url-info" {:url (:url info)} info))
    (recur)))

(defn stop-loop [chan]
  (when chan (async/close! chan)))
