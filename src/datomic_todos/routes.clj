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

(def links-guide
  "links: :get-todos gets incomplete todos for logged in user;  :get-dones gets completed todos for logged in user; :task-chart returns a vector of task-counts by timestamp; :task-graph returns a visualization of the burndown data;  :logout ends session. :add-todo bears a %s token for interpolation of task description")

(defn login
  "validates email
  and adds username cookie
  welcome to todo!"
  [{route :uri params :params
    :as request}]
  (if-let [user  (->> "user"
                      (get params)
                      (re-matches #"[a-zA-z0-9+_.-]+@[a-zA-Z0-9]+.[a-z]+"))]
    (-> {:data "successfully logged in. refer to :meta"
         :meta {:docstring links-guide
                :links {:get-todos "/todos"
                        :get-dones "/dones"
                        :add-todo "/add-todo?todo=%s"
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
    (-> {:data (str "logged in as: " user)
         :meta {:docstring links-guide
                :links {:get-todos "/todos"
                        :get-dones "/dones"
                        :task-chart "/chart"
                        :task-graph "/graph"
                        :logout "/logout"
                        }}}
        generate-string
        ring.util.response/response)
    login-prompt))


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
                             :meta {:docstring "task description. task id formatted into :links > :incomplete-todo, which asserts task is incomplete and :links > :forget-todo, which retracts task."
                                    :links {:incomplete-todo (str "/incomplete-todo?id=" id)
                                            :forget-todo (str "/forget-todo?id=" id)}}}))]
      (-> (ring.util.response/response
           (->> todos (reduce todo [])
                (assoc {:meta {:docstring "a vector of maps with :data = task description."
                               :links {:logout "/logout"
                                       :add-todo "/add-todo?todo=%s"
                                       :help "/"}}} :data)
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
                             :meta {:docstring "task description. task id formatted into :links > :complete-todo, which asserts task is complete and :links > :forget-todo, which retracts task."
                                    :links {:complete-todo (str "/complete-todo?id=" id)
                                            :forget-todo (str "/forget-todo?id=" id)}}}))]
      (-> (ring.util.response/response
           (->> todos (reduce todo [])
                (assoc {:meta {:docstring "a list of maps with :data = task description."
                               :links {:logout "/logout"
                                       :add-todo "/add-todo?todo=%s"}}} :data)
                generate-string))
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn complete-todo
  "sends assert request
  marking todo as complete
  congratulations!"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (todos/complete-todo id)
      (-> {:data "task asserted as complete? = true"
           :meta {:docstring (str "includes task id pre-formatted into :incomplete-todo to assert task as complete? = false, and :forget-todo to retract task. " links-guide)
                  :links {:logout "/logout"
                          :incomplete-todo (str "/incomplete-todo?id=" id)
                          :forget-todo (str "/forget-todo?id=" id)
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-todos "/todos"
                          :get-dones "/dones"}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn incomplete-todo
  "sends assert request
  marking task as incomplete
  better luck next time"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (todos/incomplete-todo id)
      (-> {:data "task asserted as complete? = false"
           :meta {:docstring (str "includes task id pre-formatted into :complete-todo to assert task as complete? = true, and :forget-todo to retract task. " links-guide)
                  :links {:logout "/logout"
                          :complete-todo (str "/complete-todo?id=" id)
                          :forget-todo (str "/forget-todo?id=" id)
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-todos "/todos"
                          :get-dones "/dones"}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn forget-todo
  "sends retract request
  removing task completely
  don't do this often."
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (let [id (get params "id")]
      (-> id
          todos/get-todo ffirst
          todos/remove-todo )
      (-> {:data "task retracted from history"
           :meta {:docstring links-guide
                  :links {:logout "/logout"
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :add-todo "/add-todo?todo=%s"
                          :get-todos "/todos"
                          :get-dones "/dones"}}}
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
      (-> {:data (str "successfully added new todo : " description)
           :meta {:docstring links-guide
                  :links {:logout "/logout"
                          :task-chart "/chart"
                          :task-graph "/graph"
                          :get-todos "/todos"
                          :get-dones "/dones"}}}
          generate-string
          ring.util.response/response
          (ring.util.response/content-type "text/json")))
    login-prompt))

(defn todo-chart
  "gets task history
  and returns nested vectors
  in cheshire json"
  [{route :uri params :params
    :as request}]
  (if-let [user (-> request :cookies (get "user") :value)]
    (-> (ring.util.response/response
         (generate-string
          {:data (todos/tx-graph-for-user user)
           :meta {:docstring "vector of [timestamp task-count] vectors"
                  :links {:logout "/logout"
                          :get-complete-todos "/dones"
                          :get-todos "/todos"
                          :add-todo "/add-todo?todo=%s"}}})
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
