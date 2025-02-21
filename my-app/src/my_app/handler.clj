(ns my-app.handler
  "Main web application handler.
   Provides routing, request handling, and CRUD operations for items.
   Uses XTDB for persistence and HTMX for dynamic updates."
  (:require [clojure.string :as clj-str]
            [xtdb.api :as xt]
            [honey.sql :as sql]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [mount.core :as mount :refer [defstate]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [my-app.config :as config]
            [my-app.seed :as seed]
            [my-app.layout :refer [layout]]
            [ring.middleware.cors :refer [wrap-cors]]
            [my-app.styles :as styles]
            [hiccup.core :as hiccup]
            [reitit.core :as reitit]
            [ring.util.codec :as codec]
            [tick.core :as t])
  (:gen-class))

;; Forward declarations
;; Server and state management
(declare server)

;; Route handlers
(declare home-handler)                    ; GET / - Welcome page
(declare list-items-handler)              ; GET /items - List and search items
(declare create-item-handler)             ; POST /items - Create new item
(declare create-item-form-handler)        ; GET /items/new - New item form
(declare get-item-handler)                ; GET /items/:id/get - View item details
(declare update-item-handler)             ; POST /items/:id/update - Full update
(declare patch-item-handler)              ; POST /items/:id/patch - Partial update
(declare delete-item-post-handler)        ; POST /items/:id/delete - Delete item

;; Query and rendering functions
(declare build-query)                     ; Constructs XTQL query from filters
(declare execute-query)                   ; Executes query with temporal support
(declare render-item)                     ; Renders item as Hiccup markup

;; Helper functions
(declare parse-uuid-str)                  ; Safely parses UUID strings
(declare parse-datetime)                  ; Safely parses a datetime string into an Instant, adding seconds if needed

;; Request processing pipeline
(declare log-request-interceptor)         ; Logs incoming requests
(declare error-handler-interceptor)       ; Handles errors consistently
(declare interceptor-chain)               ; Orders interceptor execution

;; Configuration
(def port 58950)

;; Add to declarations at top
(declare url-for)                         ; Generates URLs for routes
(declare render-item-details)             ; Renders full item details view
(declare render-item-update-form)         ; Renders full update form
(declare render-item-patch-form)          ; Renders partial update form
(declare format-date)                     ; Formats dates consistently
(declare render-status-badge)             ; Renders status badge
(declare render-priority-badge)           ; Renders priority badge
(declare render-tags)                     ; Renders tag list

;; Add near the top with other declarations
(def router-state (atom nil))

;; Add to declarations
(declare generate-slug)                   ; Generates URL-friendly unique slugs

;; Page handlers
(defn home-handler 
  "Renders the welcome page with feature highlights and getting started button."
  [_req]
  (layout "Welcome"
          [:div {:class styles/welcome-section}
           [:div {:class styles/welcome-content}
            [:h1 {:class styles/welcome-heading} 
             "Welcome to XTDB Items Manager"]
            [:p {:class styles/welcome-description} 
             "A modern web application showcasing XTDB integration with Clojure, 
              featuring a clean interface and robust data management capabilities."]
            [:div {:class styles/features-container}
             [:h2 {:class styles/heading-2} "Key Features"]
             [:ul {:class styles/item-list}
              [:li {:class styles/feature-item}
               [:span {:class styles/checkmark} "✓"]
               "Full CRUD operations with a clean, intuitive interface"]
              [:li {:class styles/feature-item}
               [:span {:class styles/checkmark} "✓"]
               "Persistent data storage powered by XTDB"]
              [:li {:class styles/feature-item}
               [:span {:class styles/checkmark} "✓"]
               "RESTful API endpoints for system integration"]
              [:li {:class styles/feature-item}
               [:span {:class styles/checkmark} "✓"]
               "Modern, responsive design for all devices"]]]
            [:div {:class "mt-8"}
             [:a.btn {:href (url-for ::items)
                      :class styles/get-started-button} 
              "Get Started →"]]]]))

;; CRUD handlers
(defn list-items-handler
  "Lists all items and provides a form to create new ones.
   Supports both full page loads and HTMX partial updates."
  [req]
  (let [node @config/xtdb-node
        search (get-in req [:query-params "search"])
        status (get-in req [:query-params "status"])
        priority (get-in req [:query-params "priority"])
        assigned-to (get-in req [:query-params "assigned-to"])
        tag (get-in req [:query-params "tag"])
        as-of (some-> (get-in req [:query-params "as-of"])
                     parse-datetime)
        query (build-query search status priority assigned-to tag)
        params {:search search
                                        :status status
                                        :priority priority
                                        :assigned-to assigned-to
                                        :tag tag}
        items (execute-query node query params as-of)
        content [:div {:class styles/container}
                [:div {:class styles/header-row}
                [:h1 {:class styles/heading-1} "Items"]
                 [:a {:href (url-for ::item-new)
                      :class styles/button-primary}
                  "Create New Item"]]
                ;; Search and filter form
                [:form {:method "get" :class styles/search-container}
                 [:div {:class styles/search-row}
                  [:input {:type "text"
                          :name "search"
                          :value search
                          :placeholder "Search items..."
                          :class styles/search-input}]
                  [:select {:name "status" :class styles/select}
                   [:option {:value ""} "All Statuses"]
                   [:option {:value "active"} "Active"]
                   [:option {:value "completed"} "Completed"]
                   [:option {:value "pending"} "Pending"]
                   [:option {:value "archived"} "Archived"]]
                  [:select {:name "priority" :class styles/select}
                   [:option {:value ""} "All Priorities"]
                   [:option {:value "high"} "High"]
                   [:option {:value "medium"} "Medium"]
                   [:option {:value "low"} "Low"]]
                  [:input {:type "text"
                          :name "assigned-to"
                          :value assigned-to
                          :placeholder "Assigned to..."
                          :class styles/input}]
                  [:input {:type "text"
                          :name "tag"
                          :value tag
                          :placeholder "Filter by tag..."
                          :class styles/input}]]
                 [:div {:class styles/search-row}
                  [:input {:type "datetime-local"
                          :name "as-of"
                          :value as-of
                          :class styles/input}]
                  [:span {:class styles/input-help}
                   "View items as of this date"]
                  [:button {:type "submit"
                           :class styles/button-primary}
                   "Search"]
                  [:a {:href (url-for ::items)
                      :class styles/button-secondary}
                   "Clear"]]]
                ;; Items list
                [:ul {:class styles/item-list}
                 (map render-item items)]]
        ;; Check if this is a boosted request
        is-boosted? (get-in req [:headers "hx-boosted"])]
    (if is-boosted?
      ;; Return just the content for boosted requests
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (hiccup/html content)}
      ;; Return full page for regular requests
      (layout "Items" content))))

(defn create-item-handler
  "Creates a new item from form or JSON data.
   Returns either a redirect or HTMX update based on request type."
  [req]
  (let [body (:form-params req)
        node @config/xtdb-node
        name (get body "name")
        valid-item {:xt/id (java.util.UUID/randomUUID)
                   :name name
                   :slug (generate-slug name)
                   :description (get body "description")
                   :status (get body "status" "active")
                   :priority (get body "priority" "medium")
                   :tags (when-let [tags (get body "tags")]
                          (clj-str/split tags #",\s*"))
                   :created-at (java.time.Instant/now)
                   :due-date (get body "due-date")
                   :assigned-to (get body "assigned-to")}]
    (xt/submit-tx node [[:put-docs {:into :items} valid-item]])
    {:status 303
     :headers {"Location" (url-for ::items)}}))

(defn get-item-handler
  "Handles GET requests to retrieve a single item by ID.
  Returns an HTML page with the item details, or a 404 if not found."
  [req]
  (let [node @config/xtdb-node
        slug (get-in req [:path-params :slug])
        tab (get-in req [:query-params "tab"] "details")]
    (if-let [item (first (xt/q node '(from :items [*] (where (= :slug ?slug))) {:args {:slug slug}}))]
      (let [content [:div {:class styles/container}
                    [:div {:class styles/card}
                     [:h1 {:class styles/heading-1} "Item Details"]
                     [:div {:class styles/tabs-list}
                      [:a {:href (url-for ::item-get {:slug (:slug item)})
                           :class (if (= tab "details") 
                                   styles/tab-item-active
                                   styles/tab-item)} 
                        "Details"]
                      [:a {:href (url-for ::item-get {:slug (:slug item)} {:tab "update"})
                           :class (if (= tab "update")
                                   styles/tab-item-active
                                   styles/tab-item)} 
                        "Update"]
                      [:a {:href (url-for ::item-get {:slug (:slug item)} {:tab "patch"})
                           :class (if (= tab "patch")
                                   styles/tab-item-active
                                   styles/tab-item)}
                        "Patch"]]
                     (case tab
                       "details" (render-item-details item)
                       "update"  (render-item-update-form item)
                       "patch"   (render-item-patch-form item))]]]
        (layout (str "Item - " (:name item)) content))
      {:status 404 :body "Item not found"})))

(defn update-item-handler
  "Handles PUT requests to update an existing item."
  [req]
  (let [node @config/xtdb-node
        slug (get-in req [:path-params :slug])
        body (if (= (get-in req [:headers "content-type"]) "application/json")
               (:body req)
               (:form-params req))
        updated-item {:name (get body "name")
                     :description (get body "description")}]
    (if-let [existing-item (first (xt/q node
                        '(from :items [*]
                                              (where (= :slug ?slug)))
                                       {:args {:slug slug}}))]
      (let [new-item (merge existing-item updated-item)]
        (xt/submit-tx node [[:put-docs {:into :items} new-item]])
            {:status 303
         :headers {"Location" (url-for ::item-get {:slug slug})}})
      {:status 404 :body "Item not found"})))

(defn patch-item-handler
  "Handles PATCH requests to partially update an item."
  [req]
  (let [node @config/xtdb-node
        slug (get-in req [:path-params :slug])
        form-params (:form-params req)
        updates (into {} (filter (fn [[_ v]] (seq v))
                               {:name (get form-params "name")
                                :description (get form-params "description")}))]
    (if-let [existing-item (first (xt/q node
                                       '(from :items [*]
                                              (where (= :slug ?slug)))
                                       {:args {:slug slug}}))]
      (let [merged-item (merge existing-item updates)]
        (xt/submit-tx node [[:put-docs {:into :items} merged-item]])
        {:status 303
         :headers {"Location" (url-for ::item-get {:slug slug})}})
      {:status 404 
       :body "Item not found"})))

(defn delete-item-post-handler
  "Handles POST requests to /items/:id/delete (used for form submission).
  Redirects to /items after deletion."
  [req]
  (let [node @config/xtdb-node
        slug (get-in req [:path-params :slug])]
    (when-let [item (first (xt/q node
                                '(from :items [*]
                                       (where (= :slug ?slug)))
                                {:args {:slug slug}}))]
      (xt/submit-tx node [[:delete-docs {:from :items} (:xt/id item)]]))
    {:status 303
     :headers {"Location" (url-for ::items)}}))

(defn create-item-form-handler
  "Renders the create item form page."
  [_req]
  (let [content [:div {:class styles/container}
                 [:h1 {:class styles/heading-1} "Create New Item"]
                 [:form {:method "post"
                        :action "/items"
                        :class styles/form-container}
                  [:div {:class styles/form-group}
                   [:label {:class styles/label} "Name *"]
                   [:input {:type "text"
                           :name "name"
                           :required true
                           :class styles/input}]]
                  [:div {:class styles/form-group}
                   [:label {:class styles/label} "Description *"]
                   [:textarea {:name "description"
                             :required true
                             :class styles/textarea}]]
                  [:div {:class styles/form-row}
                   [:div {:class styles/form-group}
                    [:label {:class styles/label} "Status"]
                    [:select {:name "status" :class styles/select}
                     [:option {:value "active"} "Active"]
                     [:option {:value "pending"} "Pending"]
                     [:option {:value "completed"} "Completed"]
                     [:option {:value "archived"} "Archived"]]]
                   [:div {:class styles/form-group}
                    [:label {:class styles/label} "Priority"]
                    [:select {:name "priority" :class styles/select}
                     [:option {:value "medium"} "Medium"]
                     [:option {:value "high"} "High"]
                     [:option {:value "low"} "Low"]]]]
                  [:div {:class styles/form-group}
                   [:label {:class styles/label} "Tags"]
                   [:input {:type "text"
                           :name "tags"
                           :placeholder "tag1, tag2, tag3"
                           :class styles/input}]]
                  [:div {:class styles/form-row}
                   [:div {:class styles/form-group}
                    [:label {:class styles/label} "Due Date"]
                    [:input {:type "date"
                            :name "due-date"
                            :class styles/input}]]
                   [:div {:class styles/form-group}
                    [:label {:class styles/label} "Assigned To"]
                    [:input {:type "text"
                            :name "assigned-to"
                            :class styles/input}]]]
                  [:div {:class styles/form-actions}
                   [:button {:type "submit"
                            :class styles/button-primary}
                    "Create Item"]
                   [:a {:href (url-for ::items)
                       :class styles/button-secondary}
                    "Cancel"]]]]
        ]
    (layout "Create Item" content)))

;; Router configuration
(def app
  "Main application router.
   Defines routes and their handlers, along with middleware chain.
   Supports both HTML and JSON responses."
  (let [router (ring/router
                [["/" {:name ::home
                       :get {:handler home-handler
                 :interceptors interceptor-chain}}]
                 ["/items" {:name ::items
                           :get {:handler list-items-handler
                      :interceptors interceptor-chain}
                :post {:handler create-item-handler
                                 :interceptors interceptor-chain}}]
                 ["/items/new" {:name ::item-new
                               :get {:handler create-item-form-handler
                                    :interceptors interceptor-chain}}]
                 ["/items/:slug/get" {:name ::item-get
                                 :get {:handler get-item-handler
                                      :interceptors interceptor-chain}}]
                 ["/items/:slug/update" {:name ::item-update
                                       :post {:handler update-item-handler
                                             :interceptors interceptor-chain}}]
                 ["/items/:slug/patch" {:name ::item-patch
                                      :post {:handler patch-item-handler
                                :interceptors interceptor-chain}}]
                 ["/items/:slug/delete" {:name ::item-delete
                                       :post {:handler delete-item-post-handler
                                 :interceptors interceptor-chain}}]]
    {:data {:middleware [wrap-params
                        wrap-json-response
                        [wrap-json-body {:keywords? true}]
                        [wrap-cors :access-control-allow-origin [#".*"]
                         :access-control-allow-methods [:get :post]]]}})
        handler (ring/ring-handler router (ring/create-default-handler))]
    (reset! router-state router)
    handler))

;; Server state management
(defstate server
  "Jetty server state managed by Mount.
   Starts on defined port and can be stopped gracefully."
  :start (do
           (log/info "Starting web server on port" port)
           (jetty/run-jetty app {:port port :join? false}))
  :stop (do
          (log/info "Stopping web server")
          (.stop server)))

;; Application initialization
(defn init!
  "Initializes the application:
   1. Starts Mount states
   2. Verifies XTDB connection
   3. Seeds initial data if needed"
  []
  (log/info "Initializing application...")
  (mount/start)
  (log/info "Mount states started:")
  (log/info "Active states:" (mount/running-states))
  (let [node @config/xtdb-node]
    (log/info "XTDB connection test:")
    (log/info "Node status:" (xt/status node))
    (try
      (seed/seed-data!)
      (catch Exception e
        (log/warn "Seed data may already exist:" (.getMessage e)))))
  (log/info "Application started successfully"))

(defn -main [& [port-arg]]
  (let [server-port (Integer. (or port-arg (System/getenv "PORT") (str port)))]
    (alter-var-root #'port (constantly server-port))
    (init!)
    (log/info "Application started successfully")))

;; Helper function implementations
(defn parse-uuid-str
  "Safely parses UUID string, returns nil if invalid"
  [id-str]
  (try
    (java.util.UUID/fromString id-str)
    (catch IllegalArgumentException _
      nil)))

(defn parse-datetime
  "Safely parses a datetime string into an Instant, adding seconds if needed"
  [datetime-str]
  (when datetime-str
    (try
      (if (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}" datetime-str)
        ;; Add seconds if they're missing
        (java.time.Instant/parse (str datetime-str ":00Z"))
        (java.time.Instant/parse datetime-str))
      (catch Exception _
        nil))))

;; Interceptor implementations
(def log-request-interceptor
  "Logs incoming requests for debugging"
  {:name ::log-request
   :enter (fn [context]
            (log/info "Request:" (:request context))
            context)})

(def error-handler-interceptor
  "Provides consistent error handling across all routes.
   Converts exceptions to appropriate HTTP responses."
  {:name ::error-handler
   :error (fn [context error]
            (log/error "Error handling request:" error)
            (let [status (if (instance? Exception error)
                          (cond
                            (instance? xtdb.IllegalArgumentException error) 400
                            :else 500)
                          500)]
              (assoc context :response {:status status
                                      :body (str "Error: " (.getMessage error))})))})

(def interceptor-chain
  "Order of interceptors to be applied to all routes"
  [log-request-interceptor
   error-handler-interceptor])

;; Query building and execution implementations
(defn build-query
  "Constructs an XTQL query based on filter parameters.
   Returns a query that filters items based on provided criteria."
  [search status priority assigned-to tag]
  (let [base-query '(from :items [*])]
    (cond-> base-query
      search (concat '((where (or (str-contains? name ?search)
                                 (str-contains? description ?search)))))
      status (concat '((where (= status ?status))))
      priority (concat '((where (= priority ?priority))))
      assigned-to (concat '((where (= assigned-to ?assigned-to))))
      tag (concat '((where (contains? tags ?tag)))))))

(defn execute-query
  "Executes an XTQL query with optional temporal parameters.
   Supports as-of queries for temporal features."
  [node query params as-of]
  (let [query-args {:args params}
        query-args (if as-of 
                    (assoc query-args :as-of as-of)
                    query-args)]
    (xt/q node query query-args)))

(defn render-item
  "Renders a single item as Hiccup markup.
   Includes status badges, priority indicators, and item details."
  [item]
  (let [desc (:description item)
        truncated-desc (if (> (count desc) 100)
                        (str (subs desc 0 100) "...")
                        desc)]
    [:li {:class styles/item-list-item}
     [:a {:href (url-for ::item-get {:slug (:slug item)})
          :class styles/item-link}
      [:div {:class styles/item-content}
       [:div {:class styles/item-header}
        [:span {:class styles/item-text} (:name item)]
        [:div {:class styles/item-meta}
         [:span {:class (str styles/status-badge " " 
                           (get {"active" styles/status-active
                                "completed" styles/status-completed
                                "pending" styles/status-pending
                                "archived" styles/status-archived}
                                (:status item)))}
          (:status item)]
         [:span {:class (str styles/status-badge " "
                           (get {"high" styles/priority-high
                                "medium" styles/priority-medium
                                "low" styles/priority-low}
                                (:priority item)))}
          (:priority item)]]]
       [:div {:class styles/item-details}
        [:p {:class styles/item-description} 
         truncated-desc]
        [:div {:class styles/item-footer}
         [:div {:class styles/tags-container}
          (for [tag (:tags item)]
            [:span {:class styles/tag} tag])]
         [:div {:class styles/item-meta}
          [:span {:class styles/meta-text}
           "Due: " (:due-date item)]
          [:span {:class styles/meta-text}
           "Assigned: " (:assigned-to item)]]]]]]]))

;; Update url-for to use router-state
(defn url-for
  "Generate URLs for named routes with optional params and query strings"
  ([route-name]
   (url-for route-name nil nil))
  ([route-name path-params]
   (url-for route-name path-params nil))
  ([route-name path-params query-params]
   (let [router @router-state
         match (reitit.core/match-by-name router route-name path-params)
         base-url (reitit.core/match->path match)]
     (if query-params
       (str base-url "?" (ring.util.codec/form-encode query-params))
       base-url))))

(defn generate-slug
  "Generates a URL-friendly slug from a name with a random suffix"
  [name]
  (let [base-slug (-> name
                      clojure.string/lower-case
                      (clojure.string/replace #"[^a-z0-9]+" "-")
                      (clojure.string/replace #"^-+|-+$" ""))
        random-suffix (-> (random-uuid) str (subs 0 8))]
    (str base-slug "-" random-suffix)))

;; Update the format-date function to use correct tick functions
(defn format-date
  "Formats dates consistently across the application using tick"
  [date]
  (when date
    (try
      (cond
        (string? date)
        (-> date
            t/date  ; For date strings like "2024-03-15"
            (t/format (t/formatter "MMM d, yyyy")))
        
        (instance? java.time.Instant date)
        (-> date
            t/instant  ; For Instant timestamps
            (t/in "UTC")
            (t/format (t/formatter "MMM d, yyyy HH:mm")))
        
        :else
        (str date))
      (catch Exception _
        (str date)))))

;; Add reusable components
(defn render-status-badge [status]
  [:span {:class (str styles/status-badge " " 
                     (get {"active" styles/status-active
                          "completed" styles/status-completed
                          "pending" styles/status-pending
                          "archived" styles/status-archived}
                          status))}
   status])

(defn render-priority-badge [priority]
  [:span {:class (str styles/status-badge " "
                     (get {"high" styles/priority-high
                          "medium" styles/priority-medium
                          "low" styles/priority-low}
                          priority))}
   priority])

(defn render-tags [tags]
  [:div {:class styles/tags-container}
   (for [tag tags]
     [:span {:class styles/tag} tag])])

;; Update the render-item-details function's date handling
(defn render-item-details [item]
  [:div {:class "mt-6"}
   [:div {:class styles/item-details}
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "ID: "]
     [:span {:class styles/item-details-value} (str (:xt/id item))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Name: "]
     [:span {:class styles/item-details-value} (:name item)]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Description: "]
     [:span {:class styles/item-details-value} (:description item)]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Status: "]
     [:span {:class styles/item-details-value} (render-status-badge (:status item))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Priority: "]
     [:span {:class styles/item-details-value} (render-priority-badge (:priority item))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Tags: "]
     [:span {:class styles/item-details-value} (render-tags (:tags item))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Created: "]
     [:span {:class styles/item-details-value} 
      (when-let [created-at (:created-at item)]
        (format-date created-at))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Due Date: "]
     [:span {:class styles/item-details-value} (format-date (:due-date item))]]
    [:div {:class styles/item-details-row}
     [:strong {:class styles/item-details-label} "Assigned To: "]
     [:span {:class styles/item-details-value} (:assigned-to item)]]]
   [:div {:class "flex justify-end mt-4"}
    [:form {:method "post" 
            :action (url-for ::item-delete {:slug (:slug item)})
            :hx-post (url-for ::item-delete {:slug (:slug item)})
            :hx-target "main"
            :hx-swap "outerHTML"}
     [:button {:type "submit" :class styles/button-danger} "Delete"]]]])

;; Update form component
(defn render-item-update-form [item]
  [:div {:class "mt-6"}
   [:form {:method "post" 
           :action (url-for ::item-update {:slug (:slug item)})
           :class styles/form-container}
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Name *"]
     [:input {:type "text" 
             :name "name"
             :value (:name item)
            ;;  :required true
             :readonly true
             :class styles/input}]]
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Description *"]
     [:textarea {:name "description"
                :required true
                :class styles/textarea} (:description item)]]
    [:div {:class styles/form-row}
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Status"]
      [:select {:name "status" :class styles/select}
       (for [status ["active" "pending" "completed" "archived"]]
         [:option {:value status
                  :selected (= status (:status item))} 
          status])]]
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Priority"]
      [:select {:name "priority" :class styles/select}
       (for [priority ["high" "medium" "low"]]
         [:option {:value priority
                  :selected (= priority (:priority item))} 
          priority])]]]
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Tags"]
     [:input {:type "text"
             :name "tags"
             :value (when (:tags item)
                     (clojure.string/join ", " (:tags item)))
             :placeholder "tag1, tag2, tag3"
             :class styles/input}]]
    [:div {:class styles/form-row}
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Due Date"]
      [:input {:type "date"
              :name "due-date"
              :value (:due-date item)
              :class styles/input}]]
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Assigned To"]
      [:input {:type "text"
              :name "assigned-to"
              :value (:assigned-to item)
              :class styles/input}]]]
    [:div {:class "flex justify-end mt-4"}
     [:button {:type "submit" :class styles/button-primary} "Update"]]]])

;; Update the patch form component to include all fields
(defn render-item-patch-form [item]
  [:div {:class "mt-6"}
   [:form {:method "post" 
           :action (url-for ::item-patch {:slug (:slug item)})
           :class styles/form-container}
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Name (optional)"]
     [:input {:type "text"
             :name "name"
             :disabled true
             :placeholder (:name item)
             :class styles/input}]]
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Description (optional)"]
     [:textarea {:name "description"
                :placeholder (:description item)
                :class styles/textarea}]]
    [:div {:class styles/form-row}
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Status (optional)"]
      [:select {:name "status" :class styles/select}
       [:option {:value ""} "No change"]
       (for [status ["active" "pending" "completed" "archived"]]
         [:option {:value status} status])]]
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Priority (optional)"]
      [:select {:name "priority" :class styles/select}
       [:option {:value ""} "No change"]
       (for [priority ["high" "medium" "low"]]
         [:option {:value priority} priority])]]]
    [:div {:class styles/form-group}
     [:label {:class styles/label} "Tags (optional)"]
     [:input {:type "text"
             :name "tags"
             :placeholder (when (:tags item)
                          (clojure.string/join ", " (:tags item)))
             :class styles/input}]]
    [:div {:class styles/form-row}
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Due Date (optional)"]
      [:input {:type "date"
              :name "due-date"
              :placeholder (:due-date item)
              :class styles/input}]]
     [:div {:class styles/form-group}
      [:label {:class styles/label} "Assigned To (optional)"]
      [:input {:type "text"
              :name "assigned-to"
              :placeholder (:assigned-to item)
              :class styles/input}]]]
    [:div {:class "flex justify-end mt-4"}
     [:button {:type "submit" :class styles/button-primary} "Patch"]]]])

;; REPL development helpers
(comment
  "Development and debugging helpers for REPL use.
   Includes database queries, state management, and test data creation."
  (in-ns 'my-app.handler)
  ;; Imports
  (require '[clojure.string :as clj-str])
  (require '[xtdb.api :as xt])
  (require '[honey.sql :as sql]
           '[honey.sql.helpers :refer [select from where
                                       delete-from erase-from
                                       insert-into patch-into values
                                       records]])

  (require '[mount.core :as mount])
  ;; Mount all states
  (mount/start)
  (mount/stop)
  ;; XTDB node is available in the config namespace
  (def node @config/xtdb-node)
  ;;;; XTQL queries
  ;; Queries
  (xt/q node '(from :items [*]))
  ;; Insert a new item
  (def new-item {:xt/id (java.util.UUID/randomUUID)
                 :name "Test Item"
                 :description "This is a test item"})
  (xt/submit-tx node [[:put-docs {:into :items} new-item]])
  ;;;; HoneySQL queries
  ;; Queries
  (sql/format {:select [:*] :from :items})
  (xt/q node (sql/format {:select [:*] :from :items}))
  (xt/q node (sql/format {:select [:*] :from :items
                          :where [:= :name "Test Item"]}))
  ;; Insert a new item
  (sql/format {:insert-into :items
               :columns [:xt/id :name :description]
               :values [new-item]})
  (clj-str/join " "
                (sql/format {:insert-into :items
                                    ;; :columns [:xt/id :name :description] ;; not needed
                             :values [new-item]}))
  (xt/submit-tx node [(clj-str/join " "
                                    (sql/format {:insert-into :items
                                                        ;; :columns [:xt/id :name :description] ;; not needed
                                                 :values [new-item]}))])
  ;;Update an item
  (xt/q node [(clj-str/join " "
                            (sql/format {:patch-into :items
                                         :values [{:description "Updated Item"}]
                                         :where [:= :name "Test Item"]}))])
  ;; Delete an item
  (xt/submit-tx node [(clj-str/join " "
                                    (sql/format {:delete-from :items
                                                 :where [:= :name "Test Item"]}))])
  ;;
  )
