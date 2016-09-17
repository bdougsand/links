(defproject links "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [http-kit "2.2.0-SNAPSHOT" :excludes [org.clojure/clojure]]
                 [com.stuartsierra/component "0.3.1"]
                 [environ "1.0.3"]
                 [selmer "1.0.7"]

                 [org.jsoup/jsoup "1.8.3"]
                 [org.clojure/data.json "0.2.6"]
                 [hiccup "1.0.5"]
                 [liberator "0.14.1"]
                 [com.novemberain/monger "3.0.2"]
                 [org.clojure/core.async "0.2.391"]
                 [enlive "1.1.6"]]
  :plugins [[lein-ring "0.9.7"]]
  :main links.system
  :aot :all
  :ring {:handler links.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})

