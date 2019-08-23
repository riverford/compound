(ns compound.conflict
  (:require [compound.custom-key :as cu]))

(defmulti on-conflict-fn
  (fn [index-def existing-item new-item] (get index-def :on-conflict)))

(defmethod on-conflict-fn :default
  [index-def existing-item new-item]
  (println (str "Implementation of conflict for index " index-def  " not found, replacing"))
  new-item)

(defmethod on-conflict-fn :compound/replace
  [_ existing-item new-item]
  new-item)

(defmethod on-conflict-fn :compound/throw
  [index-def existing-item new-item]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))]
    (throw (ex-info (str "Duplicate key " (key-fn new-item) " in index "  index-def) {:existing-item existing-item, :new-item new-item}))))

(defmethod on-conflict-fn :compound/merge
  [_ existing-item new-item]
  (merge existing-item new-item))
