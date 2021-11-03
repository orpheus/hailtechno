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
      wrap-multipart-params
      armor/with-access-code))

(defn authorize-and-upload [request fsf-backend]
  (let [authorized-token (:ht-access-token request)]
    (if (response? authorized-token)
      authorized-token
      (fsf/handle-upload request fsf-backend))))

(defn create-upload-route [controller fsf-backend]
  (upload-route
   (POST controller request
         (authorize-and-upload request fsf-backend))))

;; add rollbacks if exception in callbacks

(def fsf-backend-track-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
            :filepath trackpath
            :metadata ["_artist" "album" "_trackname"]}
   :callback (fn [track-data request]
               (db/save-track track-data)
               (response "Uploaded."))})

(def fsf-backend-mix-upload
  {:config {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
            :filepath (fsf/fsroot "/mixes/{artist}")
            :metadata ["_artist" "_mixname"]}
   :callback (fn [mix-data _]
               (db/save-mix mix-data)
               (response "OK"))})

(def fsf-backend-video-upload
  {:config {:accepts #{"video/mp4"}
            :filepath (fsf/fsroot "/video/{artist}")
            :metadata ["_artist" "_videoname"]}
   :callback (fn [vid-data _]
               (db/save-video vid-data)
               (response "Uploaded."))})

(def fsf-backend-image-upload
  {:config {:accepts #{"image/png" "image/jpeg" "image/jpg"}
            :filepath (fsf/fsroot "/images/{artist}")
            :metadata ["_artist" "_imgname"]}
   :callback (fn [img-data _]
               (db/save-image img-data)
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

(defn get-track-by-id []
  (GET (apiroot "/track/:id") [id]
       (if-let [track (db/get-track id)]
         (response (io/input-stream
                    (io/file (track :tracks/filepath)))))))



(defn get-image-by-id []
  (GET (apiroot "/image/:id") [id]
       (if-let [record (db/get-image id)]
         (response (io/input-stream
                    (io/file (str (record :images/filepath)
                                  "/"
                                  (record :images/filename))))))))

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
  (get-track-by-id)
  (get-image-by-id))

(defroutes all-routes
  upload-routes
  public-routes
  (login-route)
  (validate-access-token-route)
  (route/not-found "<h1>404</h1>"))

(def app
  (do
    (db/setup)
    (-> (handler/site all-routes))))
