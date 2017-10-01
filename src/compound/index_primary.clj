(ns compound.index-primary
  (:require [compound.protocols :as p]
            [clojure.spec.alpha :as s]))

(defrecord IndexPrimary [index index-def])

(extend-type IndexPrimary
  p/IIndexPrimary
  (-get-by-key [this k]
    (let [{:keys [index]} this]
      (get index k)))

  (-add-items [this items]
    (let [{:keys [index index-def]} this
          {:keys [key-fn]} index-def
          [new-index added] (reduce (fn add-items [[index added] item]
                                     (let [k (key-fn item)]
                                       [(assoc! index k item)
                                        (assoc! added k item)]))
                                   [(transient index) (transient {})]
                                   items)]
      [(->IndexPrimary (persistent! new-index) index-def) (persistent! added)]))

  (-remove-by-keys [this ks]
    (let [{:keys [index index-def]} this
          [new-index removed] (reduce (fn remove-items [[index removed] k]
                                       (let [item (get index k)]
                                         [(dissoc! index k)
                                          (assoc! removed k item)]))
                                     [(transient index) (transient {})]
                                     ks)]
      [(->IndexPrimary (persistent! new-index) index-def) (persistent! removed)])))

(s/def :compound.index-defs/primary
  (s/keys :req-un [:compound.indexes/key-fn
                   :compound.indexes/id
                   :compound.indexes/type]))

(defmethod p/make-index :compound.indexes/primary
  [index-def]
  (s/assert :compound.index-defs/primary index-def)
  (println index-def)
  (->IndexPrimary {} index-def))


(comment
  (def idx (IndexPrimary. {} {:id :a :key-fn :a}))
  (let [[idx _] (p/-command (first (p/-command idx [:add-items [{:a 1} {:b 2}]])) [:add-items [{:a 3} {:a 4} {:a 5}]])]
    (p/-query idx [:keys [3 4 5]])))
