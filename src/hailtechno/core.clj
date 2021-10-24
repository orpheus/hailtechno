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
            [ring.util.response :refer [response bad-request response?]]
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
  [File filepath filename]
  (datoteka/create-dir filepath)
  (io/copy File (io/file (str filepath "/" filename))))

(defn field-to-re
  "
  Make a regex patter out of given field surrounded by brackets.
  Used for string interpolation.
  "
  [field]
  (re-pattern (str "\\{" field "\\}")))

(defn interpolate-string
  "
  Interpolate string using brackets `{someVar}`.
  example:

  (interpolate-string \"/tracks/{artist}/{album}\"
                      {:artist porter, :album nurture)

  => \"/tracks/porter/nurture\"

  If value in map is nill, will substitute with empty string.
  "
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

(defn interpolate-path
  "
  String interpolation with double slash removal set to lowercase.
  "
  [path params]
  (-> (interpolate-string path params)
      (clojure.string/replace #"//" "/")
      clojure.string/lower-case
      .trim
      (.replaceAll "\\s+" "_")))

(defn strip-underscore-prefix [str]
  (clojure.string/replace str #"^_" ""))

(defn store-file [params config]
  (let [filepath (interpolate-path (:filepath config) params)
        filename (get-in params [:file :filename])
        File     (get-in params [:file :tempfile])]
    (save-file-to-fs File filepath filename)
    [{:filepath filepath, :filename filename :file File} nil]))

;; toDo: validate content-type
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
    (try (store-file req-params config)
         (catch Exception e [nil (str "Unknown exception in storing file(s): "
                                      (.getMessage e))]))))

(defn agg-metadata
  "
  Aggregate data for return value/as argument to the file handler callback.
  Merges the filemap with the param values as defined in the `:metedata`
  vector of the config map.
  "
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
           (if error
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

(defn upload-track-route []
  (file-upload-route
   (apiroot "/track")
   {:accepts ["audio/mpeg" "audio/vnd.wav" "audio/mp4"]
    :filepath trackpath
    :metadata ["_artist" "album" "_trackname"]}
   (fn [{:keys [artist trackname filename]} request]
     (println artist filename trackname)
     (response "OK"))))

(defn get-track-by-path
  "
  WIP: Respond with a track given a set of parameters in the query-string.
  toDo: validate request params and if file exists.
    - can use/steal fns from here as reference
  https://github.com/ring-clojure/ring/blob/master/ring-core/src/ring/util/response.clj
  "
  []
  (GET (apiroot "/track") [artist album trackname]
       (let [path (str (interpolate-path trackpath
                                         {:artist artist :album album})
                       trackname)]
         (println path)
         (response (io/input-stream (io/file path))))))

(defn get-track-by-id []
  (GET (apiroot "/track/:id") [id]
       (println id)
       (response "OK")))

(defn upload-mix-route []
  (file-upload-route
   (apiroot "/mix")
   {:accepts ["audio/mpeg" "audio/vnd.wav" "audio/mp4"]
    :filepath (fsroot "/mixes/{artist}")
    :metadata ["_artist" "_mixname"]}
   (fn [{:keys [artist mixname filename filepath]} request]
     (response "OK"))))

(defn upload-image-route []
  (file-upload-route
   (apiroot "/image")
   {:accepts ["image/png" "image/jpeg" "image/jpg"]
    :filepath (fsroot "/images/{artist}")
    :metadata ["_artist" "_imgname"]}
   (fn [{:keys [artist imgname filename filepath]} request]
     (response "OK"))))

(defn upload-video-route []
  (file-upload-route
   (apiroot "/video")
   {:accepts ["video/mp4"]
    :filepath (fsroot "/video/{artist}")
    :metadata ["_artist" "_videoname"]}
   (fn [{:keys [artist videoname filename filepath]} request]
     (response "OK"))))

(defroutes main-routes
  (upload-track-route)
  (get-track-by-id)
  (get-track-by-path)
  (upload-mix-route)
  (upload-image-route)
  (upload-video-route))


(def app
  (do
    (db-setup)
    (-> (handler/site main-routes))))
