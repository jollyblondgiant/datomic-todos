(ns datomic-todos.routes
  (:require [datomic-todos.todos :as todos]
            [datomic-todos.graph :refer [task-graph]]
            [clojure.string :refer [join]]
            [cheshire.core :refer :all]))

(def login-prompt
  (ring.util.response/response
   "please login at /login?user={email@domain.eg}\n
    if using curl, be sure to use cookie flags, eg\n
    curl -b <path/to/cookies.txt> -c <path/to/cookies.txt> <server>:3000<request>\n"))

(defn login
  "validates email
  and adds username cookie
  then redirects home"
  [{route :uri params :params
    :as request}]
  (if-let [user  (->> "user"
                      (get params)
                      (re-matches #"[a-zA-z0-9+_.-]+@[a-zA-Z0-9]+.[a-z]+"))]
    (-> {:data "successfully logged in. refer to :meta > :links"
         :meta {:docstring "a successful login attempt. links: :get-todos gets incomplete todos for logged in user;  :get-dones gets completed todos for logged in user; :task-chart returns a vector of task-counts by timestamp; :task-graph returns a visualization of the burndown data;  :logout ends session. "
                :links {:get-todos "/todos"
                        :get-dones "/dones"
                        :task-chart "/chart"
                        :task-graph "/graph"
                        :logout "/logout"
                        }}}
        generate-string
        ring.util.response/response
        (ring.util.response/set-cookie
         "user" user {:max-age 300}))
    (ring.util.response/bad-request)))

(defn logout
  "prints cheerful message
  and resets user's cookie store
  thanks for todoing!"
  [request]
  (->
   (ring.util.response/response "successfully logged out!\n")
   (ring.util.response/set-cookie "user" "logout" {:max-age 0})))

(defn home
  "checks user cookies
  and returns api links
  in cheshire json"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (-> {:data "successfully logged in. refer to :meta > :links"
         :meta {:docstring "a successful login attempt. links: :get-todos gets incomplete todos for logged in user;  :get-dones gets completed todos for logged in user; :task-chart returns a vector of task-counts by timestamp; :task-graph returns a visualization of the burndown data;  :logout ends session. "
                :links {:get-todos "/todos"
                        :get-dones "/dones"
                        :task-chart "/chart"
                        :task-graph "/graph"
                        :logout "/logout"
                        }}}
        generate-string
        ring.util.response/response)
    login-prompt))

(def nav
  '("<a href='/logout'>log out</a>" "|"
    "<a href='/'>home</a>"          "<br/>"
    "<span>add new todo by navigating to "
    "localhost:3000/add-todo?todo={description}</span>")
  )

(defn dones
  "gets completed tasks
  and returns cheshire json
  with tasks and actions"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [todos (todos/get-dones-by-user user)
          todo (fn [coll [label id]]
                 (conj coll {:data label
                             :meta {:docstring ""
                                    :links {:incomplete-todo (str "/incomplete-todo?id=" id)
                                            :forget-todo (str "/forget-todo?id=" id)}}}))]
      (-> (ring.util.response/response
           (->> todos (reduce todo [])
                (assoc {:meta {:docstring ""
                               :links {:logout "/logout"
                                       :home "/"}}} :data)
                generate-string))
          (ring.util.response/content-type "text/json")))
    login-prompt))


(defn todos
  "gets incomplete tasks
  and returns cheshire json
  with tasks and actions"
  [{route :uri params :params
              :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [todos (todos/get-todos-by-user user)
          todo (fn [coll [label id]]
                 (conj coll {:data label
                             :meta {:docstring ""
                                    :links {:complete-todo (str "/complete-todo?id=" id)
                                            :forget-todo (str "/forget-todo?id=" id)}}}))]
      (-> (ring.util.response/response
           (->> todos (reduce todo [])
                (assoc {:meta {:docstring ""
                               :links {:logout "/logout"
                                       :home "/"}}} :data)
                generate-string))
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn complete-todo
  "sends assert request
  marking todo as complete
  and then redirects"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (todos/complete-todo id)
      (-> {:data ""
           :meta {:docstring ""
                  :links {:logout "/logout"
                          :home "/"
                          :incomplete-todo ""
                          :forget-todo ""
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-incomplete-todos ""
                          :get-complete-todos ""}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn incomplete-todo
  "sends assert request
  marking task as incomplete
  and then redirects"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (todos/incomplete-todo id)
      (-> {:data ""
           :meta {:docstring ""
                  :links {:logout "/logout"
                          :home "/"
                          :complete-todo ""
                          :forget-todo ""
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-incomplete-todos ""
                          :get-complete-todos ""}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn forget-todo
  "sends retract request
  removing task completely
  and then redirects"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (-> id
          todos/get-todo ffirst
          todos/remove-todo )
      (-> {:data ""
           :meta {:docstring ""
                  :links {:logout "/logout"
                          :home "/"
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-incomplete-todos ""
                          :get-complete-todos ""}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn add-todo
  "takes description string
   makes assert request with it
  and then redirects"
  [{route :uri params :params :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [description (get params "todo")]
      (todos/add-new-todo user description)
      (-> {:data "successfully added new todo"
           :meta {:docstring ""
                  :links {:logout "/logout"
                          :home "/"
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-incomplete-todos ""
                          :get-complete-todos ""}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn todo-chart
  "gets task history
  and formats it to table
  in cheshire json"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (-> (ring.util.response/response
         (generate-string
          {:data (todos/tx-graph-for-user user)
           :meta {:docstring "vector of [timestamp task-count] vectors"
                  :links {:logout "/logout"
                          :home "/"
                          :add-todo "/add-todo?todo={description}"}}})
         (ring.util.response/content-type "text/json")))
    login-prompt))

(defn todo-graph
  "gets task history
  and sends to task-graph to be
  rendered in new tab"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [chart (todos/tx-graph-for-user user)]
      (task-graph chart))
    login-prompt))
