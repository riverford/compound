(ns compound.secondary-indexes.many-to-many
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/many-to-many
  [_]
  (s/keys :req [::key-fn]))

(defmethod csi/empty :compound.secondary-index.types/many-to-many
  [index-def]
  {})

(defmethod csi/add :compound.secondary-index.types/many-to-many
  [index index-def added]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn add-items [index item]
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

(defmethod csi/remove :compound.secondary-index.types/many-to-many
  [index index-def removed]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn remove-items [index item]
                            (let [ks (key-fn item)
                                  kvs (reduce (fn [kvs k]
                                                (let [existing-items (get index k #{})]
                                                  (conj kvs k (disj existing-items item))))
                                              []
                                              ks)]
                              (apply assoc! index kvs)))
                          (transient index)
                          removed)]
    (persistent! new-index)))
