(ns compound.core
  (:require [compound.indexes :as ci]))

(defprotocol ICompound
  (-query [c index-id q])
  (-add [c items])
  (-remove [c index-id q]))

(defrecord Compound [indexes-by-id primary-index-id])

(extend-type Compound
  ICompound
  (-add [c items] (let [{:keys [indexes-by-id primary-index-id]} c
                        primary-index (get indexes-by-id primary-index-id)
                        secondary-indexes (dissoc indexes-by-id primary-index-id)]
                    (let [[new-primary-index added] (ci/-add primary-index items)
                          new-secondary-indexes (reduce-kv (fn [m k v]
                                                             (assoc! m k (ci/-on-add v added)))
                                                           (transient {}) secondary-indexes)]
                      (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id))))

  (-remove [c index-id q] (let [{:keys [indexes-by-id primary-index-id]} c
                                primary-index (get indexes-by-id primary-index-id)
                                secondary-indexes (dissoc indexes-by-id primary-index-id)
                                pks (if (= primary-index-id index-id)
                                      q
                                      (-query c index-id q))
                                _ (println pks)
                                [new-primary-index removed] (ci/-remove primary-index pks)
                                _ (println removed)
                                new-secondary-indexes (reduce-kv (fn [m k v]
                                                                   (assoc! m k (ci/-on-remove v removed)))
                                                                 (transient {}) secondary-indexes)]
                            (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id)))
§
  (-query [c index-id q] (let [{:keys [indexes-by-id primary-index-id]} c
                               primary-index (get indexes-by-id primary-index-id)]
                           (if (= primary-index-id index-id)
                             (ci/-query-primary primary-index q)
                             (let [secondary-index (get indexes-by-id index-id)
                                   pks (ci/-query-secondary secondary-index q)]
                               (into #{} (remove nil?) (ci/-query-primary primary-index pks)))))))

;; public api
(defn compound
  "Creates a new compound with the given index definitions"
  [index-defs]
  (let [[primary-index-def & more] (filter #(= (:compound.index/type %) :compound.index.types/primary) index-defs)
        {primary-index-id :compound.index/id} primary-index-def]
    (assert (nil? more) "Compound must not define more than one primary index")
    (assert (not (nil? primary-index-def)) "Compound must define a primary index")
    (let [indexes-by-id (reduce (fn [m index-def]
                                  (let [{:keys [:compound.index/id]} index-def]
                                    (assoc m id (ci/make-index index-def))))
                                {}
                                index-defs)]
      (->Compound indexes-by-id primary-index-id))))

(defn query [compound index q]
  (-query compound index q))

(defn add-items [compound items]
  (-add compound items))

(defn remove-by-query [compound index q]
  (-remove compound index q))

(defn update-by-query [compound index q f]
  (let [changed-items (map f (query compound index q))]
    (-> (remove-by-query compound index q)
        (add-items changed-items))))

(-> (compound [#:compound.index{:key-fn :a
                                :type :compound.index.types/primary
                                :id :a}
               #:compound.index{:key-fn :b
                                :type :compound.index.types/unique
                                :id :b}
               #:compound.index{:key-fn :c
                                :type :compound.index.types/multi
                                :id :c}
               #:compound.index{:key-fn (juxt :a :b)
                                :type :compound.index.types/multi
                                :id :compound-index}])
    (add-items [{:a 1 :b 2}
                {:a 2 :b 3 :c 4}
                {:a 3 :b 4 :c 5}
                {:a 4 :b 5 :c 6}
                {:a 5 :b 6 :c 5}])
    (remove-by-query :c [3 4])
    (query :c [4 5 ]))

;; remove isn't working
