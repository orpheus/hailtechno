(defproject hailtechno "0.1.0"
  :description "Personal web server for music upload and streaming. I wanted somewhere to upload my music without paying the \nSoundCloud subscription."
  :url "https://github.com/orpheus/hailtechno"
  :license {:name "The GNU General Public License v3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[buddy/buddy-auth "3.0.1"]
                 [compojure/compojure "1.6.2"]
                 [com.github.seancorfield/next.jdbc "1.2.724"]
                 [environ/environ "1.2.0"]
                 [migratus/migratus "1.3.5"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.postgresql/postgresql "42.2.23"]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-devel "1.8.2"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors/ring-cors "0.1.13"]]
  :ring {:handler hailtechno.core/app}
  :plugins [[lein-environ/lein-environ "1.2.0"]
            [lein-pprint "1.3.2"]
            [lein-ring "0.12.5"]]
  :main ^:skip-aot hailtechno.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:env {:db-name "hailtechno" :db-host "localhost"}}})
