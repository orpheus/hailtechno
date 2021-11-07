(ns hailtechno.db
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [hailtechno.util :refer [current-timestamp]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :refer [insert! query]]
            [next.jdbc.sql.builder :as sql-builder]))

(println "db-name" (env :db-name))
(println "db-host" (env :db-host))

(def db-config {:dbtype "postgres"
                :user "postgres"
                :dbname (env :db-name)
                :host (env :db-host)
                :port 5432})

(def ds (jdbc/get-datasource db-config))
(def conn
  ;(jdbc/get-connection ds)
  )

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

;; access_token

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

;; file_upload

(defn save-file-upload [file-upload]
  (insert! ds :file_upload
           (assoc file-upload
                  :date_uploaded (current-timestamp))
           opts))

(defn get-file-by-id [id]
  (execute-one! ["select * from file_upload where id = (?)::int" id]))

(defn get-tracks []
  (execute! ["select * from file_upload where file_type_id = 0"]))

(defn get-mixes []
  (execute! ["select * from file_upload where file_type_id = 1"]))

(defn get-images []
  (execute! ["select * from file_upload where file_type_id = 2"]))

(defn get-video []
  (execute! ["select * from file_upload where file_type_id = 3"]))

;; user_account

(defn save-user [email]
  (insert! ds :user_account {:email email :date_created (current-timestamp)} opts))

(defn get-user [email]
  (execute-one! ["select * from user_account where email = ?" email]))

(defn force-get-or-save-user [email]
  (if-not (str/blank? email)
    (if-let [user (try (get-user (str email)) (catch Exception e nil))]
      user
      (try (save-user (str email))
           (catch Exception e nil)))))

(defn get-users []
  (execute! ["select * from user_account"]))
