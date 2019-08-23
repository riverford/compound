(ns compound.secondary-indexes.one-to-one
   (:require [compound.custom-key :as cu]
             [compound.secondary-indexes :as csi]
             [compound.conflict :as cc]
             [clojure.spec.alpha :as s]))

(s/def ::key ::csi/key)
(s/def ::custom-key ::csi/custom-key)
(s/def ::id any?)

(defmethod csi/spec :compound/one-to-one
  [_]
  (s/keys :req-un [(or ::key ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/one-to-one
  [index-def]
  {})

(defmethod csi/id :compound/one-to-one
  [index-def]
  (or (:id index-def)
      (:custom-key index-def)
      (:key index-def)))

(defmethod csi/add :compound/one-to-one
  [index index-def added]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))
        new-index (reduce (fn add-items [index item]
                            (let [k (key-fn item)
                                  existing-item (get index k)]
                              (if existing-item
                                (assoc! index k (cc/on-conflict-fn index-def existing-item item))
                                (assoc! index k item))))
                          (transient index)
                          added)]
    (persistent! new-index)))

(defmethod csi/remove :compound/one-to-one
  [index index-def removed]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))
        new-index (reduce (fn remove-items [index item]
                            (let [k (key-fn item)]
                              (dissoc! index k)))
                          (transient index)
                          removed)]
    (persistent! new-index)))
