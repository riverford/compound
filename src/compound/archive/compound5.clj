(ns compound.compound5
  (:require [compound.data :refer :all]))

(defn indexes [c]
  (get c :compound/indexes))

(defn index-defs [c]
  (get c :compound/index-defs))

(defn index [c id]
  (get (indexes c) id))

(defn primary-index-id [c]
  (get (indexes (index-defs c)) :primary))

(defn non-primary-index-ids [c]
  (get (indexes (index-defs c)) :non-primary))

(defn index-def [c id]
  (get-in (indexes (index-defs c)) [:id id]))

(defn primary-index-def [c]
  (index-def c (primary-index-id c)))

(defn add [c items]
  (let [{:keys [plus id]} (primary-index-def c)]
    (reduce plus c items)))

(add compound [1 2 3 4])
