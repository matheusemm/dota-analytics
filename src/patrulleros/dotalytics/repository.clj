(ns patrulleros.dotalytics.repository
  (:require [datomic.client.api :as datomic]))

;; CRUD

(def query-rules
  '[[(basic3 ?match-id ?event-type ?hero ?e)
     [?e :event/match-id ?match-id]
     [?e :event/type ?event-type]
     [?e :event/hero ?hero]]])

(defn insert-events [conn events]
  (datomic/transact conn {:tx-data events}))

(defn fetch-kill-counts-by-heroes [conn match-id]
  (datomic/q '[:find ?hero (count ?e2)
               :keys :hero :kills
               :in $ ?match-id
               :where
               [?e1 :event/match-id ?match-id]
               [?e1 :event/hero ?hero]
               [?e2 :event/match-id ?match-id]
               [?e2 :event/type :event.type/kill-hero]
               [?e2 :event/hero ?hero]]
             (datomic/db conn) match-id))

(defn fetch-items-purchased [conn match-id hero]
  (datomic/q '[:find ?item ?timestamp
               :keys :item :timestamp
               :in $ % ?match-id ?hero
               :where
               (basic3 ?match-id :event.type/purchase-item ?hero ?e)
               [?e :event.purchase-item/name ?item]
               [?e :event/timestamp ?timestamp]]
             (datomic/db conn) query-rules match-id hero))

(defn fetch-spells-cast-counts [conn match-id hero]
  (datomic/q '[:find ?spell (count ?e)
               :keys :spell :casts
               :in $ % ?match-id ?hero
               :where
               (basic3 ?match-id :event.type/cast-spell ?hero ?e)
               [?e :event.cast-spell/name ?spell]]
             (datomic/db conn) query-rules match-id hero))

(defn fetch-damage-done [conn match-id hero]
  (datomic/q '[:find ?target (count ?e) (sum ?damage)
               :keys :target :damage-instances :total-damage
               :in $ % ?match-id ?hero
               :where
               (basic3 ?match-id :event.type/damage-hero ?hero ?e)
               [?e :event.damage-hero/target ?target]
               [?e :event.damage-hero/damage ?damage]]
             (datomic/db conn) query-rules match-id hero))

;; Database schema

(def event-types
  #{:event.type/purchase-item
    :event.type/damage-hero
    :event.type/kill-hero
    :event.type/cast-spell})

(defn event-type? [value]
  (boolean (event-types value)))

(def match-events-schema
  [{:db/ident :event/match-id
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Match that the event belongs to."}
   {:db/ident :event/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :event/timestamp
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Instant, since the beginning of the match, when an event happened (in milliseconds)."}
   {:db/ident :event/hero
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Hero that performed the action recorded by the event."}

   {:db/ident :event.purchase-item/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :event.damage-hero/target
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Hero that was damaged."}
   {:db/ident :event.damage-hero/weapon
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Weapon used to hit the target."}
   {:db/ident :event.damage-hero/damage
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Amount of damage done."}

   {:db/ident :event.kill-hero/target
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Hero that was killed."}

   {:db/ident :event.cast-spell/target
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Target of the spell."}
   {:db/ident :event.cast-spell/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :event.cast-spell/level
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}])
