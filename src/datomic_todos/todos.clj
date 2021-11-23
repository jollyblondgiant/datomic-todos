(ns datomic-todos.todos
  (:require [datomic.api :as d]))

(def db-uri "datomic:mem://foo")

(def todo-schema [{:db/ident :todo/description
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc "a description of the todo"}
                  {:db/ident :todo/creator
                   :db/valueType :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc "the creator of the todo"}
                  {:db/ident :todo/complete
                   :db/valueType :db.type/boolean
                   :db/cardinality :db.cardinality/one
                   :db/doc "whether the todo is complete"}
                  {:db/ident :todo/id
                   :db/valueType :db.type/string
                   :db/unique :db.unique/identity
                   :db/cardinality :db.cardinality/one
                   :db/doc "for updating or deleting tasks"
                   }])


(def default-todos [{:todo/description "set up http server"
                     :todo/complete true
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description "create CRUD ops on todos"
                     :todo/complete true
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description "setup user login"
                     :todo/complete true
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description
                     "create graphing for todos"
                     :todo/complete false
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description "create login query"
                     :todo/complete true
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description "create logout query"
                     :todo/complete true
                     :todo/id (str (d/squuid))
                     :todo/creator "andy@apogenius.com"}
                    {:todo/description
                     "write really good documentation"
                     :todo/creator "andy@apogenius.com"
                     :todo/id (str (d/squuid))
                     :todo/complete false}])


(d/create-database db-uri)
(defonce conn (d/connect db-uri))


(map (fn [tx] @(d/transact conn tx)) [todo-schema default-todos])

(defn add-new-todo
  "asserts new todo
  based on input from client
  default: incomplete"
  [user description]
  (->> [{:todo/creator user
         :todo/description description
         :todo/id (str (d/squuid))
         :todo/complete false}]
       (d/transact conn)))

(defn remove-todo
  "issues a retract
  to delete tasks and preserve
  db of record"
  [task]
  (->> [[:db/retractEntity task]]
       (d/transact conn)))

(defn complete-todo
  "issues an assert
  updating task to complete
  be at ease, todo"
  [task]
  (->> [{:db/id [:todo/id task]
         :todo/complete true}]
       (d/transact conn)))

(defn incomplete-todo
  "issues an assert
  reverting task incomplete
  back to work, todo"
  [task]
  (->> [{:db/id [:todo/id task]
         :todo/complete false}]
       (d/transact conn)))

(defn get-todos-by-user
  "finds incomplete tasks
  that were created by the
  logged-in user's name"
  [user]
  (d/q '[:find ?description ?id
         :in $ ?user
         :where [?todo :todo/creator ?user]
         [?todo :todo/complete false]
         [?todo :todo/id ?id]
         [?todo :todo/description ?description]]
       (d/db conn)
       user))

(defn get-dones-by-user
  "finds completed tasks
  that were created by the
  logged-in user's name"
  [user]
  (-> '[:find ?description ?id
        :in $ ?user
        :where [?todo :todo/creator ?user]
        [?todo :todo/complete true]
        [?todo :todo/id ?id]
        [?todo :todo/description ?description]]
      (d/q (d/db conn) user)))

(defn get-todo
  "takes an id string
  and returns a task id
  for use in retracts"
  [uuid]
  (d/q
   '[:find ?task
     :in $ ?uuid
     :where
     [?task :todo/id ?uuid]]
   (d/db conn)
   uuid))

(defn get-todos
  "this one gets all tasks,
  regardless of creator.
  aren't we curious?
  "
  []
  (d/q
   '[:find ?description
     :where
     [?todo :todo/description ?description]]))


(defn get-completed-todos
  "get all complete tasks,
  regardless of creator
  an edge case, surely."
  []
  (d/q
   '[:find ?description
     :where
     [?todo :todo/complete true]
     [?todo :todo/description ?description]]))

(defn get-completed-todos-by-user
  "gets all complete tasks
  which the currently logged-in
  user created"
  [user]
  (d/q
   '[:find ?description
     :in $ ?user
     :where [?todo :todo/creator ?user]
     [?todo :todo/complete true]
     [?todo :todo/description ?description]]
   (d/db conn)
   user)
  )

(defn tx-history
  "gets tx-ids
  of task-related asserts
  for todo-snapshot"
  []
  (d/q
   '[:find ?tx
     :where
     [?tx :db/txInstant]]
   (d/db conn)))

(defn todo-snapshot
  "get incomplete tasks
  at a given point in time
  for todo-burndown"
  [user tx]
  (d/q
   '[:find ?description
     :in $ ?user
     :where [?todo :todo/creator ?user]
     [?todo :todo/complete false]
     [?todo :todo/description ?description]]
   (d/as-of (d/db conn) tx)
   user))

(defn tx-graph-for-user
  "helper function formats
  todo-snapshot list into
  vector for graphing"
  [user]
  (->> (tx-history)
       (map (fn [txs]
              (first txs)))
       sort
       (reduce
        #(conj %1
               [%2 (count (todo-snapshot user %2))]
               ) []
        )))

(defn todo-history
  "finds complete todo
  history for a user
  with tx info"
  [user]
  (d/q
   '[:find ?description ?complete ?tx ?op
     ;;^ search for timestamp of tx and not just tx
     ;;^ also make sure you're only getting txs assoc's to user
     :in $ ?user
     :where
     [?todo :todo/complete ?complete ?tx ?op]
     [?todo :todo/creator ?user]
     [?todo :todo/description ?description]]
   (-> conn d/db d/history)
   user))
