(ns datomic-todos.graph
  (:require [com.hypirion.clj-xchart :as c]))


(defn task-graph
  "plots todos and time
  in an image that opens
  in its own window"
  [tasks]
  (-> {"Tasks" [(-> tasks count range)
                (map last tasks)]}
      c/xy-chart
      c/view))
