(ns my-app.seed
  (:require [xtdb.api :as xt]
            [my-app.config :as config]))

(defn seed-data! []
  (let [node (config/xtdb-node)
        items [{:xt/id (java.util.UUID/randomUUID) :name "Item 1" :description "First item"}
               {:xt/id (java.util.UUID/randomUUID) :name "Item 2" :description "Second item"}
               {:xt/id (java.util.UUID/randomUUID) :name "Item 3" :description "Third item"}]]
    (println "Seeding data...")
    (xt/submit-tx node (for [item items] [:put item]))
    (println "Data seeded.")))