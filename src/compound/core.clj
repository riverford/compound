(ns compound.core
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]))

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

(defmulti index-def->behaviour :compound.index/type)

(defmulti index-def->spec :compound.index/type)

(def index-def->behaviour-memoized (memoize index-def->behaviour))

(defmethod index-def->spec :compound.index.types/primary
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type :compound.index/conflict-behaviour]))

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
                                             (assoc! indexes index-id (-> (add index added)
                                                                          (remove removed)))))
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
