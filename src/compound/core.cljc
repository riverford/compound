(ns compound.core
  (:require [clojure.set :as set]
            [compound.spec :as cs]
            [clojure.spec.alpha :as s]))

(defn indexes [compound]
  (get compound :compound/indexes))

(defn index [compound id]
  (get-in compound [:compound/indexes id]))

(defn primary-index-id [compound]
  (get compound :compound/primary-index-id))

(defn index-defs-by-id [compound]
  (get compound :compound/index-defs-by-id))

(defn index-behaviours-by-id [compound]
  (get compound :compound/index-behaviours-by-id))

(defn index-behaviour [compound id]
  (get (index-behaviours-by-id compound) id))

(defn index-def [compound id]
  (get (index-defs-by-id compound) id))

(defn primary-index-def [compound]
  (index-def compound (primary-index-id compound)))

(defn primary-index [compound]
  (index compound (primary-index-id compound)))

(defn secondary-indexes [compound]
  (dissoc (indexes compound) (primary-index-id compound)))

(defmulti index-def->behaviour :compound.index/type)

(defmethod cs/index-def-spec :compound.index.types/primary
  [_]
  (s/keys :req [:compound.index/key-fn :compound.index/id :compound.index/type :compound.index/conflict-behaviour]))

(defn add-items [compound items]
  (s/assert :compound/compound compound)
  (let [{:compound.index/keys [id key-fn conflict-behaviour]} (primary-index-def compound)
        [new-primary-index added removed] (reduce (fn add-items-to-primary-index
                                                    [[index added removed] item]
                                                    (let [k (key-fn item)
                                                          existing (get index k)]
                                                      (cond
                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/throw))
                                                        (throw (ex-info "Duplicate key " {:k k, :index id, :item item}))

                                                        (and existing (= conflict-behaviour :compound.conflict-behaviours/replace))
                                                        [(assoc! index k item)
                                                         (-> (disj! added existing)
                                                             (conj! item))
                                                         (conj! removed existing)]
                                                        
                                                        (and existing (or (= conflict-behaviour :compound.conflict-behaviours/merge)))
                                                        (let [new-item (merge existing item)]
                                                          [(assoc! index k new-item)
                                                           (-> (disj! added existing)
                                                               (conj! new-item))
                                                           (conj! removed existing)])
                                                        
                                                        (and existing (= (first conflict-behaviour) :compound.conflict-behaviours/merge-using))
                                                        (let [[_ merge-fn] conflict-behaviour
                                                              new-item (merge-fn existing item)]
                                                          [(assoc! index k new-item)
                                                           (-> (disj! added existing)
                                                               (conj! new-item))
                                                           (conj! removed existing)])
                                                        
                                                        :else [(assoc! index k item)
                                                               (conj! added item)
                                                               removed])))
                                                  [(transient (primary-index compound)) (transient #{}) (transient #{})]
                                                  items)
        [added removed] [(persistent! added) (persistent! removed)]
        new-secondary-indexes (reduce-kv (fn update-secondary-indexes [indexes index-id index]
                                           (let [{:compound.index.behaviour/keys [add remove]} (index-behaviour compound index-id)]
                                             (assoc! indexes index-id (-> (remove index removed)
                                                                          (add added)))))
                                         (transient {})
                                         (secondary-indexes compound))
        new-indexes (assoc! new-secondary-indexes (primary-index-id compound) (persistent! new-primary-index))]
    (assoc compound :compound/indexes (persistent! new-indexes))))

(defn remove-keys [compound ks]
  (s/assert :compound/compound compound)
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
                                           (let [{:compound.index.behaviour/keys [remove]} (index-behaviour compound index-id)]
                                             (assoc! m index-id (remove index removed))))
                                         (transient {})
                                         (secondary-indexes compound))
        new-indexes (assoc! new-secondary-indexes (primary-index-id compound) (persistent! new-primary-index))]
    (assoc compound :compound/indexes (persistent! new-indexes))))

(defn update-item [compound k f & args]
  (s/assert :compound/compound compound)
  (let [new-item (apply f (get (primary-index compound) k) args)]
    (-> compound
        (remove-keys [k])
        (add-items [new-item]))))

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
        secondary-index-defs (dissoc index-defs-by-id primary-index-id)
        index-behaviours-by-id (reduce-kv (fn make-behaviours [behaviours index-id index-def]
                                            (assoc behaviours index-id (index-def->behaviour index-def))) {} secondary-index-defs)]
    #:compound{:index-defs-by-id index-defs-by-id
               :index-behaviours-by-id index-behaviours-by-id
               :indexes (reduce-kv (fn make-indexes [indexes index-id index-def]
                                     (let [{:compound.index.behaviour/keys [empty]} (get index-behaviours-by-id index-id)]
                                      (assoc indexes index-id empty)))
                                  {primary-index-id {}}
                                  secondary-index-defs)
               :primary-index-id primary-index-id}))



(defn clear [compound]
  (let [primary-index-id (primary-index-id compound)
        index-defs-by-id (dissoc (index-defs-by-id compound) primary-index-id)
        index-behaviours-by-id (index-behaviours-by-id compound)]
    (assoc compound :compound/indexes
           (reduce-kv (fn make-indexes [indexes index-id index-def]
                        (let [{:compound.index.behaviour/keys [empty]} (get index-behaviours-by-id index-id)]
                          (assoc indexes index-id empty)))
                      {primary-index-id {}}
                      index-defs-by-id))))
