(ns patrulleros.dotalytics.web.handler
  (:require [muuntaja.core :as muuntaja]
            [patrulleros.dotalytics.web.controller :as controller]
            [reitit.coercion.malli]
            [reitit.dev.pretty]
            [reitit.ring]
            [reitit.ring.coercion]
            [reitit.ring.malli]
            [reitit.ring.middleware.dev]
            [reitit.ring.middleware.exception]
            [reitit.ring.middleware.multipart]
            [reitit.ring.middleware.muuntaja]
            [reitit.ring.middleware.parameters]
            [reitit.swagger]
            [reitit.swagger-ui]
            [ring.middleware.keyword-params]))

(def match-id-and-hero-path-params
  [:map
   [:match-id pos-int?]
   [:hero string?]])

(def routes
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "DOTAlytics"
                            :description "DOTA Coding Challenge"}}
           :handler (reitit.swagger/create-swagger-handler)}}]

   ["/api/match"
    {:swagger {:tags ["match"]}}

    [""
     {:post {:summary "Ingests events from a combat log text file"
             :parameters {:multipart [:map [:combat-log-file reitit.ring.malli/temp-file-part]]}
             :responses {200 {:body [:or
                                     [:map [:match-id pos-int?]]
                                     [:map
                                      [:info string?]
                                      [:file string?]]]}}
             :handler controller/ingest-combat-log}}]

    ["/:match-id"
      [""
       {:get {:summary "Heroes and their number of kills in a match"
              :parameters {:path [:map [:match-id pos-int?]]}
              :responses {200 {:body [:vector
                                      [:map
                                       [:hero string?]
                                       [:kills nat-int?]]]}}
              :handler controller/get-kill-counts-by-hero}}]

      ["/:hero"
       ["/items"
        {:get {:summary "Items purchased by a hero in a match"
               :parameters {:path match-id-and-hero-path-params}
               :responses {200 {:body [:vector
                                       [:map
                                        [:item string?]
                                        [:timestamp nat-int?]]]}}
               :handler controller/get-items-purchased}}]

       ["/spells"
        {:get {:summary "Spells and number of times they were cast by a hero in a match"
               :parameters {:path match-id-and-hero-path-params}
               :responses {200 {:body [:vector
                                       [:map
                                        [:spell string?]
                                        [:casts pos-int?]]]}}
               :handler controller/get-spells-cast-counts}}]

       ["/damage"
        {:get {:summary "Number of times and total damage done by a hero to other heroes in a match"
               :parameters {:path match-id-and-hero-path-params}
               :responses {200 {:body [:vector
                                       [:map
                                        [:target string?]
                                        [:damage-instances pos-int?]
                                        [:total-damage pos-int?]]]}}
               :handler controller/get-damage-done}}]]]]])

(def deps-injection-middleware
  {:name ::deps-injection
   :compile (fn [data _]
              (fn [handler]
                (fn [request]
                  (handler (merge request (select-keys data [:match-id-generator :db-connection]))))))})

(defn default-exception-handler [^Exception e _]
  {:status 500
   :body {:type "exception"
          :class (-> e .getClass .getName)
          :message (.getMessage e)}})

(def exception-middleware
  (reitit.ring.middleware.exception/create-exception-middleware
   (merge
    reitit.ring.middleware.exception/default-handlers
    {:reitit.ring.middleware.exception/default default-exception-handler})))

(defn router [deps]
  (reitit.ring/router
   routes
   {;;:reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
    :exception reitit.dev.pretty/exception
    :data (merge {:coercion reitit.coercion.malli/coercion
                  :muuntaja (muuntaja/create
                             (muuntaja/select-formats
                              muuntaja/default-options ["application/json"]))
                  :middleware [reitit.swagger/swagger-feature
                               reitit.ring.middleware.parameters/parameters-middleware
                               reitit.ring.middleware.multipart/multipart-middleware
                               reitit.ring.middleware.muuntaja/format-negotiate-middleware
                               reitit.ring.middleware.muuntaja/format-response-middleware
                               exception-middleware
                               reitit.ring.middleware.muuntaja/format-request-middleware
                               reitit.ring.coercion/coerce-response-middleware
                               reitit.ring.coercion/coerce-request-middleware
                               deps-injection-middleware]}
                 (select-keys deps [:match-id-generator :db-connection]))}))

(defn handler [deps]
  (reitit.ring/ring-handler
   (router deps)
   (reitit.ring/routes
    (reitit.swagger-ui/create-swagger-ui-handler {:path "/"})
    (reitit.ring/create-default-handler))))
