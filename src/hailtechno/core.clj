(ns hailtechno.core
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response bad-request]]
            ))

(def db {:dbtype "postgres"
         :dbname "hailtechno"
         :user "postgres"})

(def ds (jdbc/get-datasource db))


(defn copy-file-local [file-map]
  "Takes a file map from a http request body and copys the files locally."
  (let [file (get file-map :tempfile)
        filename (get file-map :filename)]
    (io/copy (io/file file)
             (io/file (str "./uploads/" filename)))
    ))

(defn validate-file-upload [file-attr]
  "Checks the request body file attribute for proper schema. Returns nil if OK.
   Or returns string error message."
  (if (vector? file-attr)
    (loop [[file & rest] file-attr]
      (if-not (nil? file)
        (if (vector? file)
          "Expected file attribute to be a vector of maps. Found vector inside vector.\n")
        (recur rest)))
    (if-not (map? file-attr)
      "Expected `file` attibute on request body to be a map.\n")))

(defn handle-file-upload [params]
  "Parses request body file attribute to upload files."
  (let [file-attr (params :file)
        valid-res (validate-file-upload file-attr)]
    (if-not (nil? valid-res)
      valid-res
     (if (vector? file-attr)
       (loop [[file & rest] file-attr]
         (if-not file
           (println "Uploaded files.")
           (do
             (copy-file-local file)
             (recur rest))))
      (copy-file-local file-attr)))))

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
    (println (jdbc/execute! ds ["select * from tracks"]))
    (-> (handler/site main-routes))))
