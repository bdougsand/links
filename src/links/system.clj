(ns links.system
  (:require [com.stuartsierra.component :as component]

            [links.mongo :refer [make-mongo]]
            [links.webserver :refer [make-webserver]])
  (:gen-class))


(defn links-system []
  (component/system-map
   :mongo (make-mongo)
   :webserver (component/using
               (make-webserver)
               {:mongo :mongo})))

(def system (links-system))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system component/stop))


(defn -main [] (start))
