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
            [my-app.styles :as styles])
  (:gen-class))

;; Forward declarations
;; Server and state management
(declare server)

;; Route handlers
(declare home-handler)                    ; Main page handler
(declare list-items-handler)              ; GET /items
(declare create-item-handler)             ; POST /items
(declare get-item-handler)                ; GET /items/:id
(declare update-item-handler)             ; PUT /items/:id
(declare patch-item-handler)              ; PATCH /items/:id
(declare delete-item-handler)             ; DELETE /items/:id
(declare delete-item-post-handler)        ; POST /items/:id/delete

;; Helper functions
(declare parse-uuid-str)                  ; UUID parsing utility

;; Request processing pipeline
(declare log-request-interceptor          ; Request logging
        error-handler-interceptor         ; Error handling
        interceptor-chain)                ; Interceptor ordering

;; HTTP utilities
(declare wrap-method-override)            ; HTML form method override support

;; Configuration
(def port 58950)

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
             [:a.btn {:href "/items" :class styles/get-started-button} 
              "Get Started →"]]]]))

;; CRUD handlers
(defn list-items-handler
  "Lists all items and provides a form to create new ones.
   Supports both full page loads and HTMX partial updates."
  [_req]
  (let [node @config/xtdb-node
        items (xt/q node (sql/format {:select [:*] :from :items}))
        content [:div {:class styles/container}
                [:h1 {:class styles/heading-1} "Items"]
                [:ul {:class styles/item-list}
                 (for [item items]
                   [:li {:class styles/item-list-item}
                    [:a {:href (str "/items/" (:xt/id item))
                         :class styles/item-link}
                     [:div {:class styles/item-content}
                      [:span {:class styles/checkmark} "✓"]
                      [:span {:class styles/item-text} (:name item)]]]])]
                [:hr {:class styles/divider}]
                [:div {:class styles/card}
                 [:h2 {:class styles/heading-2} "Create New Item"]
                 [:form {:method "post" 
                        :action "/items"
                        :class styles/form-container}
                  [:div {:class styles/form-group}
                   [:label {:class styles/label} "Name:"]
                   [:input#name {:name "name" :type "text" :class styles/input}]]
                  [:div {:class styles/form-group}
                   [:label {:class styles/label} "Description:"]
                   [:textarea#description {:name "description" :class styles/textarea} ""]]
                  [:div {:class styles/form-group}
                   [:button {:type "submit" :class styles/button-primary} "Create"]]]]]]
    (layout "Items" content)))

(defn create-item-handler
  "Creates a new item from form or JSON data.
   Returns either a redirect or HTMX update based on request type."
  [req]
  (let [body (:form-params req)
        node @config/xtdb-node
        valid-item {:xt/id (java.util.UUID/randomUUID)
                   :name (get body "name")
                   :description (get body "description")}]
    (xt/submit-tx node [[:put-docs {:into :items} valid-item]])
    {:status 303
     :headers {"Location" "/items"}}))

(defn get-item-handler
  "Handles GET requests to retrieve a single item by ID.
  Returns an HTML page with the item details, or a 404 if not found."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        tab (get-in req [:query-params "tab"] "details")]
    (if-let [item (and id (first (xt/q node '(from :items [*] (where (= :xt/id ?id))) {:args {:id id}})))]
      (let [content [:div {:class styles/container}
                    [:div {:class styles/card}
                     [:h1 {:class styles/heading-1} "Item Details"]
                     [:div {:class styles/tabs-list}
                      [:a {:href (str "/items/" id "?tab=details")
                           :class (if (= tab "details") 
                                   styles/tab-item-active
                                   styles/tab-item)} 
                        "Details"]
                      [:a {:href (str "/items/" id "?tab=update")
                           :class (if (= tab "update")
                                   styles/tab-item-active
                                   styles/tab-item)} 
                        "Update"]
                      [:a {:href (str "/items/" id "?tab=patch")
                           :class (if (= tab "patch")
                                   styles/tab-item-active
                                   styles/tab-item)}
                        "Patch"]]
                     (case tab
                       "details"
                       [:div {:class "mt-6"}
                        [:div {:class styles/item-details}
                         [:p {:class styles/item-details-row}
                          [:strong {:class styles/item-details-label} "ID: "]
                          [:span {:class styles/item-details-value} (str (:xt/id item))]]
                         [:p {:class styles/item-details-row}
                          [:strong {:class styles/item-details-label} "Name: "]
                          [:span {:class styles/item-details-value} (:name item)]]
                         [:p {:class styles/item-details-row}
                          [:strong {:class styles/item-details-label} "Description: "]
                          [:span {:class styles/item-details-value} (:description item)]]]
                        [:div {:class "flex justify-end mt-4"}
                         [:form {:method "post" 
                                :action (str "/items/" id "/delete")
                                :hx-post (str "/items/" id "/delete")
                                :hx-target "main"
                                :hx-swap "outerHTML"}
                          [:button {:type "submit" :class styles/button-danger} "Delete"]]]]
                       
                       "update"
                       [:div {:class "mt-6"}
                        [:form {:method "post" 
                               :action (str "/items/" id "/update")
                               :class styles/form-container}
                         [:div {:class styles/form-group}
                          [:label {:class styles/label} "Name:"]
                          [:input {:type "text" 
                                  :name "name"
                                  :value (:name item)
                                  :class styles/input}]]
                         [:div {:class styles/form-group}
                          [:label {:class styles/label} "Description:"]
                          [:textarea {:name "description"
                                    :class styles/textarea} (:description item)]]
                         [:div {:class "flex justify-end mt-4"}
                          [:button {:type "submit" :class styles/button-primary} "Update"]]]]
                       
                       "patch"
                       [:div {:class "mt-6"}
                        [:form {:method "post" 
                               :action (str "/items/" id "/patch")
                               :class styles/form-container}
                         [:div {:class styles/form-group}
                          [:label {:class styles/label} "Name (optional):"]
                          [:input {:type "text"
                                  :name "name"
                                  :class styles/input}]]
                         [:div {:class styles/form-group}
                          [:label {:class styles/label} "Description (optional):"]
                          [:textarea {:name "description"
                                    :class styles/textarea}]]
                         [:div {:class "flex justify-end mt-4"}
                          [:button {:type "submit" :class styles/button-primary} "Patch"]]]])]]]
        (layout (str "Item - " (:name item)) content))
      {:status 404 :body "Item not found"})))

(defn update-item-handler
  "Handles PUT requests to update an existing item."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        _ (log/info "Updating item with ID:" id)
        body (if (= (get-in req [:headers "content-type"]) "application/json")
               (:body req)  ; JSON request
               (:form-params req))  ; Form submission
        updated-item {:name (get body "name")
                     :description (get body "description")}
        _ (log/info "Update data:" updated-item)]
    (cond
      (not (and (:name updated-item) (:description updated-item)))
      (do
        (log/warn "Missing required fields for PUT request")
        {:status 400
         :body "PUT requests require all fields (name and description)"})
      
      (not (first (xt/q node
                        '(from :items [*]
                               (where (= :xt/id ?id)))
                        {:args {:id id}})))
      (do
        (log/warn "Item not found for update:" id)
        {:status 404 :body "Item not found"})
      
      :else
      (do
        (log/info "Replacing item with new data")
        (let [new-item {:xt/id id
                       :name (:name updated-item)
                       :description (:description updated-item)}
              tx-result (xt/submit-tx node [[:put-docs {:into :items} new-item]])]
          (log/info "Transaction result:" tx-result)
          (if (= (get-in req [:headers "content-type"]) "application/json")
            ;; JSON API response
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/generate-string new-item)}
            ;; Form submission redirect
            {:status 303
             :headers {"Location" (str "/items/" id)}}))))))

(defn patch-item-handler
  "Handles PATCH requests to partially update an item."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        form-params (:form-params req)
        ;; Only include non-empty values and convert keys to keywords
        updates (into {} (filter (fn [[_ v]] (not (empty? v)))
                               {:name (get form-params "name")
                                :description (get form-params "description")}))]
    (if-let [existing-item (first (xt/q node
                                       '(from :items [*]
                                              (where (= :xt/id ?id)))
                                       {:args {:id id}}))]
      (let [merged-item (merge existing-item updates {:xt/id id})]
        (xt/submit-tx node [[:put-docs {:into :items} merged-item]])
        {:status 303
         :headers {"Location" (str "/items/" id)}})
      {:status 404 
       :body "Item not found"})))

