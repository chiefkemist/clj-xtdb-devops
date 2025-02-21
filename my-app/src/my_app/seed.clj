(ns my-app.seed
  (:require [xtdb.api :as xt]
            [my-app.config :as config]
            [clojure.tools.logging :as log]))

(def seed-items
  [{:xt/id #uuid "550e8400-e29b-41d4-a716-446655440000"
    :name "Implement Authentication"
    :slug "implement-authentication-a1b2c3d4"
    :description "Add user authentication with OAuth2 support"
    :status "active"
    :priority "high"
    :tags ["security" "feature"]
    :created-at (java.time.Instant/parse "2024-02-01T10:00:00Z")
    :due-date "2024-03-15"
    :assigned-to "Alice"}

   {:xt/id #uuid "550e8400-e29b-41d4-a716-446655440001"
    :name "Fix Memory Leak"
    :slug "fix-memory-leak-a1b2c3d4"
    :description "Investigate and fix memory leak in background process"
    :status "pending"
    :priority "high"
    :tags ["bug" "performance"]
    :created-at (java.time.Instant/parse "2024-02-05T14:30:00Z")
    :due-date "2024-02-28"
    :assigned-to "Bob"}

   {:xt/id #uuid "550e8400-e29b-41d4-a716-446655440002"
    :name "Update Documentation"
    :slug "update-documentation-a1b2c3d4"
    :description "Update API documentation with new endpoints"
    :status "completed"
    :priority "low"
    :tags ["docs" "maintenance"]
    :created-at (java.time.Instant/parse "2024-01-15T09:00:00Z")
    :due-date "2024-01-30"
    :assigned-to "Charlie"}

   {:xt/id #uuid "550e8400-e29b-41d4-a716-446655440003"
    :name "Add Dark Mode"
    :slug "add-dark-mode-a1b2c3d4"
    :description "Implement dark mode theme support"
    :status "active"
    :priority "medium"
    :tags ["ui" "feature"]
    :created-at (java.time.Instant/parse "2024-02-10T11:20:00Z")
    :due-date "2024-03-01"
    :assigned-to "Diana"}

   {:xt/id #uuid "550e8400-e29b-41d4-a716-446655440004"
    :name "Optimize Database Queries"
    :slug "optimize-database-queries-a1b2c3d4"
    :description "Improve performance of slow database queries"
    :status "pending"
    :priority "high"
    :tags ["performance" "database"]
    :created-at (java.time.Instant/parse "2024-02-07T16:45:00Z")
    :due-date "2024-02-25"
    :assigned-to "Eve"}

   {:xt/id #uuid "550e8400-e29b-41d4-a716-446655440005"
    :name "Archive Old Records"
    :slug "archive-old-records-a1b2c3d4"
    :description "Implement data archival process for old records"
    :status "archived"
    :priority "low"
    :tags ["maintenance" "database"]
    :created-at (java.time.Instant/parse "2023-12-01T08:15:00Z")
    :due-date "2023-12-31"
    :assigned-to "Frank"}])

(defn seed-data! []
  (let [node @config/xtdb-node]
    (log/info "Seeding initial data...")
    (doseq [item seed-items]
      (try
        (xt/submit-tx node [[:put-docs {:into :items} item]])
        (log/info "Seeded item:" (:name item))
        (catch Exception e
          (log/warn "Failed to seed item:" (:name item) "-" (.getMessage e)))))
    (log/info "Seeding complete!")))

(comment
  (in-ns 'my-app.seed)
  ;; Seed the database with sample data
  (seed-data!)
  ;;
  )
