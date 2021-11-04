(ns hailtechno.util)

(defn current-timestamp []
  (java.sql.Timestamp. (System/currentTimeMillis)))

(defn rand-uuid []
  (java.util.UUID/randomUUID))

(defn strip-ns [some-map]
  (reduce-kv (fn [m k v] (assoc m (name k) v)) {} some-map))
