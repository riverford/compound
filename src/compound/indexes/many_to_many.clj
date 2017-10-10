(ns compound.indexes.many-to-many
  (:require [compound.core :as c]
            [clojure.spec.alpha :as s]))

(defmethod c/index-def-spec :compound.index.types/many-to-many
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(defmethod c/index-def->behaviour :compound.index.types/many-to-many
  [index-def]
  (let [{:compound.index/keys [key-fn]} index-def]
    {:compound.index.behaviour/empty {}
     :compound.index.behaviour/add (fn [index added]
                                     (let [new-index (reduce (fn add-items [index item]
                                                               (let [ks (key-fn item)
                                                                     kvs (reduce (fn [kvs k]
                                                                                   (let [existing-items (get index k #{})]
                                                                                     (conj kvs k (conj existing-items item))))
                                                                                 []
                                                                                 ks)]
                                                                 (apply assoc! index kvs)))
                                                             (transient index)
                                                             added)]
                                       (persistent! new-index)))
     :compound.index.behaviour/remove (fn [index removed]
                                        (let [new-index (reduce (fn remove-items [index item]
                                                                  (let [ks (key-fn item)
                                                                        kvs (reduce (fn [kvs k]
                                                                                      (let [existing-items (get index k #{})]
                                                                                        (conj kvs k (disj existing-items item))))
                                                                                    []
                                                                                    ks)]
                                                                    (apply assoc! index kvs)))
                                                                (transient index)
                                                                removed)]
                                          (persistent! new-index)))}))
