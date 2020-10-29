(ns patrulleros.dotalytics.web.controller
  (:require [clojure.string :as str]
            [patrulleros.dotalytics.repository :as repository]
            [patrulleros.dotalytics.service :as service]
            [ring.util.response :as response]))

(defn parameter [request name & keys]
  (let [[value & vs] (map (fn [[_ params]] (params name))
                          (:parameters request))]
    (if (seq vs)
      (throw (ex-info "Multiple request parameters have the same name."
                      {:request request
                       :parameters (:parameters request)
                       :name name}))
      (if (seq keys)
        (get-in value keys)
        value))))

(defn generate-match-id [request]
  ((:match-id-generator request)))

(defn read-combat-log-file-content [request]
  (slurp (parameter request :combat-log-file :tempfile)))

(defn ingest-combat-log [request]
  (let [events (service/extract-events
                (generate-match-id request)
                (read-combat-log-file-content request))]
    (if (seq events)
      (do
        (repository/insert-events (:db-connection request) events)
        (response/response {:match-id (-> events first :event/match-id)}))
      (response/response
       {:info "No events imported. Did you submit a proper combat-log file?"
        :file (parameter request :combat-log-file :filename)}))))

(defn get-kill-counts-by-hero [request]
  (response/response
   (repository/fetch-kill-counts-by-heroes
    (:db-connection request)
    (parameter request :match-id))))

(defn get-items-purchased [request]
  (response/response
   (repository/fetch-items-purchased
    (:db-connection request)
    (parameter request :match-id)
    (parameter request :hero))))

(defn get-spells-cast-counts [request]
  (response/response
   (repository/fetch-spells-cast-counts
    (:db-connection request)
    (parameter request :match-id)
    (parameter request :hero))))

(defn get-damage-done [request]
  (response/response
   (repository/fetch-damage-done
    (:db-connection request)
    (parameter request :match-id)
    (parameter request :hero))))
