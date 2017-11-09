(ns compound.secondary-indexes.composite
  (:require [compound.custom-key :as cu]
            [compound.core :as c]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]))

(s/def ::keys (s/coll-of keyword? :kind vector?))
(s/def ::custom-key keyword?)
(s/def ::id any?)

#_(defmethod csi/spec :compound/one-to-many-nested
  [_]
  (s/keys :req-un [(or ::keys ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/compound
  [index-def]
  (let [{:keys [compound]} index-def]
    (c/compound compound)))

(defmethod csi/id :compound/compound
  [index-def]
  (or (:id index-def)
      (:key (get-in index-def [:compound :primary-index-def]))
      (:custom-key (get-in index-def [:compound :primary-index-def]))))

(defmethod csi/add :compound/compound
  [index index-def added]
  (c/add-items index added))

(defmethod csi/remove :compound/compound
  [index index-def removed]
  (let [primary-index-def (c/primary-index-def index)
        {:keys [key custom-key]} primary-index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))]
    (c/remove-keys index (map key-fn removed))))

(-> (c/compound {:primary-index-def {:key :a}
                 :secondary-index-defs [{:index-type :compound/compound
                                         :compound {:primary-index-def {:key :b}
                                                    :secondary-index-defs [{:key :c}]}}]})
    (c/add-items #{{:a 1 :b 2 :c 4} {:a 2 :b 3 :c 3}}))


(-> (c/compound {:primary-index-def {:key :table}
                 :secondary-index-defs [{:index-type :compound/compound
                                         :compound {:primary-index-def {:key :b}
                                                    :secondary-index-defs [{:key :c}]}}]})
    (c/add-items #{{:a 1 :b 2 :c 4} {:a 2 :b 3 :c 3}}))