(defn delete-item-handler
  "Handles DELETE requests to delete an item by ID.
    Returns 204 No Content on success, or 404 if not found."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        _ (log/info "Attempting to delete item with ID:" id)
        existing-item (and id
                           (first (xt/q node
                                        '(from :items [*]
                                               (where (= :xt/id ?id)))
                                        {:args {:id id}})))]
    (if existing-item
      (do
        (log/info "Found item to delete:" existing-item)
        (log/info "Submitting transaction to delete item:" id)
        (log/info "Transaction: [:delete-docs {:from :items} id]")
        (let [tx-result (xt/submit-tx node [[:delete-docs {:from :items} id]])]
          (log/info "Transaction result:" tx-result))
        {:status 204 :body nil})
      (do
        (log/warn "Item not found for deletion:" id)
        {:status 404 :body "Item not found"}))))

(defn delete-item-post-handler
  "Handles POST requests to /items/:id/delete (used for form submission).
  Redirects to /items after deletion."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)]
    (when id
      (log/info "Submitting transaction to delete item (POST handler):" id)
      (log/info "Transaction: [:delete-docs {:from :items} id]")
      (let [tx-result (xt/submit-tx node [[:delete-docs {:from :items} id]])]
        (log/info "Transaction result:" tx-result)))
    {:status 303
     :headers {"Location" "/items"}}))

