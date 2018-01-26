(ns compound.secondary-indexes.one-to-many
  (:require [compound.custom-key :as cu]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key :compound.core/key)
(s/def ::custom-key :compound.core/custom-key)
(s/def ::id any?)

(defmethod csi/spec :compound/one-to-many
  [_]
  (s/keys :req-un [(or ::key ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/one-to-many
  [index-def]
  {})

(defmethod csi/id :compound/one-to-many
  [index-def]
  (or (:id index-def)
      (:custom-key index-def)
      (:key index-def)))

(defmethod csi/add :compound/one-to-many
  [index index-def added]
  (let [{:keys [key custom-key]} index-def
        key-fn (csi/key-fn index-def)
        new-index (reduce (fn add-items [index item]
                            (let [k (key-fn item)
                                  existing-items (get index k #{})]

                              (assoc! index k (conj existing-items item))))
                          (transient index)
                          added)]
    (persistent! new-index)))

(defmethod csi/remove :compound/one-to-many
  [index index-def removed]
  (let [{:keys [key custom-key]} index-def
        key-fn (csi/key-fn index-def)
        new-index (reduce (fn remove-items [index item]
                            (let [k (key-fn item)
                                  existing-items (get index k #{})
                                  new-items (disj existing-items item)]
                              (if (empty? new-items)
                                (dissoc! index k)
                                (assoc! index k new-items))))
                          (transient index)
                          removed)]
    (persistent! new-index)))

