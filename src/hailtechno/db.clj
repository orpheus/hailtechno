(ns hailtechno.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :as sql-builder]))

(def db {:dbtype "postgres"
         :dbname "hailtechno"
         :user "postgres"})

(def ds (jdbc/get-datasource db))

(defn setup []
  (println "Setting up database.")
  (jdbc/execute! ds ["
CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";

CREATE TABLE IF NOT EXISTS tracks(
  id uuid primary key DEFAULT uuid_generate_v4(),
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  album varchar,
  trackname varchar);

CREATE TABLE IF NOT EXISTS mixes(
  id uuid primary key DEFAULT uuid_generate_v4(),
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  mixname varchar);

CREATE TABLE IF NOT EXISTS images(
  id uuid primary key DEFAULT uuid_generate_v4(),
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  imgname varchar);

CREATE TABLE IF NOT EXISTS video(
  id uuid primary key DEFAULT uuid_generate_v4(),
  filepath varchar unique not null,
  filename varchar,
  artist varchar,
  vidname varchar);
"])
  (println "Database setup."))


(defn filter-map-vals
  "Filters out unspecifed key-values on a map.
  Returns a map with only the keys described in `keys` and their values.
  `obj` is a clojure map and `keys` is a clojure set.
  "
  [obj keys]
  (into {} (filter (fn [key-val] (keys (first key-val))) obj)))

(def sql-track-cols #{:id :filepath :filename :artist :album :trackname})
(defn save-track [track]
  (jdbc/execute-one! ds
                     (sql-builder/for-insert
                      "tracks"
                      (filter-map-vals track sql-track-cols)
                      {})))

(defn get-track
  ([id] (get-track id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from tracks where id = (?)::uuid" id] opts)))

(defn get-image
  ([id] (get-image id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from images where id = (?)::uuid" id] opts)))

(def sql-mix-cols #{:id :filepath :filename :artist :mixname})
(defn save-mix [mix]
  (jdbc/execute-one! ds
                     (sql-builder/for-insert
                      "mixes"
                      (filter-map-vals mix sql-mix-cols)
                      {})))

(def sql-img-cols #{:id :filepath :filename :artist :imgname})
(defn save-image [img]
  (jdbc/execute-one! ds
                     (sql-builder/for-insert
                      "images"
                      (filter-map-vals img sql-img-cols)
                      {})))

(def sql-vid-cols #{:id :filepath :filename :artist :vidname})
(defn save-video [video]
  (jdbc/execute-one! ds
                     (sql-builder/for-insert
                      "images"
                      (filter-map-vals video sql-vid-cols)
                      {})))
