(ns hailtechno.util)

(defn current-timestamp []
  (java.sql.Timestamp. (System/currentTimeMillis)))

(defn rand-uuid []
  (java.util.UUID/randomUUID))
