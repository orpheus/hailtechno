(ns hailtechno.db
  (:require [hailtechno.util :refer [current-timestamp]]
            [next.jdbc :refer [execute-one!] :as jdbc]
            [next.jdbc.sql :refer [insert! query]]
            [next.jdbc.sql.builder :as sql-builder]
            ))

(def db {:dbtype "postgres"
         :dbname "hailtechno"
         :user "postgres"})

(def ds (jdbc/get-datasource db))

(defn setup []
  (println "Setting up database.")
  (jdbc/execute! ds ["
CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS track(
  id serial primary key,
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  album varchar,
  trackname varchar,
  upload_date timestamp with time zone);

CREATE TABLE IF NOT EXISTS mix(
  id serial primary key,
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  mixname varchar,
  upload_date timestamp with time zone);

CREATE TABLE IF NOT EXISTS image(
  id serial primary key,
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  imgname varchar,
  upload_date timestamp with time zone);

CREATE TABLE IF NOT EXISTS video(
  id serial primary key,
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  vidname varchar,
  upload_date timestamp with time zone);

CREATE TABLE IF NOT EXISTS access_token(
  id uuid primary key DEFAULT uuid_generate_v4(),
  uploads smallint DEFAULT 0,
  max_uploads smallint,
  exp_date timestamp with time zone,
  created_date timestamp with time zone,
  name name unique,
  type text,
  notes varchar,
  email citext);

CREATE TABLE IF NOT EXISTS upload_history(
  email citext primary key,
  access_code uuid,
  upload_time timestamp with time zone,
  type varchar,
  file_id integer);

CREATE TABLE IF NOT EXISTS user_account(
  email citext primary key,
  created_date timestamp with time zone);
"])
  (println "Database setup."))

(defn filter-map-vals
  "Filters out unspecifed key-values on a map.
  Returns a map with only the keys described in `keys` and their values.
  `obj` is a clojure map and `keys` is a clojure set."
  [obj keys]
  (into {} (filter (fn [key-val] (keys (first key-val))) obj)))

;; Access Tokens
(defn create-access-token [obj]
  (insert! ds :access_token
           (assoc obj :created_date (current-timestamp))))

(defn increment-token-upload-count [access-token-id]
  (execute-one! ds ["update access_token set uploads = uploads + 1;"]))

;; Save
(defn save-upload-history [obj]
  (insert! ds :upload_history obj))

(defn save-track [obj access-token]
  (insert! ds :track (assoc obj :upload_date (current-timestamp))))

(defn save-mix [obj]
  (insert! ds :mix (assoc obj :upload_date (current-timestamp))))

(defn save-image [obj]
  (insert! ds :image (assoc obj :upload_date (current-timestamp))))

(defn save-video [obj]
  (insert! ds :video (assoc obj :upload_date (current-timestamp))))

(defn get-track-data []
  (query ds ["select * from track;"]))

(defn get-mix-data []
  (query ds ["select * from mix;"]))

(defn get-user-data []
  (query ds ["select * from user_account;"]))

(defn get-image-data []
  (query ds ["select * from image;"]))

(defn get-video-data []
  (query ds ["select * from video;"]))

(defn get-upload-history []
  (query ds ["select * from upload_history;"]))

(defn get-access-tokens []
  (query ds ["select * from access_token;"]))

(defn get-track-by-id
  ([id] (get-track-by-id id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from tracks where id = (?)::uuid" id] opts)))

(defn get-mix-by-id
  ([id] (get-mix-by-id id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from tracks where id = (?)::uuid" id] opts)))

(defn get-video-by-id
  ([id] (get-video-by-id id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from tracks where id = (?)::uuid" id] opts)))

(defn get-image-by-id
  ([id] (get-image-by-id id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from images where id = (?)::uuid" id] opts)))

(defn get-access-token-by-id
  ([id] (get-access-token-by-id id {}))
  ([id options]
   (jdbc/execute-one! ds
                      ["select * from access_token where id = (?)::uuid" id]
                      options)))

(defn get-access-token-by-name
  ([name] (get-access-token-by-name name {}))
  ([name opts]
   (jdbc/execute-one! ds
                      ["select * from access_token where name = ?" name]
                      opts)))
