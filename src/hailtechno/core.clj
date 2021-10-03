(ns hailtechno.core
  (:gen-class)
  (:use compojure.core)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            ))

(defroutes main-routes
  (GET "/" [] (str "What's up stunna"))
  (route/resources "/")
  (route/not-found "Page not found"))


(def app
  (-> (handler/site main-routes)))
