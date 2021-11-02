(ns hailtechno.core
  (:gen-class)
  (:use [compojure.core])
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication
                                           wrap-authorization]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [hailtechno.db :as db]
            [hailtechno.fsf :as fsf]
            [ring.util.response :refer [response bad-request response?]]
            [ring.middleware.jwt :as jwt])
  (:import (java.io File)))

(defn apiroot [controller]
  (str "/api" controller))

(def trackpath (fsf/fsroot "/tracks/{artist}/{album}"))
(def mixpath   (fsf/fsroot "/mixes/{artist}"))
(def imgpath   (fsf/fsroot "/images/{artist}"))
(def vidpath   (fsf/fsroot "/video/{artist}"))

;; save the actual filepath to the db, not the parent dir
(defn upload-track-route []
  (fsf/file-upload-route
   (apiroot "/track")
   {:accepts #{"audio/mpeg" "audio/wave" "audio/mp4"}
    :filepath trackpath
    :metadata ["_artist" "album" "_trackname"]}
   (fn [track-data request]
     (db/save-track track-data)
     (response "Uploaded."))))

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

(defn upload-mix-route []
  (fsf/file-upload-route
   (apiroot "/mix")
   {:accepts #{"audio/mpeg" "audio/vnd.wav" "audio/mp4"}
    :filepath (fsf/fsroot "/mixes/{artist}")
    :metadata ["_artist" "_mixname"]}
   (fn [mix-data _]
     ;; toDo: add rollback if exception
     (db/save-mix mix-data)
     (response "OK"))))

(defn upload-image-route []
  (fsf/file-upload-route
   (apiroot "/image")
   {:accepts #{"image/png" "image/jpeg" "image/jpg"}
    :filepath (fsf/fsroot "/images/{artist}")
    :metadata ["_artist" "_imgname"]}
   (fn [img-data _]
     (db/save-image img-data)
     (response "OK"))))

(defn get-image-by-id []
  (GET (apiroot "/image/:id") [id]
       (if-let [record (db/get-image id)]
         (response (io/input-stream
                    (io/file (str (record :images/filepath) "/" (record :images/filename)))))
         )))

(defn upload-video-route []
  (fsf/file-upload-route
   (apiroot "/video")
   {:accepts #{"video/mp4"}
    :filepath (fsf/fsroot "/video/{artist}")
    :metadata ["_artist" "_videoname"]}
   (fn [vid-data _]
     (db/save-video vid-data)
     (response "OK"))))


(defn test-route []
  (jwt/wrap-jwt (GET (apiroot "/test") request
                     (println request)
                     (response "OK"))
                {:issuers {"com.hailtechno" {:alg :HS256 :secret "secret"}}}))


(defn unauthenticated-handler
  [request metadata]
  (-> (response "Unauthorized by the Decree of the Techno Lord")
      (assoc :status 403)))

(defn basic-auth-authenticate [request authdata]
  (let [username (:username authdata)
        password (:password authdata)]
    nil))

(def basic-auth-backend (backends/basic {:realm "com.hailtechno"
                                 :authfn basic-auth-authenticate
                                 :unauthorized-handler unauthenticated-handler}))

(defn with-basic-auth [route-handler]
  (-> route-handler
      (wrap-authentication basic-auth-backend)
      (wrap-authorization basic-auth-backend)))

(defn login-route []
  (with-basic-auth
    (POST (apiroot "/login") request
          (when (not (authenticated? request))
            (throw-unauthorized))
          (response "toDo: create JWT"))))

(defroutes main-routes
  (upload-track-route)
  (get-track-by-id)
  (get-track-by-path)
  (upload-mix-route)
  (upload-image-route)
  (upload-video-route)
  (get-image-by-id)
  (login-route)
  ;; needs to be last
  (route/not-found "<h1>404</h1>"))

(def app
  (do
    (db/setup)
    (-> (handler/site main-routes))))
