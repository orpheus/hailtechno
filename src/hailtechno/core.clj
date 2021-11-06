(ns hailtechno.core
  (:gen-class)
  (:use [compojure.core])
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
            [hailtechno.util :as util]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response bad-request response?]])
  (:import (java.io File)))

(defn apiroot [controller]
  (str "/api" controller))

(def trackpath (fsf/fsroot "/tracks/{artist}/{album}"))
(def mixpath   (fsf/fsroot "/mixes/{artist}"))
(def imgpath   (fsf/fsroot "/images/{artist}"))
(def vidpath   (fsf/fsroot "/video/{artist}"))


(defn upload-route [handler]
  (-> handler
      wrap-multipart-params ;; doesn't fail if this is missing
      armor/with-access-code))

(defn authorize-and-upload [request fsf-backend]
  (let [authorized-token (:ht-access-token request)]
    (if (response? authorized-token)
      authorized-token
      (fsf/handle-upload request fsf-backend))))

(defn create-upload-route [controller fsf-backend]
  (upload-route
   (POST controller request
         (println "Start authorize-and-upload")
         (authorize-and-upload request fsf-backend))))

;; add rollbacks if exception in callbacks

(def fsf-backend-track-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
            :filepath trackpath
            :metadata ["_artist" "album" "_trackname"]}
   :callback (fn [{:keys [trackname artist album filepath filename]}
                  {access-token :ht-access-token
                   email :email}]
               (db/save-file-upload {:display_name trackname
                                     :artist artist
                                     :album album
                                     :filepath filepath
                                     :filename filename
                                     :access_code (:id access-token)
                                     :file_type_id 0})
               (response "Uploaded."))})

(def fsf-backend-mix-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
            :filepath (fsf/fsroot "/mixes/{artist}")
            :metadata ["_artist" "_mixname"]}
   :callback (fn [{:keys [artist mixname filepath filename]}
                  {access-token :ht-access-token
                   email :email}]
               (db/save-file-upload {:artist artist
                                     :display_name mixname
                                     :filepath filepath
                                     :filename filename
                                     :access_code (:id access-token)
                                     :file_type_id 1})
               (response "OK"))})

(def fsf-backend-video-upload
  {:config {:accepts #{"video/mp4"}
            :filepath (fsf/fsroot "/video/{artist}")
            :metadata ["_artist" "_videoname"]}
   :callback (fn [{:keys [artist videoname filepath filename]}
                  {access-token :ht-access-token
                   email :email}]
               (db/save-file-upload {:artist artist
                                     :display_name videoname
                                     :filepath filepath
                                     :filename filename
                                     :access_code (:id access-token)
                                     :file_type_id 2})
               (response "Uploaded."))})

(def fsf-backend-image-upload
  {:config {:accepts #{"image/png" "image/jpeg" "image/jpg"}
            :filepath (fsf/fsroot "/images/{artist}")
            :metadata ["_artist" "_imgname"]}
   :callback (fn [{:keys [artist imgname filepath filename]}
                  {access-token :ht-access-token
                   email :email}]
               (db/save-file-upload {:artist artist
                                     :display_name imgname
                                     :filepath filepath
                                     :filename filename
                                     :access_code (:id access-token)
                                     :file_type_id 3})
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

(defn get-file-by-id []
  (GET (apiroot "/file/:id") [id]
       (try
         (response (some->> (db/get-file-by-id id)
                            io/file
                            io/input-stream))
         (catch Exception e
           (internal-server-error e)))))

(defn validate-access-token-route []
  (armor/with-access-code
    (POST "/api/access-code/validate" {access-token :ht-access-token}
          (if (response? access-token)
            access-token
            (json/write-str access-token)))))

(defn login-route []
  (auth/with-basic-auth
    (POST (apiroot "/login") request
          (when (not (authenticated? request))
            (throw-unauthorized))
          (response "toDo: create JWT"))))

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
  )

(defroutes all-routes
  upload-routes
  public-routes
  (login-route)
  (validate-access-token-route)
  (route/not-found "<h1>404</h1>"))

(def app
  (do (-> (handler/site all-routes))))
