(ns my-app.handler-test
 (:require [clojure.test :refer :all]
           [my-app.handler :refer :all]
           [ring.mock.request :as mock]
           [cheshire.core :as json]
           [xtdb.api :as xt]
           [my-app.config :as config]))


(defn- test-xtdb-fixture [f]
 (mount/start #'config/xtdb-node)
 (f)
 (mount/stop #'config/xtdb-node))

(use-fixtures :once test-xtdb-fixture)

(deftest test-create-item-handler
  (testing "Create item"
    (let [request (-> (mock/request :post "/items")
                      (mock/json-body {:name "Test Item" :description "A test item"}))
          response (app request)
          body (json/parse-string (:body response) true)]
      (is (= 201 (:status response)))
      (is (contains? body :xt/id))
      (is (= "Test Item" (:name body)))
      (is (= "A test item" (:description body))))))

(deftest test-create-item-handler-missing-fields
 (testing "Create item with missing fields"
   (let [request (-> (mock/request :post "/items")
                     (mock/json-body {:name "Test Item"}))  ; Missing description
         response (app request)]
     (is (= 201 (:status response))))
   (let [request (-> (mock/request :post "/items")
                     (mock/json-body {:description "Test Item"}))  ; Missing name
         response (app request)]
     (is (= 201 (:status response))))))

(deftest test-list-items-handler
 (testing "List items"
   (let [request (mock/request :get "/items")
         response (app request)]
     (is (= 200 (:status response)))
     (is (= "text/html" (get-in response [:headers "Content-Type"])))
     ;; Ideally, we'd parse the HTML and check the content, but for now,
     ;; we'll just check that the body is not empty.
     (is (not (empty? (:body get-resp)))))))

(deftest test-get-item-handler
 (testing "Get item"
   (let [; Create an item first
         create-req (-> (mock/request :post "/items")
                        (mock/json-body {:name "Test Item Get" :description "For get test"}))
         create-resp (app create-req)
         created-item (json/parse-string (:body create-resp) true)
         item-id (str (:xt/id created-item))

         ; Now retrieve it
         get-req (mock/request :get (str "/items/" item-id))
         get-resp (app get-req)]
     (is (= 200 (:status get-resp)))
     (is (= "text/html" (get-in get-resp [:headers "Content-Type"])))
     (is (not (empty? (:body get-resp)))))))

(deftest test-get-item-handler-invalid-uuid
 (testing "Get item - Invalid UUID"
   (let [get-req (mock/request :get "/items/not-a-uuid")
         get-resp (app get-req)]
     (is (= 404 (:status get-resp))))))

(deftest test-get-item-handler-not-found
 (testing "Get item - Not Found"
   (let [get-req (mock/request :get (str "/items/" (java.util.UUID/randomUUID)))
         get-resp (app get-req)]
     (is (= 404 (:status get-resp))))))

(deftest test-update-item-handler
 (testing "Update item"
   (let [; Create an item
         create-req (-> (mock/request :post "/items")
                        (mock/json-body {:name "Test Item Update" :description "For update test"}))
         create-resp (app create-req)
         created-item (json/parse-string (:body create-resp) true)
         item-id (str (:xt/id created-item))

         ; Update it
         update-req (-> (mock/request :put (str "/items/" item-id))
                        (mock/json-body {:name "Updated Name" :description "Updated description"}))
         update-resp (app update-req)
         updated-item (json/parse-string (:body update-resp) true)]

     (is (= 200 (:status update-resp)))
     (is (= "Updated Name" (:name updated-item)))
     (is (= "Updated description" (:description updated-item))))))

(deftest test-update-item-handler-invalid-uuid
 (testing "Update item - Invalid UUID"
   (let [update-req (-> (mock/request :put "/items/not-a-uuid")
                        (mock/json-body {:name "Updated Name" :description "Updated description"}))
         update-resp (app update-req)]
     (is (= 404 (:status update-resp))))))

(deftest test-update-item-handler-not-found
 (testing "Update item - Not Found"
   (let [update-req (-> (mock/request :put (str "/items/" (java.util.UUID/randomUUID)))
                        (mock/json-body {:name "Updated Name" :description "Updated description"}))
         update-resp (app update-req)]
     (is (= 404 (:status update-resp))))))

(deftest test-update-item-handler-empty-fields
  (testing "Update item with empty fields"
    (let [; Create an item
          create-req (-> (mock/request :post "/items")
                         (mock/json-body {:name "Test Item Update" :description "For update test"}))
          create-resp (app create-req)
          created-item (json/parse-string (:body create-resp) true)
          item-id (str (:xt/id created-item))

          ; Update with empty name
          update-req (-> (mock/request :put (str "/items/" item-id))
                         (mock/json-body {:name "" :description "Updated description"}))
          update-resp (app update-req)
          updated-item (json/parse-string (:body update-resp) true)]
      (is (= "" (:name updated-item))))))

(deftest test-delete-item-handler
 (testing "Delete item"
   (let [; Create an item
         create-req (-> (mock/request :post "/items")
                        (mock/json-body {:name "Test Item Delete" :description "For delete test"}))
         create-resp (app create-req)
         created-item (json/parse-string (:body create-resp) true)
         item-id (str (:xt/id created-item))

         ; Delete it
         delete-req (mock/request :delete (str "/items/" item-id))
         delete-resp (app delete-req)]

     (is (= 204 (:status delete-resp))))))

(deftest test-delete-item-handler-invalid-uuid
  (testing "Delete item - Invalid UUID"
    (let [delete-req (mock/request :delete "/items/not-a-uuid")
          delete-resp (app delete-req)]
      (is (= 404 (:status delete-resp))))))

(deftest test-delete-item-handler-not-found
 (testing "Delete item - Not Found"
   (let [delete-req (mock/request :delete (str "/items/" (java.util.UUID/randomUUID)))
         delete-resp (app delete-req)]
     (is (= 404 (:status delete-resp))))))

(deftest test-submit-tx-handler-bad-request
 (testing "Submit transaction with bad request"
   (let [request (mock/request :post "/tx")
         request (assoc request :body "invalid json") ; Provide invalid JSON
         response (submit-tx-handler request)]
     (is (= 400 (:status response))))))