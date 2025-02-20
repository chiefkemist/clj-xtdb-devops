(ns my-app.handler
  (:require [clojure.string :as clj-str]
            [xtdb.api :as xt]
            [honey.sql :as sql]
            ;; [honey.sql.helpers :refer [select from where
            ;;                            delete-from erase-from
            ;;                            insert-into patch-into values
            ;;                            records]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [mount.core :as mount :refer [defstate]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [hiccup.core :as hiccup]
            [hiccup.form :as hf]
            [my-app.config :as config]
            [my-app.seed :as seed]
            [ring.middleware.cors :refer [wrap-cors]])
  (:gen-class))

(def port 58950)

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hiccup/html body)})

(def log-request-interceptor
  {:name ::log-request
   :enter (fn [context]
            (log/info "Request:" (:request context))
            context)})

(def error-handler-interceptor
  {:name ::error-handler
   :error (fn [context error]
            (log/error "Error handling request:" (:request context))
            (log/error "Error details:" error)
            (log/error "Stack trace:" (.getStackTrace error))
            (let [status (if (instance? Exception error)
                           (cond
                             (and (.getCause error) (instance? java.io.IOException (.getCause error)))
                             400
                             (instance? xtdb.IllegalArgumentException error)
                             400
                             :else 500)
                           500)]
              (assoc context :response {:status status
                                        :headers {"Content-Type" "text/plain"}
                                        :body (str "Error: " (.getMessage error))})))})

(def interceptor-chain
  [log-request-interceptor
   error-handler-interceptor])

;; --- Helper Functions ---

(defn- parse-uuid-str [id-str]
  (try
    (java.util.UUID/fromString id-str)
    (catch IllegalArgumentException _
      nil)))

;; --- CRUD Operations ---

(defn create-item-handler
  "Handles POST requests to create a new item.
  Expects a JSON body with :name and :description."
  [req]
  (let [body (-> req :body)  ; body is already parsed to a map by wrap-json-body
        _ (log/info "Creating new item:" body)
        node @config/xtdb-node
        valid-item (-> {:xt/id (java.util.UUID/randomUUID)
                        :name (:name body)
                        :description (:description body)})]
    (log/info "Submitting transaction to create item:" valid-item)
    (log/info "Transaction: [:put-docs {:into :items} valid-item]")
    ;; (let [tx-result (xt/submit-tx node [[:put-docs {:into :items} valid-item]])]
    ;;   (log/info "Transaction result:" tx-result))
    (let [tx-result (clj-str/join " "
                                  (sql/format {:insert-into :items
                                               :values [valid-item]}))]
      (log/info "Transaction result:" tx-result))
    (log/info "Item created successfully:" (:xt/id valid-item))
    {:status 201
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string valid-item)}))

(defn list-items-handler
  "Handles GET requests to list all items.
  Returns an HTML page with a list of items and a form to create a new item."
  [_req]
  (log/info "Listing all items")
  (let [node @config/xtdb-node
        _ (log/info "XTDB node retrieved:" node)
        _ (log/info "XTDB node status:" (xt/status node))
        ;; items (xt/q node '(from :items [*]))
        items (xt/q node (sql/format {:select [:*] :from :items}))]
    (log/info "Query executed, found" (count items) "items")
    (log/debug "Items:" items)
    (html-response
     [:div
      [:h1 "Items"]
      [:ul
       (for [item items]
         [:li [:a {:href (str "/items/" (:xt/id item))} (:name item)]])]
      [:hr]
      [:h2 "Create Item"]
      (hf/form-to [:post "/items"]
                  (hf/label "name" "Name:")
                  (hf/text-field "name")
                  [:br]
                  (hf/label "description" "Description:")
                  (hf/text-area "description")
                  [:br]
                  (hf/submit-button "Create"))])))

(defn get-item-handler
  "Handles GET requests to retrieve a single item by ID.
  Returns an HTML page with the item details, or a 404 if not found."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)]
    (log/info "Fetching item with ID:" id)
    (if-let [;; item (and id
             ;;           (first (xt/q node
             ;;                        '(from :items [*]
             ;;                               (where (= :xt/id ?id)))
             ;;                        {:args {:id id}})))
             item (and id
                       (first (xt/q node
                                    (sql/format {:select [:*] :from :items
                                                 :where [:= :xt/id id]}))))]
      (do
        (log/debug "Found item:" item)
        (html-response
         [:div
          [:h1 "Item Details"]
          [:p "ID: " (str (:xt/id item))]
          [:p "Name: " (:name item)]
          [:p "Description: " (:description item)]
          [:form {:method "post" :action (str "/items/" id "/delete")}
           [:input {:type "submit" :value "Delete"}]]
          [:hr]
          [:h2 "Update Item"]
          (hf/form-to [:put (str "/items/" id)]
                      (hf/label "name" "Name:")
                      (hf/text-field {:value (:name item)} "name")
                      [:br]
                      (hf/label "description" "Description:")
                      (hf/text-area "description" (:description item))
                      [:br]
                      (hf/submit-button "Update"))]))
      (do
        (log/warn "Item not found:" id)
        {:status 404 :body "Item not found"}))))

