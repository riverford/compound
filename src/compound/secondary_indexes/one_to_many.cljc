(ns compound.secondary-indexes.one-to-many
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-many
  [_]
  (s/keys :req [::key-fn]))

(defmethod csi/empty :compound.secondary-index.types/one-to-many
  [index-def]
  {})

(defmethod csi/add :compound.secondary-index.types/one-to-many
  [index index-def added]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn add-items [index item]
                            (let [k (key-fn item)
                                  existing-items (get index k #{})]

                              (assoc! index k (conj existing-items item))))
                          (transient index)
                          added)]
    (persistent! new-index)))

(defmethod csi/remove :compound.secondary-index.types/one-to-many
  [index index-def removed]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn remove-items [index item]
                            (let [k (key-fn item)
                                  existing-items (get index k #{})]
                              (assoc! index k (disj existing-items item))))
                          (transient index)
                          removed)]
    (persistent! new-index)))

