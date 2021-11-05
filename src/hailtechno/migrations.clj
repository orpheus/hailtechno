(ns hailtechno.migrations
  (:require [hailtechno.db :refer [db-config]]
            [migratus.core :as migratus]))

(def migconfig
  {:store                :database
   :migration-dir        "migrations/"
   :init-script          "init.sql"
   :init-in-transaction? false
   :migration-table-name "db_migration"
   :db db-config})

(defn miginit []
  (migratus/init migconfig))

(defn migrate []
  (migratus/migrate migconfig))

(defn migcreate [name]
  (migratus/create migconfig name))

(defn migrollback []
  (migratus/rollback))
