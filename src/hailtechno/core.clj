(ns hailtechno.core
  (:gen-class)
  (:use [compojure.core])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datoteka.core :as datoteka]
            [next.jdbc :as jdbc]
            [next.jdbc.sql.builder :as sql-builder]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response bad-request response?]]
            )
  (:import (java.io File)))

(def db {:dbtype "postgres"
         :dbname "hailtechno"
         :user "postgres"})

(def ds (jdbc/get-datasource db))

(defn db-setup []
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

(defn db-get-track
  ([id] (db-get-track id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from tracks where id = (?)::uuid" id] opts)))

(defn db-get-image
  ([id] (db-get-image id {}))
  ([id opts]
   (jdbc/execute-one! ds ["select * from images where id = (?)::uuid" id] opts)))

;; (defn db-get-by-id
;;   ([table id] (db-get-by-id table id nil))
;;   ([table id opts]
;;    (jdbc/execute-one! ds ["select * from ? where id = (?)::uuid" (quoted/postgres table) id] opts)))

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

(defn save-file-to-fs
  "Saves a File to a filesystem path and creates the intermediate directories."
  [^File File ^String filepath]
  (let [file-to-create (File. filepath)]
    (if (.exists file-to-create)
      (str filepath " already exists.")
      (let [parent-file (-> file-to-create .getParent File.)]
        (if-not (.isDirectory parent-file)
          (.mkdirs parent-file))
        (io/copy File file-to-create)))))

(defn field-to-re
  "Make a regex patter out of given field surrounded by brackets.
  Used for string interpolation."
  [field]
  (re-pattern (str "\\{" field "\\}")))

(defn ^String interpolate-string
  "Interpolate string using brackets `{someVar}`.

  example:
  => (interpolate-string \"/tracks/{artist}/{album}\"
                         {:artist porter, :album nurture)
  => \"/tracks/porter/nurture\"

  If value in map is nill, will substitute with empty string."
  [string values]
  (let [matcher (re-matcher #"\{(.*?)\}" string)]
    (loop [match (re-find matcher)
           re-string string]
      (if-not match
        re-string
        (recur (re-find matcher)
               (clojure.string/replace re-string
                                       (field-to-re (second match))
                                       (-> match
                                           second
                                           keyword
                                           values
                                           (or ""))))))))

(defn ^String interpolate-path
  "String interpolation with double slash removal set to lowercase."
  [path params]
  (-> (interpolate-string path params)
      (clojure.string/replace #"//" "/")
      clojure.string/lower-case
      .trim
      (.replaceAll "\\s+" "_")))

(defn strip-underscore-prefix [str]
  (clojure.string/replace str #"^_" ""))

(defn store-file [params config]
  (let [dirpath (interpolate-path (:filepath config) params)
        filename (get-in params [:file :filename])
        ;; toDo: clean up
        filepath (str/replace (str dirpath (File/separator) filename) #"//" "/")
        tempfile     (get-in params [:file :tempfile])]
    (let [error (save-file-to-fs tempfile filepath)]
      [{:filepath filepath, :filename filename :file tempfile} error])))

(defn validate-req-params
  " `params`::Map | `config`::Map => {:metadata Vector[String]}

  Checks the `params` map for required fields as found in the `:metadata`
  vector of the `config` map denoted by strings that start with `_`, an underscore."
  [params config]
  (loop [[param & rest] (cons "_file" (:metadata config))]
    (if (and param ;; not nil
             (str/starts-with? param "_") ;; starts with `_`
             (not (params (keyword (subs param 1))))) ;; and is not in the req body
      (str "Missing required request body parameter: " (subs param 1))
      ;; else recur
      (if rest (recur rest))))
  (let [{{content-type :content-type} :file} params
        accepts (:accepts config)]
    (if-not (set? accepts)
      "Interval server error: file-upload-route config.accepts must be a set."
      (if-not (accepts content-type)
        (str "File content-type must be one of: " (str/join ", " accepts))))))

;; Returns: Vector<Filemap, Error>
(defn validate-and-store [req-params config]
  (if-let [error (validate-req-params req-params config)]
    [nil error]
    (try (store-file req-params config)
         (catch Exception e [nil (str "Unknown exception in storing file(s): "
                                      (.getMessage e))]))))

(defn agg-metadata
  "Aggregate data for return value/as argument to the file handler callback.
  Merges the filemap with the param values as defined in the `:metedata`
  vector of the config map."
  [params filemap config]
  (loop [[field & rest] (:metadata config)
         agg filemap]
    (if-not field
      agg
      (let [key (keyword (strip-underscore-prefix field))
            val (key params)]
        (recur rest (if val (assoc agg key val) agg))
        ))))

(defn file-upload-route
  "Creates a configurable file upload route with validation.
  Returns a composure route wrapped in ring's multipart middleware.

  controller: String
  config: Map {
    :accepts Set::String (content-type) #(\"audio/mpeg\" \"audio/vnd.wav\")
    :filepath String \"path/to/save/file\"
    :metadata Vector::String [\"trackname\" \"artistname\"]
  }
  callback: Fn (fn [Map {:keys [trackname artistname filemap]}]) ()

  The `file-upload-route` looks for the `file` key to exist as part of the req
  params. This `file` must have a `content-type` that matches one of
  the values inside the `:accepts` set

  The `:filepath` is the location where the file will be stored.
  Accepts templates such as 'file/{trackname}' where `{trackname}` will
  be substituted for the `trackname` parameter as defined to lookup by the metadata.
  Can also optionally template via `file/{trackname}/{album?}` where if `album`
  is defined in the metadata, it will use it as part of the path.

  The `:metadata` is a list of values to look up in the `params` map of
  the request body to be used as templates in the filepath and as arguments
  for the callback. The `file-upload-route` will only look at the variables
  in the params map that are defined in the `:metadata` field. To mark
  a field as requied, prefix it with an underscore. This will not change
  how thpe callback fn will receive this parameter, e.g. the underscore will
  be stripped after it has verified that value is non-null and non-empty.

  The `callback` parameter is a function that takes a map of metadata from
  the request and vars calculated during execution for the user to use to
  perform side effects like update a databas. It takes as a second argument
  the actual http `request`.
  "
  [controller config callback]
  (wrap-multipart-params
   (POST controller request
         (let [[filemap error] (validate-and-store (:params request) config)]
           (if error
             ;; toDo: Let the fns down low send back a proper request so they
             ;; can provide more accurate status codes.
             (bad-request error)
             (let [res (callback (agg-metadata (:params request) filemap config)
                                 request)]
               (if (response? res)
                 res
                 {:status 500
                  :body "Callback passed to `file-upload-route` returned an invalid ring response map."})))))))

(defn fsroot [path]
  (str "fsroot" path))

(defn apiroot [controller]
  (str "/api" controller))

(def trackpath (fsroot "/tracks/{artist}/{album}"))
(def mixpath   (fsroot "/mixes/{artist}"))
(def imgpath   (fsroot "/images/{artist}"))
(def vidpath   (fsroot "/video/{artist}"))

;; save the actual filepath to the db, not the parent dir
(defn upload-track-route []
  (file-upload-route
   (apiroot "/track")
   {:accepts #{"audio/mpeg" "audio/vnd.wav" "audio/mp4"}
    :filepath trackpath
    :metadata ["_artist" "album" "_trackname"]}
   (fn [track-data request]
     (save-track track-data)
     (response "Uploaded."))))

(defn get-track-by-path
  "Accepts `artist` `album` and `trackname` as query parameters to locate and
  retrieve a file on the file system."
  []
  (GET (apiroot "/track") [artist album trackname]
       (let [path (str (interpolate-path trackpath
                                         {:artist artist :album album})
                       (File/separator)
                       trackname)
             file (File. path)]
         (if (.isDirectory file)
           (bad-request (str "File not found: " path " is a directory."))
           (if (.exists file)
             (response (io/input-stream file))
             (bad-request (str path " does not exist.")))))))

(defn get-track-by-id []
  (GET (apiroot "/track/:id") [id]
       (if-let [track (db-get-track id)]
         (response (io/input-stream
                    (io/file (track :tracks/filepath)))))))

(defn upload-mix-route []
  (file-upload-route
   (apiroot "/mix")
   {:accepts #{"audio/mpeg" "audio/vnd.wav" "audio/mp4"}
    :filepath (fsroot "/mixes/{artist}")
    :metadata ["_artist" "_mixname"]}
   (fn [mix-data _]
     ;; toDo: add rollback if exception
     (save-mix mix-data)
     (response "OK"))))


;; (defn get-mix-by-id []
;;   (GET (apiroot "/mix/:id") [id]
;;        (if-let [record (db-get-by-id "mixes" id)]
;;          (response (io/input-stream
;;                     (io/file (str (record :mixes/filepath) "/" (record :mixes/filename)))))
;;          )))

(defn upload-image-route []
  (file-upload-route
   (apiroot "/image")
   {:accepts #{"image/png" "image/jpeg" "image/jpg"}
    :filepath (fsroot "/images/{artist}")
    :metadata ["_artist" "_imgname"]}
   (fn [img-data _]
     (save-image img-data)
     (response "OK"))))

(defn get-image-by-id []
  (GET (apiroot "/image/:id") [id]
       (if-let [record (db-get-image id)]
         (response (io/input-stream
                    (io/file (str (record :images/filepath) "/" (record :images/filename)))))
         )))

(defn upload-video-route []
  (file-upload-route
   (apiroot "/video")
   {:accepts #{"video/mp4"}
    :filepath (fsroot "/video/{artist}")
    :metadata ["_artist" "_videoname"]}
   (fn [vid-data _]
     (save-video vid-data)
     (response "OK"))))

(defroutes main-routes
  (upload-track-route)
  (get-track-by-id)
  (get-track-by-path)
  (upload-mix-route)
  (upload-image-route)
  (upload-video-route)
  (get-image-by-id)
  (route/not-found "<h1>404</h1>"))

(def app
  (do
    (db-setup)
    (-> (handler/site main-routes))))
