(ns my-app.seed
  (:require [xtdb.api :as xt]
            [my-app.config :as config]
            [clojure.tools.logging :as log]))

(defn seed-data! []
  (let [node @config/xtdb-node
        items [{:xt/id (java.util.UUID/randomUUID)
                :name "Sample Item 1"
                :description "This is a sample item for testing"}
               {:xt/id (java.util.UUID/randomUUID)
                :name "Sample Item 2"
                :description "Another sample item"}
               {:xt/id (java.util.UUID/randomUUID)
                :name "Sample Item 3"
                :description "Yet another sample item"}]]
    (log/info "Seeding database with sample items...")
    (doseq [item items]
      (log/debug "Adding item:" item)
      (xt/submit-tx node
                    [[:put-docs :items item]]))
    (log/info "Seed data added successfully")))

(comment
  (in-ns 'my-app.seed)
  ;; Seed the database with sample data
  (seed-data!)
  ;;
  )
