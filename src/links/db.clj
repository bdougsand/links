(ns links.db
  (:require [monger.collection :as mc]
            [monger.core :as mg]
            [monger.operators :refer [$in $pull $push]]
            [monger.query :as mq]
            [monger.result :as mr]

            [links.processing :as proc]

            [clojure.core.async :as async])
  (:import [org.bson.types ObjectId]
           [java.net URL]))

(def gen (java.util.Random.))

(def chars "ol23456789abcdefghijkmnpqrstvwxyz")

(defn rand-id
  "Generates a new random id."
  []
  (loop [n (.nextLong gen), s "", steps 12]
    (if (not= steps 0)
      (recur (bit-shift-right n 5) (str s (nth chars (bit-and 31 n))) (dec steps))

      s)))

(defn get-list [db list-key]
  (let [link-list (mc/find-one-as-map db "links" {:key list-key})
        links (:links link-list)
        urls (mc/find-maps db "url-info" {:url {$in (map :url links)}})
        url-map (into {} (map (juxt :url identity)) urls)]
    (assoc link-list
           :links (map (fn [link-info]
                         (merge link-info (url-map (:url link-info))))
                       links))))

(defn add-to-list [db list-key link-info]
  (proc/process (:url link-info))
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
    (mc/insert db "url-info" (async/<! proc/out-chan))
    (recur)))

(defn stop-loop [chan]
  (when chan (async/close! chan)))
