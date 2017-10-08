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
      (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id))))

(defn make-compound
  [index-defs]
  (let [[primary-index-def & more] (filter #(= (:compound.index/type %) :compound.index.types/primary) index-defs)
        {primary-index-id :compound.index/id} primary-index-def]
    (assert (nil? more) "Compound must not define more than one primary index")
    (assert (not (nil? primary-index-def)) "Compound must define a primary index")
    (let [indexes-by-id (reduce (fn [m index-def]
                                  (let [{:keys [:compound.index/id]} index-def]
                                    (assoc m id (p/make-index index-def))))
                                {}
                                index-defs)]
      (->Compound indexes-by-id primary-index-id))))

;; public api

(defn query [compound index q]
  (-query compound index q))

(defn add-items [compound items]
  (-add-items compound items))

(defn remove-by-keys [compound index ks]
  (-remove-by-keys compound index ks))

(defn remove-by-query [compound index q]
  (let [refs ()])
  (-remove-by-keys compound index ks))

(defn update-by-query [compound index q f]
  (let [changed-items (map f (query compound index q))]
    (-> (remove-by-query compound index q)
        (add-items changed-items))))
(make-ompound)
