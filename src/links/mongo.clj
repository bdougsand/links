(ns links.mongo
  (:require [com.stuartsierra.component :as component]
            [monger.core :as mg]
            [monger.collection :as mc]
            [environ.core :refer [env]]
            [links.db :as db]))


(defn setup [db]
  ;; Ensure that keys are unique:
  (mc/ensure-index db "links" (array-map :key 1) {:unique true})
  (mc/ensure-index db "links" (array-map :host 1))
  (mc/ensure-index db "url-info" (array-map :url 1) {:unique true}))

(defrecord MongoDB [host]
  component/Lifecycle
  (start [this]
    (let [conn (mg/connect {:host host})
          db (mg/get-db conn (env :mongo-db "links-dev"))]
      (setup db)
      (assoc this
             :conn conn
             :db db
             :save-loop (db/start-save-loop db))))

  (stop [this]
    (db/stop-loop (:save-loop this))
    (when-let [conn (:conn this)]
      (mg/disconnect conn)
      (dissoc this :conn :db))))

(defn make-mongo []
  (->MongoDB (env :mongo-host "localhost")))
