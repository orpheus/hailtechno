(ns hailtechno.db
  (:require [clojure.tools.logging :as log]
            [hailtechno.util :refer [current-timestamp]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :refer [insert! query]]
            [next.jdbc.sql.builder :as sql-builder]))

(def db-config {:dbtype "postgres"
                :dbname "hailtechno"
                :user "postgres"})

(def ds (jdbc/get-datasource db-config))
(def conn (jdbc/get-connection ds))

;; Helpers
(def opts {:builder-fn rs/as-unqualified-lower-maps})

(defn execute-one!
  [sql]
  (log/debugf "Executing JDBC [%s]." sql)
  (try
    (let [results (jdbc/execute-one! ds sql opts)]
      (when (not= nil results)
        (log/debugf "JDBC Results [%s]." results)
        results))
    (catch Exception e
      (throw e))))

(defn execute!
  [sql]
  (log/debugf "Executing JDBC [%s]." sql)
  (try
    (let [results (jdbc/execute! ds sql opts)]
      (when (not= nil results)
        (log/debugf "JDBC Results [%s]." results)
        results))
    (catch Exception e (throw e))))

;; Access Tokens
(defn create-access-token [obj]
  (insert! ds :access_token
           (assoc obj :date_created (current-timestamp))
           opts))

(defn get-access-token-by-id
  ([id] (get-access-token-by-id id opts))
  ([id options]
   (execute-one! ["select * from access_token where id = (?)::uuid" id])))

(defn get-access-token-by-name
  ([name] (get-access-token-by-name name opts))
  ([name options]
   (execute-one! ["select * from access_token where name = ?" name])))

;; todo: change table name to _data and max_upload_count to max_uploads?
(defn get-access-token-data [access-code]
  (execute-one! ["
select * from vw_access_token_usage where access_code = (?)::uuid" access-code]))

(defn get-access-tokens []
  (execute! ["select * from access_token"]))
;; File Uploads

(defn save-file-upload [file-upload]
  (insert! ds :file_upload
           (assoc file-upload
                  :date_uploaded (current-timestamp))
           opts))

(defn get-file-by-id [id]
  (execute-one! ["select * from file_upload where id = ?" id]))

(defn get-tracks []
  (execute! ["select * from file_upload where file_type_id = 0"]))

(defn get-mixes []
  (execute! ["select * from file_upload where file_type_id = 1"]))

(defn get-images []
  (execute! ["select * from file_upload where file_type_id = 2"]))

(defn get-video []
  (execute! ["select * from file_upload where file_type_id = 3"]))
