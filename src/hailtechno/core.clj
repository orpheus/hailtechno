(ns hailtechno.core
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.java.io :as io]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            ))

(defn upload-route []
  (wrap-multipart-params
   (POST "/upload" {params :params}
         (io/copy (io/file (get-in params [:file :tempfile]))
                  (io/file (str "./uploads/" (get-in params [:file :filename]))))
         {:status 200
          :headers {"Content-Type" "text/plain"}
          :body (str "File saved")})))

(defroutes main-routes
  (GET "/" [] (str "What's up stunna"))
  (upload-route))


(def app
  (-> (handler/site main-routes)))
