(ns hailtechno.fsf
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [compojure.core :refer [POST]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [bad-request response?]])
  (:import (java.io File)))

(defn fsroot [path]
  (str "fsroot" path))

(defn save-file-to-fs
  "Saves a File to a filesystem path and creates the intermediate directories."
  [^File File ^String filepath]
  (let [file-to-create (File. filepath)]
    (if (.exists file-to-create)
      (str filepath " already exists.")
      (let [parent-file (-> file-to-create .getParent File.)]
        (if-not (.isDirectory parent-file)
          (.mkdirs parent-file))
        (io/copy File file-to-create)))))

(defn field-to-re
  "Make a regex patter out of given field surrounded by brackets.
  Used for string interpolation."
  [field]
  (re-pattern (str "\\{" field "\\}")))

(defn ^String interpolate-string
  "Interpolate string using brackets `{someVar}`.

  example:
  => (interpolate-string \"/tracks/{artist}/{album}\"
                         {:artist porter, :album nurture)
  => \"/tracks/porter/nurture\"

  If value in map is nill, will substitute with empty string."
  [string values]
  (let [matcher (re-matcher #"\{(.*?)\}" string)]
    (loop [match (re-find matcher)
           re-string string]
      (if-not match
        re-string
        (recur (re-find matcher)
               (clojure.string/replace re-string
                                       (field-to-re (second match))
                                       (-> match
                                           second
                                           keyword
                                           values
                                           (or ""))))))))

(defn ^String interpolate-path
  "String interpolation with double slash removal set to lowercase."
  [path params]
  (-> (interpolate-string path params)
      (clojure.string/replace #"//" "/")
      clojure.string/lower-case
      .trim
      (.replaceAll "\\s+" "_")))

(defn strip-underscore-prefix [str]
  (clojure.string/replace str #"^_" ""))

(defn delete-file [filepath]
  (let [file (File. filepath)]
    (.delete file)
    ;; only deletes parent folder if empty
    (.delete (.getParentFile file))))

(defn store-file [params config]
  (let [dirpath (interpolate-path (:filepath config) params)
        filename (get-in params [:file :filename])
        ;; toDo: clean up
        filepath (str/replace (str dirpath (File/separator) filename) #"//" "/")
        tempfile     (get-in params [:file :tempfile])]
    (let [error (save-file-to-fs tempfile filepath)]
      [{:filepath filepath, :filename filename :file tempfile} error])))

(defn validate-content-type [params config]
  (let [{{content-type :content-type} :file} params
        accepts (:accepts config)]
    (if-not (set? accepts)
      ;; toDo: return Ring response from lower in the code
      "Interval server error: file-upload-route config.accepts must be a set."
      (if-not (accepts content-type)
        (str "File content-type must be one of: " (str/join ", " accepts)
             " Was: " content-type)))))

(defn validate-required-req-params [params config]
  (loop [[param & rest] (cons "_file" (:metadata config))]
    (if (and param ;; not nil
             (str/starts-with? param "_") ;; starts with `_`
             (not (params (keyword (subs param 1))))) ;; and is not in the req body
      (str "Missing required request body parameter: " (subs param 1))
      ;; else recur
      (if rest (recur rest)))))

(defn validate-req-params
  "Checks the request body `params` map for required fields as found in the
  `:metadata` vector of the `config` map denoted by strings that start with `_`,
   an underscore."
  [params config]
  (let [param-error   (validate-required-req-params params config)
        content-error (validate-content-type params config)]
    (if param-error
      param-error
      content-error)))

;; Returns: Vector<Filemap, Error>
(defn validate-and-store [req-params config]
  (if-let [error (validate-req-params req-params config)]
    [nil error]
    (try (store-file req-params config)
         (catch Exception e [nil (str "Unknown exception in storing file(s): "
                                      (.getMessage e))]))))

(defn agg-metadata
  "Aggregate data for return value/as argument to the file handler callback.
  Merges the filemap with the param values as defined in the `:metedata`
  vector of the config map."
  [params filemap config]
  (loop [[field & rest] (:metadata config)
         agg filemap]
    (if-not field
      agg
      (let [key (keyword (strip-underscore-prefix field))
            val (key params)]
        (recur rest (if val (assoc agg key val) agg))))))

(defn handle-upload
  "Configurable file upload used in conjuction with an http handler.
  Takes a an http `request` map (ring) and custom map with a `config`
  and `callback` key. Using the config map, the `handle-upload` fn
  performs validation on the request ensuring there is a `file` property
  present in the request as well as ensuring that other required fields
  exist as specified in the `config` map.

  config: {
    :accepts Set::content-types #(\"audio/mpeg\" \"audio/vnd.wav\")
    :filepath String \"path/to/save/file\"
    :metadata Vector::String [\"trackname\" \"artistname\"]
  }
  callback: Fn (fn [Map {:keys [trackname artistname filemap]}]) ()

  The function looks for the `file` key to exist as part of the req
  params. This `file` must have a `content-type` that matches one of
  the values inside the `:accepts` set

  The `:filepath` is the location where the file will be stored.
  Accepts templates such as 'file/{trackname}' where `{trackname}` will
  be substituted for the `trackname` parameter as defined to lookup by the metadata.
  Can also optionally template via `file/{trackname}/{album?}` where if `album`
  is defined in the metadata, it will use it as part of the path.

  The `:metadata` is a list of values to look up in the `params` map of
  the request body to be used as templates in the filepath and as arguments
  for the callback. This function will only look at the variables
  in the params map that are defined in the `:metadata` field. To mark
  a field as requied, prefix it with an underscore. This will not change
  how thpe callback fn will receive this parameter, e.g. the underscore will
  be stripped after it has verified that value is non-null and non-empty.

  The `callback` parameter is a function that takes a map of metadata from
  the request and vars calculated during execution for the user to use to
  perform side effects like update a database. It takes as a second argument
  the actual http `request`.
  "
  [request {:keys [config callback]}]
  (let [[filemap error] (validate-and-store (:params request) config)]
    (if error (bad-request error)
        (try
          (let [res (callback (agg-metadata (:params request) filemap config) request)]
            (if (response? res) res
                {:status 500
                 :body "Callback passed to `fsf/handle-upload` returned an invalid ring response map."}))
          (catch Exception e
            (delete-file (:filepath filemap))
            {:status 500
             :body (.getMessage e)})))))