(defn update-item-handler
  "Handles PUT requests to update an existing item.
  Expects a JSON body with :name and :description.
  Returns a 404 if the item is not found."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        _ (log/info "Updating item with ID:" id)
        updated-item (-> req :body)
        _ (log/info "Update data:" updated-item)
        ;; existing-item (and id
        ;;                    (first (xt/q node
        ;;                                 '(from :items [*]
        ;;                                        (where (= :xt/id ?id)))
        ;;                                 {:args {:id id}})))
        existing-item (and id
                           (first (xt/q node
                                        (sql/format {:select [:*] :from :items
                                                     :where [:= :xt/id id]}))))]
    (if existing-item
      (do
        (log/info "Found existing item:" existing-item)
        (let [merged-item (merge existing-item updated-item {:xt/id id})
              _ (log/info "Submitting transaction to update item:" merged-item)
              _ (log/info "Transaction: [:put-docs {:into :items} merged-item]")
              ;; tx-result (xt/submit-tx node [[:put-docs {:into :items} merged-item]])
              tx-result (xt/submit-tx node [(clj-str/join " "
                                                          (sql/format {:patch-into :items
                                                                       :values [merged-item]}))])]
          (log/info "Transaction result:" tx-result))
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string (merge existing-item updated-item {:xt/id id}))})
      (do
        (log/warn "Item not found for update:" id)
        {:status 404 :body "Item not found"}))))

(defn delete-item-handler
  "Handles DELETE requests to delete an item by ID.
    Returns 204 No Content on success, or 404 if not found."
  [req]
  (let [node @config/xtdb-node
        id (-> req :path-params :id parse-uuid-str)
        _ (log/info "Attempting to delete item with ID:" id)
        ;; existing-item (and id
        ;;                    (first (xt/q node
        ;;                                 '(from :items [*]
        ;;                                        (where (= :xt/id ?id)))
        ;;                                 {:args {:id id}})))
        existing-item (and id
                           (first (xt/q node
                                        (sql/format {:select [:*] :from :items
                                                     :where [:= :xt/id id]}))))]
    (if existing-item
      (do
        (log/info "Found item to delete:" existing-item)
        (log/info "Submitting transaction to delete item:" id)
        (log/info "Transaction: [:delete-docs {:from :items} id]")
        ;; (let [tx-result (xt/submit-tx node [[:delete-docs {:from :items} id]])]
        ;;   (log/info "Transaction result:" tx-result))
        (let [tx-result (xt/submit-tx node [(clj-str/join " "
                                                          (sql/format {:delete-from :items
                                                                       :where [:= :xt/id id]}))])]
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
      ;; (let [tx-result (xt/submit-tx node [[:delete-docs {:from :items} id]])]
      ;;   (log/info "Transaction result:" tx-result))
      (let [tx-result (xt/submit-tx node [(clj-str/join " "
                                                        (sql/format {:delete-from :items
                                                                     :where [:= :xt/id id]}))])]
        (log/info "Transaction result:" tx-result)))
    {:status 303
     :headers {"Location" "/items"}}))

(def app
  (ring/ring-handler
   (ring/router
    [["/items" {:get {:handler list-items-handler
                      :interceptors interceptor-chain}
                :post {:handler create-item-handler
                       :interceptors interceptor-chain}}]
     ["/items/:id" {:get {:handler get-item-handler
                          :interceptors interceptor-chain}
                    :put {:handler update-item-handler
                          :interceptors interceptor-chain}
                    :delete {:handler delete-item-handler
                             :interceptors interceptor-chain}}]
     ["/items/:id/delete" {:post {:handler delete-item-post-handler
                                  :interceptors interceptor-chain}}]]
    {:data {:middleware [wrap-params
                         wrap-json-response
                         [wrap-json-body {:keywords? true}]
                         [wrap-cors :access-control-allow-origin [#".*"]
                          :access-control-allow-methods [:get :put :post :delete]]]}})
   (ring/routes
    (ring/create-default-handler))))

;; Define server var before defstate
(declare server)

;; Define server as a mount state
(defstate server
  :start (do
           (log/info "Starting web server on port" port)
           (jetty/run-jetty app {:port port :join? false}))
  :stop (do
          (log/info "Stopping web server")
          (.stop server)))

;; Update the init! function to use seed.clj
(defn init! []
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

;; Update main function to initialize mount
(defn -main [& [port-arg]]
  (let [server-port (Integer. (or port-arg (System/getenv "PORT") (str port)))]
    (alter-var-root #'port (constantly server-port))
    (init!)
    (log/info "Application started successfully")))

(comment
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
