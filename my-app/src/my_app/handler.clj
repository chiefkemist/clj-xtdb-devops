(ns my-app.handler
  (:require [xtdb.api :as xt]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [mount.core :as mount]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [hiccup.core :as hiccup]
            [hiccup.form :as hf]
            [my-app.config :as config]
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
            (log/error "Error:" error)
            (let [status (if (instance? Exception error)
                           (cond
                             (and (.getCause error) (instance? java.io.IOException (.getCause error)))
                             400  ; Bad Request for IOExceptions during body parsing
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
  (let [item (-> req :body)
        valid-item (-> item
                       (assoc :xt/id (java.util.UUID/randomUUID))
                       (select-keys [:xt/id :name :description]))]
    (xt/submit-tx (config/xtdb-node) [[:put valid-item]])
    {:status 201 ; Created
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string valid-item)}))

(defn list-items-handler
  "Handles GET requests to list all items.
  Returns an HTML page with a list of items and a form to create a new item."
  [_req]
  (let [items (xt/q (xt/db (config/xtdb-node))
                    '{:find [(pull item [*])]
                      :where [[item :xt/id]]})]
    (html-response
     [:div
      [:h1 "Items"]
      [:ul
       (for [item items]
         [:li [:a {:href (str "/items/" (-> item first :xt/id))} (-> item first :name)]])]
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
  (let [id (-> req :path-params :id parse-uuid-str)]
    (if-let [item (and id (xt/entity (xt/db (config/xtdb-node)) id))]
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
                    (hf/submit-button "Update"))])
      {:status 404 :body "Item not found"})))

(defn update-item-handler
  "Handles PUT requests to update an existing item.
  Expects a JSON body with :name and :description.
  Returns a 404 if the item is not found."
  [req]
  (let [id (-> req :path-params :id parse-uuid-str)
        updated-item (-> req :body)
        existing-item (and id (xt/entity (xt/db (config/xtdb-node)) id))]
    (if existing-item
      (do
        (xt/submit-tx (config/xtdb-node) [[:put (merge existing-item updated-item {:xt/id id})]])
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string (merge existing-item updated-item {:xt/id id}))})
      {:status 404 :body "Item not found"})))

(defn delete-item-handler
  "Handles DELETE requests to delete an item by ID.
    Returns 204 No Content on success, or 404 if not found."
  [req]
  (let [id (-> req :path-params :id parse-uuid-str)
        existing-item (and id (xt/entity (xt/db (config/xtdb-node)) id))]
    (if existing-item
      (do
        (xt/submit-tx (config/xtdb-node) [[:delete id]])
        {:status 204 :body nil}) ; No Content
      {:status 404 :body "Item not found"})))

(defn delete-item-post-handler
  "Handles POST requests to /items/:id/delete (used for form submission).
  Redirects to /items after deletion."
  [req]
  (let [id (-> req :path-params :id parse-uuid-str)]
    (when id
      (xt/submit-tx (config/xtdb-node) [[:delete id]]))
    {:status 303 ; See Other
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

(mount/defstate server
  :start (jetty/run-jetty app {:port port :join? false})
  :stop (.stop server))

(defn -main [& [port-arg]]
  (let [server-port (Integer. (or port-arg (System/getenv "PORT") (str port)))]
    (alter-var-root #'my-app.handler/port (constantly server-port))
    (mount/stop)
    (mount/start)))
