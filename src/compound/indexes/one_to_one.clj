(ns compound.indexes.one-to-one
  (:require [compound.core :as c]
            [clojure.spec.alpha :as s]))

;; One to one index

;; Use when (key-fn item) returns a single key for each item.
;; Stores the item against the (key-fn item); throw if
;; (key-fn item) returns a duplicate key to a previous item. 

(defmethod c/index-def-spec :compound.index.types/one-to-one
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(defmethod c/index-def->behaviour :compound.index.types/one-to-one
  [index-def]
  (let [{:compound.index/keys [id key-fn]} index-def]
    {:compound.index.behaviour/empty {}
     :compound.index.behaviour/add (fn [index added]
                                     (let [new-index (reduce (fn add-items [index item]
                                                               (let [k (key-fn item)
                                                                     existing-item (get index k)]
                                                                 (if existing-item
                                                                   (throw (ex-info "Duplicate key" {:key k, :index id, :item item}))
                                                                   (assoc! index k item))))
                                                             (transient index)
                                                             added)]
                                       (persistent! new-index)))
     :compound.index.behaviour/remove (fn [index removed]
                                        (let [new-index (reduce (fn remove-items [index item]
                                                                  (let [k (key-fn item)]
                                                                    (dissoc! index k)))
                                                                (transient index)
                                                                removed)]
                                          (persistent! new-index)))}))
