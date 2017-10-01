(ns compound.index-multi
  (:require [compound.protocols :as p]
            [clojure.spec.alpha :as s]
            [compound.spec]))

(defrecord IndexMulti [index index-def])

(defmulti query-refs (fn [index [op & args]] op))

(defmethod query-refs :get-by-key
  [this q]
  (let [{:keys [index]} this
        [op k] q]
    (get index k)))

(defmethod query-refs :get-by-keys
  [this q]
  (let [{:keys [index]} this
        [op ks] q]
    (mapcat #(get index %)) ks))

(extend-type IndexMulti
  p/IIndexSecondary
  (-on-add [this added]
    (let [{:keys [index index-def]} this
          {:keys [key-fn]} index-def
          new-index (reduce (fn add-items [index [pk item]]
                             (let [k (key-fn item)
                                   pks (get index k #{})]
                               (assoc! index k (conj pks pk))))
                           (transient index)
                           added)]
      (->IndexMulti (persistent! new-index) index-def)))

  (-on-remove [this removed]
    (let [{:keys [index index-def]} this
          {:keys [key-fn]} index-def
          new-index (reduce (fn remove-items [index [pk item]]
                             (let [k (key-fn item)
                                   pks (get index k #{})]
                               (assoc! index k (disj pks pk))))
                           (transient index)
                           removed)]
      (->IndexMulti (persistent! new-index) index-def)))

  p/IQueryRefs
  (-query-refs [this q]
    (query-refs this q)))

(s/def :compound.index-defs/multi
  (s/keys :req-un [:compound.indexes/key-fn
                   :compound.indexes/id
                   :compound.indexes/type]))

(defmethod p/make-index :compound.indexes/multi
  [index-def]
  (s/assert :compound.index-defs/multi index-def)
  (->IndexMulti {} index-def))
