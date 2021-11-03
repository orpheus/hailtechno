(ns hailtechno.armor
  (:require [hailtechno.db :as db]
            [hailtechno.util :as util]
            [ring.util.response :refer [response response?]])
  (:import org.postgresql.util.PSQLException))

(defn find-header
  "Looks up a header in a headers map case insensitively,
  returning the header map entry, or nil if not present.
  https://github.com/funcool/buddy-auth/blob/master/src/buddy/auth/http.clj#L52"
  [{headers :headers} ^String header-name]
  (first (filter #(.equalsIgnoreCase header-name (name (key %))) headers)))

(defn unauthorized
  ([] (unauthorized "Unauthorized"))
  ([message]
   (assoc (response message) :status 401)))

(defn get-token-from-db [token-id]
  (try
    (db/get-access-token-by-id token-id)
    (catch PSQLException e
      (assoc (response "Invalid access code") :status 401))))

(defn is-token-expired? [token]
  (some? (some->> (:upload_access_token/exp_date token)
                  (.after (util/current-timestamp)))))

(defn is-token-exhausted? [token]
  (let [{:upload_access_token/keys [infinite uploads max_uploads]} token]
    (if (nil? max_uploads)
      false
      (>= uploads max_uploads))))

(defn is-token-valid? [token]
  (if-not token
    [false "Access code is nil."]
    (if (is-token-expired? token)
      [false "Access code is expired."]
      (if (is-token-exhausted? token)
        [false "Access code is exhausted."]
        [true nil]))))

(defn validate-access-code
  "Fetches an access-token from the db and validates it.
  Returns the token if valid or an error ring response if not."
  [access-code]
  (if-let [token (get-token-from-db access-code)]
    (if (response? token)
      token
      (let [[valid error] (is-token-valid? token)]
        (if valid
          token
          (unauthorized error))))
    (unauthorized)))

(defn with-access-code [handler]
  (fn [request]
    (let [access-code (second (find-header request "HT-ACCESS-CODE"))]
      (handler (assoc request
                      :ht-access-token (validate-access-code access-code))))))

(defn get-access-token [{token :ht-access-token}] token)
