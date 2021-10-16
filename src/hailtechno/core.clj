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

(defroutes main-routes
  (GET "/" [] (str "What's up stunna"))
  (upload-route))


(def app
  (do
    (db-setup)
    (-> (handler/site main-routes))))
