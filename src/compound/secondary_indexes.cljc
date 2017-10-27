(ns compound.secondary-indexes)

(defmulti empty (fn [index-def] (:compound.secondary-index/type index-def)))
(defmulti add (fn [index index-def added] (:compound.secondary-index/type index-def)))
(defmulti remove (fn [index index-def removed] (:compound.secondary-index/type index-def)))

