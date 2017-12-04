(ns compound.secondary-indexes.many-to-many
  (:require [compound.custom-key :as cu]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key keyword?)
(s/def ::custom-key keyword?)
(s/def ::id any?)

(defmethod csi/spec :compound/many-to-many
  [_]
  (s/keys :req-un [(or ::key ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/many-to-many
  [index-def]
  {})

(defmethod csi/id :compound/many-to-many
  [index-def]
  (or (:id index-def)
      (:custom-key index-def)
      (:key index-def)))

(defmethod csi/add :compound/many-to-many
  [index index-def added]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))
        new-index (reduce (fn add-items [index item]
                            (let [ks (key-fn item)
                                  kvs (reduce (fn [kvs k]
                                                (let [existing-items (get index k #{})]
                                                  (conj kvs k (conj existing-items item))))
                                              []
                                              ks)]
                              (if (seq kvs)
                                (apply assoc! index kvs)
                                index)))
                          (transient index)
                          added)]
    (persistent! new-index)))

(defmethod csi/remove :compound/many-to-many
  [index index-def removed]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))
        new-index (reduce (fn remove-items [index item]
                            (let [ks (key-fn item)
                                  kvs (reduce (fn [kvs k]
                                                (let [existing-items (get index k #{})]
                                                  (conj kvs k (disj existing-items item))))
                                              []
                                              ks)]
                              (if (seq kvs)
                                (apply assoc! index kvs)
                                index)))
                          (transient index)
                          removed)]
    (persistent! new-index)))
