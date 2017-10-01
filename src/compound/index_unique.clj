(ns compound.index-unique
  (:require [compound.protocols :as p]
            [clojure.spec.alpha :as s]))

(defrecord IndexUnique [index index-def])

;; spec for q
;; [:get-by-key ks]
;; [:get-by-ket k]

(defmulti query-refs (fn [this [op & args]] op))

(defmethod query-refs :get-by-key
  [this q]
  (let [[op k] q
        {:keys [index]} this]
    (get index k)))

(defmethod query-refs :get-by-keys
  [this primary q]
  (let [{:keys [index]} this
        [op ks] q]
    (map #(get index %) ks)))

(extend-type IndexUnique
  p/IIndexSecondary
  (-on-add [this added]
    (let [{:keys [index index-def]} this
          {:keys [key-fn]} index-def
          new-index (reduce (fn add-items [index [pk item]]
                             (let [k (key-fn item)]
                               (assoc! index k pk)))
                           (transient index)
                           added)]
      (->IndexUnique (persistent! new-index) index-def)))

  (-on-remove [this removed]
    (let [{:keys [index index-def]} this
          new-index (reduce (fn remove-items [index [pk item]]
                             (dissoc! index pk))
                           (transient index)
                           removed)]
      (->IndexUnique (persistent! new-index) index-def)))

  p/IQueryRefs
  (-query-refs [this q]
    (query-refs this q)))

(s/def :compound.indexes/key-fn fn?)
(s/def :compound.indexes/id keyword?)
(s/def :compound.indexes/type keyword?)

(s/def :compound.index-defs/unique
  (s/keys :req-un [:compound.indexes/key-fn
                   :compound.indexes/id
                   :compound.indexes/type]))

(defmethod p/make-index :compound.indexes/unique
  [index-def]
  (s/assert :compound.index-defs/unique index-def)
  (->IndexUnique {} index-def))
