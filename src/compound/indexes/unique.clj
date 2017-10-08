(ns compound.indexes.unique
  (:require [compound.core :as c]
            [clojure.spec.alpha :as s]))

(defmethod c/index-def->spec :compound.index.types/unique
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(defmethod c/index-def->behaviour :compound.index.types/unique
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
