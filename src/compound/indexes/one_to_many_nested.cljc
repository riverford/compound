(ns compound.indexes.one-to-many-nested
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [clojure.spec.alpha :as s]))

(defmethod cs/index-def-spec :compound.index.types/one-to-many-nested
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(defmethod c/index-def->behaviour :compound.index.types/one-to-many-nested
  [index-def]
  (let [{:compound.index/keys [key-fn]} index-def]
    {:compound.index.behaviour/empty {}
     :compound.index.behaviour/add (fn [index added]
                                     (let [new-index (reduce (fn add-items [index item]
                                                               (let [ks (key-fn item)]
                                                                 (update-in index ks (fnil conj #{}) item)))
                                                             index
                                                             added)]
                                       new-index))
     :compound.index.behaviour/remove (fn [index removed]
                                        (let [new-index (reduce (fn remove-items [index item]
                                                                  (let [ks (key-fn item)]
                                                                    (update-in index ks disj item)))
                                                                index
                                                                removed)]
                                          new-index))}))
