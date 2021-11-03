(ns hailtechno.auth
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [ring.util.response :refer [response]]))

(defn unauthenticated-handler
  [request metadata]
  (-> (response "Unauthorized by the Decree of the Techno Lord")
      (assoc :status 403)))

(defn basic-auth-authenticate [request authdata]
  (let [username (:username authdata)
        password (:password authdata)]
    nil))

(def basic-auth-backend
  (backends/basic {:realbm "com.hailtechno"
                   :authfn basic-auth-authenticate
                   :unauthorized-handler unauthenticated-handler}))

(defn with-basic-auth [route-handler]
  (-> route-handler
      (wrap-authentication basic-auth-backend)
      (wrap-authorization basic-auth-backend)))
