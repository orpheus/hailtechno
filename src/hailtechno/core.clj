(ns hailtechno.core
  (:gen-class)
  (:use [compojure.core])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [datoteka.core :as datoteka]
            [next.jdbc :as jdbc]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response bad-request]]
            ))

(def db {:dbtype "postgres"
         :dbname "hailtechno"
         :user "postgres"})

(def ds (jdbc/get-datasource db))

(defn db-setup []
  (jdbc/execute! ds ["
CREATE TABLE IF NOT EXISTS tracks(
  id varchar primary key,
  name varchar,
  filename varchar,
  artist varchar,
  album varchar
)"]))

(defn save-file-to-fs [File destination]
  "Saves a File to a filesystem path"
  (let [dst (str "tracks/" destination)]
    (datoteka/create-dir (datoteka/parent dst))
    (io/copy File
             (io/file dst))))

(defn create-file-path [params]
  (clojure.string/join "/" (remove clojure.string/blank? params)))

(defn store-file [req-body]
  (let [file-map (req-body :file)
        filename (file-map :filename)
        track-name (req-body :name)
        artist (req-body :artist)
        album (req-body :album)
        fs-dir (create-file-path [artist album])
        fs-path (create-file-path [artist album filename])]
    (save-file-to-fs (file-map :tempfile)
                     fs-path)
    ;; todo: Save filepath and metadata to postgres
    (println "Saved file to filesystem")))

(defn validate-file-upload [file-attr]
  "Checks the request body file attribute for proper schema. Returns nil if OK.
   Or returns string error message."
  (if (vector? file-attr)
    (loop [[file & rest] file-attr]
      (if-not (nil? file)
        (if (vector? file)
          "Expected file attribute to be a vector of maps. Found vector inside vector.\n")
        ;; todo: test file type/content type
        (recur rest)))
    (if-not (map? file-attr)
      "Expected `file` attibute on request body to be a map.\n")))

(defn handle-file-upload [params]
  "Parses request body file attribute to upload files."
  (let [file-attr (params :file)
        valid-res (validate-file-upload file-attr)]
    (println params)
    (if-not (nil? valid-res)
      valid-res
     (if (vector? file-attr)
       (loop [[file & rest] file-attr]
         (if-not file
           (println "Uploaded files.")
           (do
             (store-file params)
             (recur rest))))
       (store-file params)))))

(defn upload-route []
  "Route handler to upload multipart files."
  (wrap-multipart-params
   (POST "/upload" {params :params}
         (println "POST /upload")
         (let [res (handle-file-upload params)]
           (if (nil? res) (response "File(s) uploaded\n")
               (bad-request res)))
         )))

(defn is-valid-req
  "Returns null if valid. Returns error message if not."
  [params config])

(defn store-files [params config]
  {:filepath "some/file/path"})

(defn agg-metadata [params filepaths]
  {})

(defn file-upload-route
  "Creates a configurable file upload route with validation.
  Returns a composure route wrapped in ring's multipart middleware.

  controller: String
  config: Map {
    :accepts Vector::String (content-type) [\"audio/mpeg\" \"audio/vnd.wav\"]
    :filepath String \"path/to/save/file\"
    :metadata Vector::String [\"trackname\" \"artistname\"]
  }
  callback: Fn (fn [Map {:keys [trackname artistname filepath]}]) ()

  The `file-upload-route` looks for the `file` key to exist as part of the req
  params. This `file` must have a `content-type` that matches one of
  the values inside the `:accepts` vector

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

  The  `callback` parameter is a function that takes a map of metadata from
  the request and vars calculated during execution for the user to use to
  perform side effects like update a databas. It takes as a second argument
  the actual http `request`.
  "
  [controller config callback]
  (wrap-multipart-params
   (POST controller request
         (println request)
         ;; Validate request body - returns null or error message
         (let [error (is-valid-req (:params request) config)]
           (if-not (nil? error)
             (bad-request error)
             ;; If no error, store file
             (let [[filepaths error] (store-files (:params request) config)]
               (if-not (nil? error)
                 (bad-request error)
                 ;; If no error, call callback with metadata
                 (callback (agg-metadata (:params request) filepaths) request)))))
         (response "OK"))))

(defn track-route []
  (file-upload-route
   "/track"
   {:accepts ["audio/mpeg" "audio/vnd.wav" "audio/mp4"]
    :filepath "track/{artist}/{album?}"
    :metadata ["_name" "_artist" "album"]}
   (fn [{:keys [name artist album filepath]}]
     (println name artist album filepath))))

(defroutes main-routes
  (GET "/" [] (str "What's up stunna"))
  (upload-route)
  (track-route))


(def app
  (do
    (db-setup)
    (-> (handler/site main-routes))))
