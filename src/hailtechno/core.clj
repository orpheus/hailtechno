(ns hailtechno.core
  (:gen-class)
  (:use [compojure.core]
        [ring.adapter.jetty])
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [hailtechno.armor :as armor]
            [hailtechno.auth :as auth]
            [hailtechno.db :as db]
            [hailtechno.fsf :as fsf]
            [hailtechno.migrations :as migrations]
            [hailtechno.util :as util]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :as site]
            [ring.middleware.json :as ring-json]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response bad-request response?]])
  (:import (java.io File)
           (java.util UUID)))

(defn apiroot [controller]
  (str "/api" controller))

(def trackpath (fsf/fsroot "/tracks/{artist}/{album}"))
(def mixpath   (fsf/fsroot "/mixes/{artist}"))
(def imgpath   (fsf/fsroot "/images/{artist}"))
(def vidpath   (fsf/fsroot "/video/{artist}"))

(def TYPE_TRACK 0)
(def TYPE_MIX 1)
(def TYPE_IMAGE 2)
(def TYPE_VIDEO 3)


(defn upload-route [handler]
  (-> handler
      wrap-multipart-params ;; doesn't fail if this is missing
      armor/wrap-access-code))

(defn test-route []
  (wrap-multipart-params
   (POST "/api/test" {file :file params :params}
         (println params)
         (response "OK"))))

(defn authorize-and-upload [request fsf-backend]
  (let [access-token (armor/validate-access-header request)]
    (if (response? access-token)
      access-token
      (fsf/handle-upload request fsf-backend))))

(defn create-upload-route [controller fsf-backend]
  (upload-route
   (POST controller request
         (authorize-and-upload request fsf-backend))))

;; toDo: Instead of saving the file upload to the db after the
;; file system save, use a `callbefore` to save the data
;; so that if it fails you don't have to waste time with the fs

;; toDo: Add logging

(def fsf-backend-track-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4" "audio/wav"}
            :filepath trackpath
            :metadata ["_artist" "album" "_trackname"]}
   :callback (fn [{:keys [trackname artist album filepath filename content-type]} request]
               (let [access-code (armor/get-access-code request)
                     email (get-in request [:params :email])
                     account-id (:id (db/force-get-or-save-user email))]
                 (db/save-file-upload {:display_name trackname
                                       :artist artist
                                       :album album
                                       :filepath filepath
                                       :filename filename
                                       :access_code (UUID/fromString access-code)
                                       :content_type content-type
                                       :uploaded_by account-id
                                       :file_type_id TYPE_TRACK}))
               (response "Uploaded."))})

(def fsf-backend-mix-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
            :filepath mixpath
            :metadata ["_artist" "_mixname"]}
   :callback (fn [{:keys [artist mixname filepath filename content-type]} request]
               (let [access-code (armor/get-access-code request)
                     email (get-in request [:params :email])
                     account-id (:id (db/force-get-or-save-user email))]
                 (db/save-file-upload {:artist artist
                                       :display_name mixname
                                       :filepath filepath
                                       :filename filename
                                       :access_code (UUID/fromString access-code)
                                       :content_type content-type
                                       :uploaded_by account-id
                                       :file_type_id TYPE_MIX}))
               (response "Uploaded."))})

(def fsf-backend-video-upload
  {:config {:accepts #{"video/mp4" "video/quicktime"}
            :filepath vidpath
            :metadata ["_artist" "_videoname"]}
   :callback (fn [{:keys [artist videoname filepath filename content-type]} request]
               (let [access-code (armor/get-access-code request)
                     email (get-in request [:params :email])
                     account-id (:id (db/force-get-or-save-user email))]
                 (db/save-file-upload {:artist artist
                                       :display_name videoname
                                       :filepath filepath
                                       :filename filename
                                       :access_code (UUID/fromString access-code)
                                       :content_type content-type
                                       :uploaded_by account-id
                                       :file_type_id TYPE_VIDEO}))
               (response "Uploaded."))})

(def fsf-backend-image-upload
  {:config {:accepts #{"image/png" "image/jpeg" "image/jpg"}
            :filepath imgpath
            :metadata ["_artist" "_imgname"]}
   :callback (fn [{:keys [artist imgname filepath filename content-type]} request]
               (let [access-code (armor/get-access-code request)
                     email (get-in request [:params :email])
                     account-id (:id (db/force-get-or-save-user email))]
                 (db/save-file-upload {:artist artist
                                       :display_name imgname
                                       :filepath filepath
                                       :filename filename
                                       :access_code (UUID/fromString access-code)
                                       :content_type content-type
                                       :uploaded_by account-id
                                       :file_type_id TYPE_IMAGE}))
               (response "Uploaded."))})

