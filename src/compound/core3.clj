(ns compound.core3
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(defprotocol IndexSecondary
  (on-add [this items])
  (on-remove [this items]))

(defmulti index-def->index :compound.index/type)

(defmulti index-def->spec :compound.index/type)

(defn indexes [compound]
  (get compound :compound/indexes))

(defn index [compound id]
  (get-in compound [:compound/indexes id]))

(defn primary-index-id [compound]
  (get compound :compound/primary-index-id))

(defn index-defs [compound]
  (get compound :compound/index-defs))

(defn index-def [compound id]
  (get (index-defs compound) id))

(defn primary-index-def [compound]
  (index-def compound (primary-index-id compound)))

(defn primary-index [compound]
  (index compound (primary-index-id compound)))

(defn secondary-indexes [compound]
  (dissoc (indexes compound) (primary-index-id compound)))

;; we don't do updates
;; we can just do add / remove
(defn add [compound items]
  (let [{:compound.index/keys [id key-fn conflict-behaviour]} (primary-index-def compound)
        [new-primary-index added removed] (reduce (fn add-items-to-primary-index
                                                    [[index added removed] item]
                                                    (let [k (key-fn item)
                                                          existing (get index k)]
                                                      (cond
                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/throw)
                                                             (throw (ex-info "Key conflict: " {:k k, :index id})))
                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/upsert)
                                                             [(assoc! index k item)
                                                              (conj! added item)
                                                              (conj! removed existing)])
                                                        :else [(assoc! index k item)
                                                               (conj! added item)
                                                               removed])))
                                                  [(transient (primary-index compound)) (transient #{}) (transient #{})]
                                                  items)
        new-secondary-indexes (reduce-kv (fn update-secondary-indexes [m k v]
                                           (assoc! m k (-> (-on-add v (persistent! added))
                                                           (-on-remove removed (persistent! removed)))))
                                         (transient {}) (secondary-indexes compound))
        new-indexes (assoc! new-secondary-indexes (primary-index-id compound) (persistent! new-primary-index))]
    (assoc compound :indexes (persistent! new-indexes))))

(defn remove [compound ks]
  (let [primary-index (primary-index compound)
        [new-primary-index removed] (reduce (fn add-items-to-primary-index
                                            [[index added] item]
                                            (let [k (key-fn item)]
                                              [(assoc! index k item)
                                               (assoc! added k item)]))
                                          [(transient (primary-index compound)) (transient {})]
                                          items)
        secondary-indexes (dissoc indexes primary-index-id)
        new-secondary-indexes (reduce-kv (fn [m k v]
                                           (assoc! m k (p/-on-remove v removed)))
                                         (transient {}) secondary-indexes)]
    (->Compound (persistent! (assoc! new-secondary-indexes primary-index-id new-primary-index)) primary-index-id)))

;;make this a multi spec

(s/def :compound/index-def
  (s/multi-spec index-def->spec :compound.index/type))

(defmethod index-def->spec :compound.index.types/primary
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(s/def :compound/index-defs
  (s/and
   (s/coll-of :compound/index-def)
   ;; only one primary index allowed
   #(= 1 (count (filter (comp #{:compound.index.types/primary} :compound.index/type) %)))))

(defmethod index-def->index :compound.index/unique
  [index-def]
  (reify IndexSecondary
    (-on-add [this added])
    (-on-remove [this removed])))

(s/def ::compound
  (s/keys :req [:compound/indexes :compound/index-defs :compound/primary-index-id]))



;; new thought, don't need to differentiate primary index quite so much
;; instead we can treat it as the same, as we are no longer storing references, but values

(defn empty-compound [index-defs]
  (s/assert :compound/index-defs index-defs)
  (let [{:keys [index-defs-by-id index-defs-by-type]} (reduce (fn [m index-def]
                                                                (let [{:compound.index/keys [id type]} index-def]
                                                                  (-> (assoc-in m [:index-defs-by-id id] index-def)
                                                                      (update-in [:index-defs-by-type type] (fnil conj #{}) index-def))))
                                                              {}
                                                              index-defs)
        primary-index-def (first (get index-defs-by-type :compound.index.types/primary))
        primary-index-id (get primary-index-def :compound.index/id)
        secondary-index-defs (dissoc index-defs-by-id primary-index-id)]
    {:compound/indexes (reduce-kv (fn make-indexes [indexes id index-def]
                           (assoc indexes id (index-def->index index-def)))
                         {primary-index-id {}}
                         secondary-index-defs)
     :compound/index-defs index-defs-by-id
     :compound/primary-index-id primary-index-id}))

(-> (empty-compound #{{:compound.index/id :id
                       :compound.index/conflict-behaviour :compound.conflict-behaviour/upsert
                       :compound.index/key-fn :id
                       :compound.index/type :compound.index.types/primary}
                      {:compound.index/id :id
                       :compound.index/key-fn :b
                       :compound.index/type :compound.index.types/unique}})

    (add [{:id 1 :b 1} {:id 2 :b 2} {:id 3 :b 3}]))
