(ns my-app.config
  (:require [mount.core :refer [defstate]]
            [xtdb.api :as xt]
            [xtdb.client :as xtc]
            [clojure.tools.logging :as log]))

(def ^:dynamic *config* nil)

(declare xtdb-node)

(defn get-xtdb-url []
  (let [host (or (System/getenv "XTDB_HOST") "localhost")
        port "3000"] ; XTDB 2 HTTP API port is 3000
    (format "http://%s:%s" host port)))

(defn wait-for-xtdb [node url retries]
  (let [attempts (range retries)]
    (doseq [attempt attempts]
      (try
        (xt/status node)
        (log/info "XTDB is ready!")
        (log/info "Connection successful after" (inc attempt) "attempts")
        ;; Successfully connected, just return the node
        (throw (ex-info "success" {:node node}))
        (catch Exception e
          (if (= "success" (.getMessage e))
            (throw e)  ; Re-throw our success signal
            (let [attempts-left (- retries (inc attempt))]
              (when (zero? attempts-left)
                (throw (ex-info "Failed to connect to XTDB" {:url url})))
              (log/info "Waiting for XTDB to be ready... retries left:" attempts-left)
              (Thread/sleep 1000)))))))
  ;; If we get here without a success exception, throw error
  (throw (ex-info "Failed to connect to XTDB" {:url url})))

(defstate ^:dynamic xtdb-node
  :start
  (let [url (get-xtdb-url)]
    (log/info "Starting XTDB client, connecting to" url)
    (try
      (let [node (xtc/start-client url)]
        (log/info "XTDB client started successfully")
        (log/info "Waiting for XTDB to be ready...")
        (try
          (let [ready-node (wait-for-xtdb node url 30)]
            (atom ready-node))  ;; Wrap the node in an atom
          (catch clojure.lang.ExceptionInfo e
            (if (= "success" (.getMessage e))
              (atom (:node (ex-data e)))  ;; Wrap the node in an atom
              (throw e)))))
      (catch Exception e
        (log/error "Failed to start XTDB client:" e)
        (throw e))))

  :stop
  (do
    (log/info "Stopping XTDB client")
    (try
      (.close @xtdb-node)  ;; Deref the atom here
      (log/info "XTDB client stopped successfully")
      (catch Exception e
        (log/error "Error stopping XTDB client:" e)
        (throw e)))))

(comment
  (require 'my-app.config)
  (in-ns 'my-app.config)
  (require '[mount.core :as mount])
  (mount/start)
  (mount/stop)
  ;;
  )
