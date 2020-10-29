(ns patrulleros.dotalytics.service
  (:require [clojure.string :as str])
  (:import [java.time Duration LocalTime]))

(def event-parsers
  [{:event-type :event.type/purchase-item
    :regex #"^\[(?<timestamp>.*)\] npc_dota_hero_(?<hero>.*) buys item item_(?<item>.*)$"
    :create (fn [{:keys [item]}]
              {:event.purchase-item/name item})}
   {:event-type :event.type/damage-hero
    :regex #"^\[(?<timestamp>.*)\] npc_dota_hero_(?<hero>.*) hits npc_dota_hero_(?<target>.*) with (?<weapon>.*) for (?<damage>\d+) damage.*$"
    :create (fn [{:keys [target weapon damage]}]
              {:event.damage-hero/target target
               :event.damage-hero/weapon weapon
               :event.damage-hero/damage (Long/parseLong damage)})}
   {:event-type :event.type/kill-hero
    :regex #"^\[(?<timestamp>.*)\] npc_dota_hero_(?<target>.*) is killed by npc_dota_hero_(?<hero>.*)$"
    :create (fn [{:keys [target]}]
              {:event.kill-hero/target target})}
   {:event-type :event.type/cast-spell
    :regex #"^\[(?<timestamp>.*)\] npc_dota_hero_(?<hero>.*) casts ability (?<spell>.*) \(lvl (?<level>\d+)\) on (?<target>.*)$"
    :create (fn [{:keys [spell level target]}]
              {:event.cast-spell/name spell
               :event.cast-spell/level (Long/parseLong level)
               :event.cast-spell/target (cond
                                          (str/starts-with? target "npc_dota_hero_")
                                          (subs target 14)

                                          (str/starts-with? target "npc_dota_")
                                          (subs target 9)

                                          :else target)})}])

(defn get-match-group-names [^java.util.regex.Pattern regex]
  (->> (.pattern regex)
       (re-seq #"\?<([a-zA-Z][a-zA-Z0-9]*)>")
       (map (comp keyword second))))

(defn parse-timestamp [s]
  (->> (LocalTime/parse s)
       (Duration/between LocalTime/MIDNIGHT)
       (.toMillis)))

(defn parse-event [combat-log-line]
  (->> event-parsers
       (map (fn [{:keys [event-type regex create]}]
              (let [values (->> combat-log-line
                                (re-find regex)
                                (drop 1)
                                (zipmap (get-match-group-names regex)))]
                (when (seq values)
                  (assoc (create values)
                         :event/type event-type
                         :event/hero (:hero values)
                         :event/timestamp (parse-timestamp (:timestamp values)))))))
       (remove nil?)
       (first)))

(defn extract-events [match-id combat-log]
  (let [pipeline (comp (map parse-event)
                       (remove nil?)
                       (map #(assoc % :event/match-id match-id)))]
    (into [] pipeline (str/split-lines combat-log))))
