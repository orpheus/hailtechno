(ns hailtechno.util)

(defn current-timestamp []
  (java.sql.Timestamp. (System/currentTimeMillis)))

(defn rand-uuid []
  (java.util.UUID/randomUUID))

(defn strip-ns [some-map]
  (reduce-kv (fn [m k v] (assoc m (name k) v)) {} some-map))

(defn filter-map-vals
  "Filters out unspecifed key-values on a map.
  Returns a map with only the keys described in `keys` and their values.
  `obj` is a clojure map and `keys` is a clojure set."
  [obj keys]
  (into {} (filter (fn [key-val] (keys (first key-val))) obj)))
