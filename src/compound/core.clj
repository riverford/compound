(ns compound.core
  (:require [compound.protocols :as p]
            [compound.index-unique :as iu]
            [compound.index-multi :as im]
            [compound.index-primary :as ip]))

(defrecord Compound [indexes primary-index-id])

(defmulti query (fn [index [op & args]] op))

(defmethod query :index
  [compound q]
  (let [[op index-id sub-q] q
        {:keys [indexes primary-index-id]} compound
        index (get indexes index-id)]
    (cond
      (satisfies? p/IQuery index) (p/-query index sub-q)
      (satisfies? p/IQueryRefs index) (let [ks (p/-query-refs index sub-q)
                                           index-primary (get indexes primary-index-id)]
                                       (map #(p/-get-by-key index-primary %) ks))
      :else (throw (ex-info "Index must satisfy Query or QueryIndirect to be queried")))))

(extend-type Compound
  p/IIndexPrimary ;; Compound acts like a primary index, but also updates the secondary indexes
  (-get-by-key [compound k]
    (let [{:keys [indexes primary-index-id]} compound
          primary-index (get indexes primary-index-id)]
      (p/-get-by-key primary-index k)))

  (-add-items [compound items]
    (let [{:keys [indexes primary-index-id]} compound
          primary-index (get indexes primary-index-id)
          secondary-indexes (dissoc indexes primary-index-id)]
      (let [[new-primary-index added] (p/-add-items primary-index items)
            new-secondary-indexes (reduce-kv (fn [m k v]
                                               (assoc! m k (p/-on-add v added)))
                                             (transient {}) secondary-indexes)]
        (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id))))

  (-remove-by-keys [compound ks]
    (let [{:keys [indexes primary-index-id]} compound
          primary-index (get indexes primary-index-id)
          [new-primary-index removed] (p/-remove-by-keys primary-index ks)
          secondary-indexes (dissoc indexes primary-index-id)
          new-secondary-indexes (reduce-kv (fn [m k v]
                                             (assoc! m k (p/-on-remove v removed)))
                                           (transient {}) secondary-indexes)]
      (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id)))

  p/IQuery
  (-query [compound q] (query compound q)))

(p/make-index {:type :compound.indexes/primary
               :key-fn :a
               :id :a})

(ip/->IndexPrimary {} {})
