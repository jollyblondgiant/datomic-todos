(defproject datomic-todos "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 ;;SERVER DEPS
                 [ring/ring-core "1.9.4"]
                 [ring/ring-jetty-adapter "1.9.4"]

                 ;; VISUALIZATION DEPS
                 [com.hypirion/clj-xchart "0.2.0"]

                 ;; DB DEPS
                 [com.datomic/datomic-free "0.9.5697"]

                 ;;MISC DEPS
                 [cheshire "5.10.1"] ;;json encoding
                 ]
  :main datomic-todos.core
  :repl-options {:init-ns datomic-todos.core})
