(ns datomic-todos.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [clojure.string :refer [join split]]
            [ring.middleware.params :refer [wrap-params]]
            [datomic-todos.routes :as routes]
            ))


(defn handler
  "defines routes for app
  case evaluates request
  for lazy routing"
  [{route :uri :as request}]
  (case route
    "/" (routes/home request)
    "/login" (routes/login request)
    "/todos" (routes/todos request)
    "/dones" (routes/dones request)
    "/logout" (routes/logout request)
    "/complete-todo" (routes/complete-todo request)
    "/incomplete-todo" (routes/incomplete-todo request)
    "/forget-todo" (routes/forget-todo request)
    "/add-todo" (routes/add-todo request)
    "/graph" (routes/todo-graph request)
    "/chart" (routes/todo-chart request)
    (ring.util.response/not-found)))

(defn -main
  "entrypoint for app
  starts the ring-jetty server
  enables cookies"
  [& args]
  (let [port 3000]
    (run-jetty
     (-> handler
         wrap-params
         wrap-cookies)
     {:port port})
    ))
