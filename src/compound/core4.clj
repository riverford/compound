
(ns compound.core4
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(defmulti index-def->behaviour :compound.index/type)

(def index-def->behaviour-memoized (memoize index-def->behaviour))

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
                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/throw))
                                                        (throw (ex-info "Duplicate key " {:k k, :index id, :item item}))

                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/upsert))
                                                        [(assoc! index k item)
                                                         (conj! added item)
                                                         (conj! removed existing)]

                                                        :else [(assoc! index k item)
                                                               (conj! added item)
                                                               removed])))
                                                  [(transient (primary-index compound)) (transient #{}) (transient #{})]
                                                  items)
        [added removed] [(persistent! added) (persistent! removed)]
        new-secondary-indexes (reduce-kv (fn update-secondary-indexes [indexes index-id index]
                                           (let [{:compound.index.behaviour/keys [add remove]} (index-def->behaviour-memoized (index-def compound index-id))]
                                             (assoc! indexes index-id (-> (remove index removed)
                                                                          (add added)))))
                                         (transient {})
                                         (secondary-indexes compound))
        new-indexes (assoc! new-secondary-indexes (primary-index-id compound) (persistent! new-primary-index))]
    (assoc compound :compound/indexes (persistent! new-indexes))))

(defn remove [compound ks]
  (let [{:compound.index/keys [id key-fn]} (primary-index-def compound)
        [new-primary-index removed] (reduce (fn remove-items-from-primary-index
                                              [[index removed] k]
                                              (let [item (get index k)]
                                                [(dissoc! index k)
                                                 (conj! removed item)]))
                                            [(transient (primary-index compound)) (transient #{})]
                                            ks)
        removed (persistent! removed)
        new-secondary-indexes (reduce-kv (fn [m index-id index]
                                           (let [{:compound.index.behaviour/keys [remove]} (index-def->behaviour-memoized (index-def compound index-id))]
                                             (assoc! m index-id (remove index removed))))
                                         (transient {})
                                         (secondary-indexes compound))
        new-indexes (assoc! new-secondary-indexes (primary-index-id compound) (persistent! new-primary-index))]
    (assoc compound :compound/indexes (persistent! new-indexes))))

;;make this a multi spec

(s/def :compound/index-def
  (s/multi-spec index-def->spec :compound.index/type))

(s/def :compound.index/conflict-behaviour
  #{:compound.conflict-behaviours/upsert :compound.conflict-behaviours/throw})

(defmethod index-def->spec :compound.index.types/primary
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type :compound.index/conflict-behaviour]))

(defmethod index-def->spec :compound.index.types/multi
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(defmethod index-def->spec :compound.index.types/unique
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type]))

(s/def :compound/index-defs
  (s/and
   (s/coll-of :compound/index-def)
   ;; only one primary index allowed
   #(= 1 (count (filter (comp #{:compound.index.types/primary} :compound.index/type) %)))))

(s/def :compound.index.behaviour/empty any?)
(s/def :compound.index.behaviour/add fn?)
(s/def :compound.index.behaviour/remove fn?)

(s/def :compound.index/behaviour
  (s/keys :req [:compound.index.behaviour/empty :compound.index.behaviour/add :compound.index.behaviour/remove]))

(defmethod index-def->behaviour :compound.index.types/unique
  [index-def]
  (let [{:compound.index/keys [id key-fn]} index-def]
    {:compound.index.behaviour/empty {}
     :compound.index.behaviour/add (fn [index added]
                                     (let [new-index (reduce (fn add-items [index item]
                                                               (let [k (key-fn item)
                                                                     existing-item (get index k)]
                                                                 (if existing-item
                                                                   (throw (ex-info "Duplicate key" {:key k, :index id, :item item}))
                                                                   (assoc! index k item))))
                                                             (transient index)
                                                             added)]
                                       (persistent! new-index)))
     :compound.index.behaviour/remove (fn [index removed]
                                        (let [new-index (reduce (fn remove-items [index item]
                                                                  (let [k (key-fn item)]
                                                                    (dissoc! index k)))
                                                                (transient index)
                                                                removed)]
                                          (persistent! new-index)))}))

(defmethod index-def->behaviour :compound.index.types/multi
  [index-def]
  (let [{:compound.index/keys [key-fn]} index-def]
    {:compound.index.behaviour/empty {}
     :compound.index.behaviour/add (fn [index added]
                                     (let [new-index (reduce (fn add-items [index item]
                                                               (let [k (key-fn item)
                                                                     existing-items (get index k #{})]

                                                                 (assoc! index k (conj existing-items item))))
                                                             (transient index)
                                                             added)]
                                       (persistent! new-index)))
     :compound.index.behaviour/remove (fn [index removed]
                                        (let [new-index (reduce (fn remove-items [index item]
                                                                  (let [k (key-fn item)
                                                                        existing-items (get index k #{})]
                                                                    (assoc! index k (disj existing-items item))))
                                                                (transient index)
                                                                removed)]
                                          (persistent! new-index)))}))

(s/def ::compound
  (s/keys :req [:compound/indexes :compound/index-defs :compound/primary-index-id]))

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
    {:compound/index-defs index-defs-by-id
     :compound/indexes (reduce-kv (fn make-indexes [indexes index-id index-def]
                                    (let [{:compound.index.behaviour/keys [empty]} (index-def->behaviour-memoized index-def)]
                                      (assoc indexes index-id empty)))
                                  {primary-index-id {}}
                                  secondary-index-defs)
     :compound/primary-index-id primary-index-id}))

(index-def (empty-compound #{{:compound.index/id :id
                              :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                              :compound.index/key-fn :id
                              :compound.index/type :compound.index.types/primary}
                             {:compound.index/id :b
                              :compound.index/key-fn :b
                              :compound.index/type :compound.index.types/unique}}) :b)

(-> (empty-compound #{{:compound.index/id :id
                       :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                       :compound.index/key-fn :id
                       :compound.index/type :compound.index.types/primary}
                      {:compound.index/id :a
                       :compound.index/key-fn :a
                       :compound.index/type :compound.index.types/unique}})
    (add [{:id 1 :a 3} {:id 2 :a 2} {:id 4 :a 4}]))

(-> (empty-compound #{{:compound.index/id :id
                       :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                       :compound.index/key-fn :id
                       :compound.index/type :compound.index.types/primary}
                      {:compound.index/id :b
                       :compound.index/key-fn :b
                       :compound.index/type :compound.index.types/unique}
                      {:compound.index/id :c
                       :compound.index/key-fn :c
                       :compound.index/type :compound.index.types/multi}})

    (add [{:id 1 :b 1 :c 3} {:id 2 :c 4 :b 2} {:id 3 :c 3 :b 3}])
    (remove [1 ]))
