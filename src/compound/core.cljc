(ns compound.core
  (:require [clojure.set :as set]
            [compound.spec :as cs]
            [clojure.spec.alpha :as s]))

(defmulti secondary-index-def->behaviour :compound.secondary-index/type)

(def secondary-index-def->behaviour-memoized secondary-index-def->behaviour ;(memoize secondary-index-def->behaviour)
  )
(defn secondary-indexes-by-id [compound]
  (get compound :compound/secondary-indexes-by-id))

(defn secondary-index [compound id]
  (get-in compound [:compound/secondary-indexes-by-id id]))

(defn secondary-index-defs-by-id [compound]
  (get compound :compound/secondary-index-defs-by-id))

(defn secondary-index-def [compound id]
  (get (secondary-index-defs-by-id compound) id))

(defn secondary-index-behaviour [compound id]
  (secondary-index-def->behaviour-memoized (secondary-index-def compound id)))

(defn primary-index-def [compound]
  (get compound :compound/primary-index-def))

(defn primary-index-id [compound]
  (get (primary-index-def compound) :compound.primary-index/id))

(defn primary-index [compound]
  (get compound :compound/primary-index))

(defn index-defs-by-id [compound]
  (assoc (secondary-index-defs-by-id compound) (primary-index-id compound) (primary-index-def compound)))

(defn index-def [compound id]
  (get (index-defs-by-id compound) id))

(defn indexes-by-id [compound]
  (assoc (secondary-indexes-by-id compound) (primary-index-id compound) (primary-index compound)))

(defn index [compound id]
  (get (indexes-by-id compound) id))

(s/fdef add-items
        :args (s/cat :compound :compound/compound
                     :items (s/coll-of any?))
        :ret :compound/compound)

(defn add-items [compound items]
  (let [{:compound.primary-index/keys [id key-fn conflict-behaviour]} (primary-index-def compound)
        [new-primary-index added removed] (reduce (fn add-items-to-primary-index
                                                    [[index added removed] item]
                                                    (let [k (key-fn item)
                                                          existing (get index k)]
                                                      (cond
                                                        (and existing (= conflict-behaviour :compound.primary-index.conflict-behaviours/throw))
                                                        (throw (ex-info "Duplicate key " {:k k, :index-def (primary-index-def compound), :item item}))

                                                        (and existing (= conflict-behaviour :compound.primary-index.conflict-behaviours/replace))
                                                        [(assoc! index k item)
                                                         (-> (disj! added existing)
                                                             (conj! item))
                                                         (conj! removed existing)]

                                                        (and existing (or (= conflict-behaviour :compound.primary-index.conflict-behaviours/merge)))
                                                        (let [new-item (merge existing item)]
                                                          [(assoc! index k new-item)
                                                           (-> (disj! added existing)
                                                               (conj! new-item))
                                                           (conj! removed existing)])

                                                        (and existing (= (first conflict-behaviour) :compound.primary-index.conflict-behaviours/merge-using))
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
        new-secondary-indexes-by-id (reduce-kv (fn update-indexes [indexes index-id index]
                                                 (let [{:compound.secondary-index.behaviour/keys [add remove]} (secondary-index-behaviour compound index-id)]
                                                   (assoc! indexes index-id (-> (remove index removed)
                                                                                (add added)))))
                                               (transient {})
                                               (secondary-indexes-by-id compound))]
    (assoc compound
           :compound/primary-index (persistent! new-primary-index)
           :compound/secondary-indexes-by-id (persistent! new-secondary-indexes-by-id))))

(s/fdef remove-keys
        :args (s/cat :compound :compound/compound
                     :keys (s/coll-of any?))
        :ret :compound/compound)

(defn remove-keys [compound ks]
  (let [{:compound.primary-index/keys [id key-fn]} (primary-index-def compound)
        [new-primary-index removed] (reduce (fn remove-items-from-primary-index
                                              [[index removed] k]
                                              (let [item (get index k)]
                                                (if item
                                                  [(dissoc! index k)
                                                   (conj! removed item)]
                                                  [(dissoc! index k)
                                                   removed])))
                                            [(transient (primary-index compound)) (transient #{})]
                                            ks)
        removed (persistent! removed)
        new-secondary-indexes-by-id (reduce-kv (fn [m index-id index]
                                                 (let [{:compound.secondary-index.behaviour/keys [remove]} (secondary-index-behaviour compound index-id)]
                                                   (assoc! m index-id (remove index removed))))
                                               (transient {})
                                               (secondary-indexes-by-id compound))]
    (assoc compound
           :compound/primary-index (persistent! new-primary-index)
           :compound/secondary-indexes-by-id (persistent! new-secondary-indexes-by-id))))

(s/fdef update-item
        :args (s/cat :compound :compound/compound
                     :key any?
                     :f ifn?
                     :args (s/* any?))
        :ret :compound/compound)

(defn update-item [compound k f & args]
  (let [new-item (apply f (get (primary-index compound) k) args)]
    (-> compound
        (remove-keys [k])
        (add-items [new-item]))))

(s/fdef empty-compound
        :args (s/cat :primary-index-def :compound/primary-index-def
                     :secondary-index-def (s/* :compound/secondary-index-def))
        :ret :compound/compound)

(defn empty-compound [primary-index-def & secondary-index-defs]
  ;; index index-defs and initial index values by id
  (let [{:keys [secondary-index-defs-by-id secondary-indexes-by-id]} (reduce (fn make-secondary-indexes [m index-def]
                                                                               (let [{:compound.secondary-index/keys [id]} index-def
                                                                                     {:compound.secondary-index.behaviour/keys [empty]} (secondary-index-def->behaviour-memoized index-def)]
                                                                                 (-> (assoc-in m [:secondary-index-defs-by-id id] index-def)
                                                                                     (assoc-in [:secondary-indexes-by-id id] empty))))
                                                                             {}
                                                                             secondary-index-defs)]
    #:compound{:primary-index-def primary-index-def
               :primary-index {}
               :secondary-index-defs-by-id secondary-index-defs-by-id
               :secondary-indexes-by-id secondary-indexes-by-id}))

(defn clear [compound]
  (assoc compound
         :compound/primary-index {}
         :compound/secondary-indexes-by-id (reduce-kv (fn clear-secondary-indexes [indexes index-id index-def]
                                                        (let [{:compound.secondary-indedx.behaviour/keys [empty]} (secondary-index-def->behaviour-memoized index-def)]
                                                          (assoc indexes index-id empty)))
                                                      {}
                                                      (secondary-indexes-by-id compound))))

