(ns my-app.config
  (:require [mount.core :as mount]
            [xtdb.api :as xt]
            [clojure.tools.logging :as log]))

(def ^:dynamic *config* nil)

(defn dev-config []
  {:xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.mem-kv/->kv-store}}
   :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.mem-kv/->kv-store}}
   :xtdb/index-store {:kv-store {:xtdb/module 'xtdb.mem-kv/->kv-store}}})

(defn prod-config []
  {:xtdb/tx-log {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                            :db-dir "data/tx-log"}}
   :xtdb/document-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                  :db-dir "data/doc-store"}}
   :xtdb/index-store {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                 :db-dir "data/index-store"}}})
(defn get-config []
    (or *config*
      (if (= (System/getenv "APP_ENV") "production")
        (prod-config)
        (dev-config))))

(mount/defstate xtdb-node
  :start (let [config (get-config)]
           (log/info "Starting XTDB node with config:" config)
           (xt/start-node config))
  :stop (.close xtdb-node))