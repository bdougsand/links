(ns links.webserver
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as server]
            [environ.core :refer [env]]

            [links.handler :refer [wrapped-app]]))

(defrecord WebServer [port]
  component/Lifecycle
  (start [this]
    (println ";; Starting webserver")
    (assoc this
           :server (server/run-server (wrapped-app (:mongo this))
                                      {:port port})))

  (stop [this]
    (when-let [stop-fn (:server this)]
      (println ";; Stopping webserver")
      (stop-fn)
      (dissoc this :server))))

(defn make-webserver []
  (->WebServer (env :webserver-port 3000)))