(def track-route
  (create-upload-route (apiroot "/track") fsf-backend-track-upload))

(def image-route
  (create-upload-route (apiroot "/image") fsf-backend-image-upload))

(def mix-route
  (create-upload-route (apiroot "/mix") fsf-backend-mix-upload))

(def video-route
  (create-upload-route (apiroot "/video") fsf-backend-video-upload))


(defn get-track-by-path
  "Accepts `artist` `album` and `trackname` as query parameters to locate and
  retrieve a file on the file system."
  []
  (GET (apiroot "/track") [artist album trackname]
       (let [path (str (fsf/interpolate-path trackpath
                                         {:artist artist :album album})
                       (File/separator)
                       trackname)
             file (File. path)]
         (if (.isDirectory file)
           (bad-request (str "File not found: " path " is a directory."))
           (if (.exists file)
             (response (io/input-stream file))
             (bad-request (str path " does not exist.")))))))

(defn file-to-dto [file-upload]
  (apply dissoc file-upload [:filepath :access_code]))

(defn files-to-dto [file-uploads]
  (map file-to-dto file-uploads))

(defn internal-server-error [exception]
  {:status 500
   :body (.getMessage exception)})

(defn get-files-from-db [db-getter]
  (try {:status 200
        :headers {"Content-Type" "application/json"}
        :body (json/write-str (files-to-dto (db-getter)))}
       (catch Exception e
         (internal-server-error e))))

(defn get-tracks []
  (GET (apiroot "/tracks") _
       (get-files-from-db db/get-tracks)))

(defn get-mixes []
  (GET (apiroot "/mixes") _
       (get-files-from-db db/get-mixes)))

(defn get-images []
  (GET (apiroot "/images") _
       (get-files-from-db db/get-images)))

(defn get-video []
  (GET (apiroot "/video") _
       (get-files-from-db db/get-video)))

(defn file-response [file-upload]
  (-> (some->> (:filepath file-upload)
                  io/file
                  io/input-stream)
      ;; toDo: save content-type to db and send back here
      response
      (assoc :headers {"Content-Type" (:content_type file-upload)})))

(defn get-file-by-id []
  (GET (apiroot "/file/:id") [id]
       (try
         (let [file-upload (db/get-file-by-id id)
               response (file-response file-upload)]
           (if-not (:body response)
             (bad-request (str "File with id " id " not found."))
             response))
         (catch Exception e
           (internal-server-error e)))))

(defn validate-access-token-route []
  (ring-json/wrap-json-body
   (armor/wrap-access-code
    (POST "/api/access-code/validate" request
          (let [access-token (armor/validate-access-header request)
                email (get-in request [:body "email"])]
            ;; just auto-save email addresses for now
            (db/force-get-or-save-user email)
            (if (response? access-token)
              access-token
              (-> (json/write-str access-token)
                  response
                  (assoc :headers {"Content-Type" "application/json"}))))))))

(defn login-route []
  (auth/with-basic-auth
    (POST (apiroot "/login") request
          (when (not (authenticated? request))
            (throw-unauthorized))
          (response "toDo: create JWT"))))

(defn health-check []
  (GET "/health" _
    (response "Healthy")))

(defroutes upload-routes
  track-route
  image-route
  mix-route
  video-route)

(defroutes public-routes
  (get-file-by-id)
  (get-tracks)
  (get-mixes)
  (get-images)
  (get-video)
  (test-route)
  (health-check))

(defroutes all-routes
  upload-routes
  public-routes
  (validate-access-token-route)
  (route/not-found "<h1>404</h1>"))

(def app
  (-> all-routes
      (wrap-cors :access-control-allow-origin #".*"
                 :access-control-allow-methods [:get :put :post :delete])
      (handler/site)))




(defn -main [& args]
  (println "Starting migrations.")
  (migrations/miginit)                                      ;; toDo: how not to init everytime?
  (migrations/migrate)
  (println "End migrations.")
  (println "Service starting on port 3000")
  (run-jetty app {:port 3000}))