(defn wrap-method-override [handler]
  (fn [request]
    (let [method (or (get-in request [:form-params "_method"])
                     (get-in request [:headers "x-http-method-override"]))]
      (if method
        (handler (assoc request :request-method 
                       (keyword (clj-str/lower-case method))))
        (handler request)))))

;; Router configuration
(def app
  "Main application router.
   Defines routes and their handlers, along with middleware chain.
   Supports both HTML and JSON responses."
  (ring/ring-handler
   (ring/router
    [["/" {:get {:handler home-handler
                 :interceptors interceptor-chain}}]
     ["/items" {:get {:handler list-items-handler
                      :interceptors interceptor-chain}
                :post {:handler create-item-handler
                       :interceptors interceptor-chain}}]
     ["/items/:id" {:get {:handler get-item-handler
                          :interceptors interceptor-chain}}]
     ["/items/:id/update" {:post {:handler update-item-handler  ; PUT -> POST /items/:id/update
                                 :interceptors interceptor-chain}}]
     ["/items/:id/patch" {:post {:handler patch-item-handler    ; PATCH -> POST /items/:id/patch
                                :interceptors interceptor-chain}}]
     ["/items/:id/delete" {:post {:handler delete-item-post-handler
                                 :interceptors interceptor-chain}}]]
    {:data {:middleware [wrap-params
                        wrap-json-response
                        [wrap-json-body {:keywords? true}]
                        [wrap-cors :access-control-allow-origin [#".*"]
                         :access-control-allow-methods [:get :post]]]}})
   (ring/routes
    (ring/create-default-handler))))

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

;; Middleware implementations
(defn wrap-method-override
  "Allows HTML forms to use PUT/PATCH/DELETE via _method parameter"
  [handler]
  (fn [request]
    (if-let [method (get-in request [:form-params "_method"])]
      (handler (assoc request :request-method (keyword (clj-str/lower-case method))))
      (handler request))))

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
