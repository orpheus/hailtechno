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

(defn save-file-to-fs
  "Saves a File to a filesystem path"
  [File destination]
  (let [dst (str "tracks/" destination)]
    (datoteka/create-dir (datoteka/parent dst))
    (io/copy File
             (io/file dst))))

(defn create-file-path [params]
  (clojure.string/join "/" (remove clojure.string/blank? params)))

(defn path-interpolation
  "
  Simple string interpolation for creating dynamic filepaths.

  toDo: check for `?` option fields and require non-optionals.
  "
  [path fields params]
  (loop [[field & rest] fields
         _path path]
    (let [regx (re-pattern (str "\\{" field "\\}"))
          value (params (keyword field) "")]
      (if field
        (recur rest (clojure.string/replace _path regx value))
        ;; for now remove double slashes if a field wasn't found
        ;; and set to all lowercase
        (-> _path
            (clojure.string/replace #"//" "/")
            clojure.string/lower-case)
        ))))

(defn strip-underscore-prefix [str]
  (clojure.string/replace str #"^_" ""))

(defn wo-undescore-prefix [vec]
  (map (partial strip-underscore-prefix) vec))

(defn store-files [params config]
  (let [filepath (path-interpolation (:filepath config)
                                     (wo-undescore-prefix (:metadata config))
                                     params)]
    [{:filepath filepath} nil]))

(defn validate-req-params
  "
  `params`::Map
  `config`::Map => {:metadata Vector<String>}

  Checks the `params` map for required fields as found in the `:metadata`
  vector of the `config` map denoted by strings that start with `_`, an underscore.
  "
  [params config]
  (loop [[param & rest] (cons "_file" (:metadata config))]
    (if (and param ;; not nil
             (str/starts-with? param "_") ;; starts with `_`
             (not (params (keyword (subs param 1))))) ;; and is not in the req body
      (str "Missing required request body parameter: " (subs param 1))
      ;; else recur
      (if rest (recur rest)))))

;; Returns: Vector<Filemap, Error>
(defn validate-and-store [req-params config]
  (if-let [error (validate-req-params req-params config)]
    [nil error]
    (try (store-files req-params config)
         (catch Exception e [nil (str "Unknown exception in storing file(s)"
                                      (.getMessage e))]))))

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
  callback: Fn (fn [Map {:keys [trackname artistname filemap]}]) ()

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
         (let [[filemap error] (validate-and-store (:params request) config)]
           (println "filemap: " filemap)
           (if error
             (bad-request error)
             (callback (agg-metadata (:paramms request) filemap) request)))
         )))

(defn track-route []
  (file-upload-route
   "/track"
   {:accepts ["audio/mpeg" "audio/vnd.wav" "audio/mp4"]
    :filepath "track/{artist}/{album}"
    :metadata ["_name" "_artist" "album"]}
   (fn [{:keys [name artist album filepath]} request]
     ;; (println name artist album filepath)
     (response "OK"))))

(defroutes main-routes
  (GET "/" [] (str "What's up stunna"))
  (track-route))


(def app
  (do
    (db-setup)
    (-> (handler/site main-routes))))
