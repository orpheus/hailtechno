(defproject hailtechno "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[compojure "1.6.2"]
                 [com.github.seancorfield/next.jdbc "1.2.724"]
                 [org.clojure/clojure "1.10.1"]
                 [org.postgresql/postgresql "42.2.23"]
                 [funcool/datoteka "2.0.0"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring/ring-devel "1.8.2"]
                 ]
  :ring {:handler hailtechno.core/app}
  :plugins [[lein-ring "0.12.5"]]
  :main ^:skip-aot hailtechno.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
