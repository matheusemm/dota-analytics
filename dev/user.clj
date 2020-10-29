(ns user
  (:require [datomic.client.api :as d]
            [hashp.core]
            [integrant.repl :refer [clear go halt init prep reset reset-all]]
            [integrant.repl.state :as state]
            [patrulleros.dotalytics.system :as system]
            #_[portal.api :as portal]))

(integrant.repl/set-prep! (constantly system/config))

;; (portal/open {:portal.colors/theme :portal.colors/nord})
;; (portal/tap)

(defn conn [] (:dota/db-connection state/system))

(defn db [] (d/db (conn)))

(defn delete-events []
  (let [event-ids (d/q '[:find ?e :where [?e :event/match-id]] (db))]
    (d/transact (conn) {:tx-data (mapv #(vector :db/retractEntity (first %))
                                       event-ids)})
    :done))
