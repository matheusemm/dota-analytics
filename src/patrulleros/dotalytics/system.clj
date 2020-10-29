(ns patrulleros.dotalytics.system
  (:require [datomic.client.api :as datomic]
            [integrant.core :as integrant]
            [patrulleros.dotalytics.web.handler :as handler]
            [ring.adapter.jetty :as jetty]))

(def config
  {:dota/web-handler {:match-id-generator (integrant/ref :dota/match-id-generator)
                      :db-connection (integrant/ref :dota/db-connection)}
   :dota/web-server {:port 3000
                     :join? false
                     :handler (integrant/ref :dota/web-handler)}
   :dota/match-id-generator {:start 0}
   :dota/db-connection {:db-name "dotalytics"
                        :server-type :peer-server
                        :access-key "myaccesskey"
                        :secret "mysecret"
                        :endpoint "localhost:8998"
                        :validate-hostnames false}})

;; web-handler

(defmethod integrant/init-key :dota/web-handler [_ deps]
  (handler/handler deps))

;; web-server

(defmethod integrant/init-key :dota/web-server [_ {:keys [handler] :as options}]
  (jetty/run-jetty handler (dissoc options :handler)))

(defmethod integrant/halt-key! :dota/web-server [_ server]
  (.stop server))

;; match-id-generator

(defmethod integrant/init-key :dota/match-id-generator [_ {:keys [start]}]
  (let [id (atom (max 0 (dec start)))]
    #(swap! id inc)))

;; db-connection

(defmethod integrant/init-key :dota/db-connection [_ {:keys [db-name] :as options}]
  (-> options
      (dissoc :db-name)
      (datomic/client)
      (datomic/connect {:db-name db-name})))
