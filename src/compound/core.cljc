(ns compound.core
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [compound.custom-key :as cu]
            [compound.secondary-indexes :as csi]
            [compound.secondary-indexes.many-to-many]
            [compound.secondary-indexes.many-to-one]
            [compound.secondary-indexes.one-to-many]
            [compound.secondary-indexes.one-to-many-composite]
            [compound.secondary-indexes.one-to-one]
            [compound.secondary-indexes.one-to-one-composite]))

;; ------------------------------
;;   Primary index spec
;; ------------------------------

(s/def ::id any?)
(s/def ::key (s/or :key keyword? :path (s/coll-of keyword? :kind vector)))
(s/def ::custom-key keyword?)
(s/def ::conflict-behaviour keyword?)

;; ------------------------------
;;   Compound spec
;; ------------------------------

(s/def ::primary-index-def
  (s/keys :req-un [(or ::key ::custom-key)]
          :opt-un [::id ::conflict-behaviour]))

(s/def ::primary-index map?)

(s/def ::secondary-index-defs-by-id
  (s/map-of ::id ::csi/secondary-index-def))

(s/def ::secondary-indexes-by-id
  (s/map-of ::id any?))

(s/def ::compound
  (s/keys :req-un [::primary-index-def
                   ::primary-index
                   ::secondary-index-defs-by-id
                   ::secondary-indexes-by-id]))

(s/def ::diff.update.source any?)
(s/def ::diff.update.target any?)
(s/def ::diff.update (s/keys :req-un [::diff.update.source ::diff.update.target]))
(s/def ::diff.updates (s/coll-of ::diff.update))
(s/def ::diff.inserts (s/coll-of any?))
(s/def ::diff.deletes (s/coll-of any?))

(s/def ::diff
  (s/keys :opt-un [::diff.inserts
                   ::diff.updates
                   ::diff.deletes]))

;; ------------------------------
;;   Helper functions
;; ------------------------------

(defn secondary-indexes-by-id [compound]
  (get compound :secondary-indexes-by-id))

(defn secondary-index [compound id]
  (get-in compound [:secondary-indexes-by-id id]))

(defn secondary-index-defs-by-id [compound]
  (get compound :secondary-index-defs-by-id))

(defn secondary-index-def [compound id]
  (get (secondary-index-defs-by-id compound) id))

(defn primary-index-def [compound]
  (get compound :primary-index-def))

(defn primary-index-id [compound]
  (let [index-def (primary-index-def compound)]
    (or (:id index-def)
        (:custom-key index-def)
        (:key index-def))))

(defn primary-index [compound]
  (get compound :primary-index))

(defn primary-index-fn [compound]
  (let [{:keys [key custom-key]} (primary-index-def compound)]
    (cond
      (keyword? key) key
      (vector? key) (fn [x] (get-in x key))
      custom-key (partial cu/custom-key-fn custom-key)
      :else (throw (ex-info "Unsupported key type" {:key key})))))

(defn index-defs-by-id [compound]
  (assoc (secondary-index-defs-by-id compound) (primary-index-id compound) (primary-index-def compound)))

(defn index-def [compound id]
  (get (index-defs-by-id compound) id))

(defn indexes-by-id [compound]
  (assoc (secondary-indexes-by-id compound) (primary-index-id compound) (primary-index compound)))

(defn index [compound id]
  (get (indexes-by-id compound) id))

;; ------------------------------
;;   Custom conflict behaviour
;; ------------------------------

(defmulti on-conflict-fn
  (fn [index-def existing-item new-item] (get index-def :on-conflict)))

(defmethod on-conflict-fn :default
  [index-def existing-item new-item]
  (throw (ex-info (str "Implementation of conflict for " (get index-def :on-conflict)  " not found") {:index-def index-def, :existing-item existing-item, :new-item new-item})))

(defmethod on-conflict-fn :compound/replace
  [_ existing-item new-item]
  new-item)

(defmethod on-conflict-fn :compound/throw
  [index-def existing-item new-item]
  (let [{:keys [key custom-key]} index-def
        key-fn (or key (partial cu/custom-key-fn custom-key))]
    (throw (ex-info (str "Duplicate key " (key-fn new-item) " in primary index") {:existing-item existing-item, :new-item new-item}))))

(defmethod on-conflict-fn :compound/merge
  [_ existing-item new-item]
  (merge existing-item new-item))

;; ------------------------------
;; Core implementation
;; ------------------------------

(s/fdef add-items
        :args (s/cat :compound ::compound
                     :items (s/coll-of any?))
        :ret ::compound)

(defn add-items
  "Adds the given items to the compound, cascading the additions to the secondary indexes.
   If the key exists, handle the conflict as specified in the primary index definition. "
  [compound items]
  (let [key-fn (primary-index-fn compound)
        [new-primary-index added removed] (reduce (fn add-items-to-primary-index
                                                    [[index added removed] item]
                                                    (let [k (key-fn item)
                                                          existing (get index k)]
                                                      (if existing
                                                        (let [new-item (on-conflict-fn (primary-index-def compound) existing item)]
                                                          [(assoc! index k new-item)
                                                           (-> (disj! added existing)
                                                               (conj! new-item))
                                                           (conj! removed existing)])
                                                        [(assoc! index k item)
                                                         (conj! added item)
                                                         removed])))
                                                  [(transient (primary-index compound)) (transient #{}) (transient #{})]
                                                  items)
        [added removed] [(persistent! added) (persistent! removed)]
        new-secondary-indexes-by-id (reduce-kv (fn update-indexes [indexes index-id index]
                                                 (let [index-def (secondary-index-def compound index-id)]
                                                   (assoc! indexes index-id (-> (csi/remove index index-def removed)
                                                                                (csi/add index-def added)))))
                                               (transient {})
                                               (secondary-indexes-by-id compound))]
    (assoc compound
           :primary-index (persistent! new-primary-index)
           :secondary-indexes-by-id (persistent! new-secondary-indexes-by-id))))

(s/fdef remove-keys
        :args (s/cat :compound ::compound
                     :keys (s/coll-of any?))
        :ret ::compound)

(defn remove-keys
  "Remove items from the compound with the given primary keys"
  [compound ks]
  (let [key-fn (primary-index-fn compound)
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
                                                 (let [index-def (secondary-index-def compound index-id)]
                                                   (assoc! m index-id (csi/remove index index-def removed))))
                                               (transient {})
                                               (secondary-indexes-by-id compound))]
    (assoc compound
           :primary-index (persistent! new-primary-index)
           :secondary-indexes-by-id (persistent! new-secondary-indexes-by-id))))

(s/fdef update-item
        :args (s/cat :compound ::compound
                     :key any?
                     :f ifn?
                     :args (s/* any?))
        :ret ::compound)


;; ------------------------------
;; Additional public functions
;; ------------------------------

(defn update-item
  "Update the item with the given primary key, cascading updates to the secondary indexes"
  ([compound k f]
   (if-let [new-item (f (get (primary-index compound) k))]
     (-> compound
         (remove-keys [k])
         (add-items [new-item]))
     (throw (ex-info (str "Can't update item, item with key " k " not found") {:compound compound}))))
  ([compound k f & args]
   (update-item compound k #(apply f % args))))

(s/fdef clear
        :args (s/cat :compound ::compound)
        :ret ::compound)

(defn clear
  "Clear a compound of data"
  [compound]
  (assoc compound
         :primary-index {}
         :secondary-indexes-by-id (reduce-kv (fn clear-secondary-indexes [indexes index-id index-def]
                                               (assoc indexes index-id (csi/empty index-def)))
                                             {}
                                             (secondary-index-defs-by-id compound))))

(s/fdef items
        :args (s/cat :compound ::compound)
        :ret (s/nilable (s/coll-of any?)))

(defn items
  "Returns the items indexed by the compound"
  [compound]
  (vals (primary-index compound)))

(s/fdef diff
        :args (s/cat :source ::compound
                     :target ::compound)
        :ret ::diff)

(defn diff
  "Returns a diff which will provide the information required to turn the _source_ compound into the _target_ compound,
   so that source can be turned into target  in the following format.

   {:inserts ... items that exist only in source, and not in target
    :updates {:source ... :target ...} source and target items that exist in source and target, but are different
    :deletes ... items that exist in target, but not in source

   This is useful when e.g. syncing a compound with an external mutable datastructure"
  [source target]
  (assert (= (primary-index-def source) (primary-index-def target)) "Only compounds with matching primary-indexes can be diffed")
  (let [source-index (primary-index source)
        target-index (primary-index target)]
    (reduce (fn [m k]
              (let [in-source? (contains? source-index k)
                    in-target? (contains? target-index k)]
                (cond
                  (and in-source? (not in-target?)) (update m :inserts conj (get source-index k))
                  (and in-target? (not in-source?)) (update m :deletes conj (get target-index k))
                  ;; must be in both
                  :else (let [s (get source-index k)
                              k (get target-index k)]
                          (if (not= s k)
                            (update m :updates conj {:source s, :target k})
                            m)))))
            {:inserts #{}
             :updates #{}
             :deletes #{}}
            (into #{} (concat (keys source-index) (keys target-index))))))

(s/fdef apply-diff
        :args (s/cat :compound ::compound
                     :diff ::diff)
        :ret ::compound)

(defn apply-diff
  [c diff]
  (let [key-fn (primary-index-fn c)
        ;; convert updates to add/remove
        {:keys [inserts deletes]} (reduce (fn [m {:keys [source target]}]
                                            (-> (update m :inserts conj source)
                                                (update :deletes conj target)))
                                          diff
                                          (get diff :updates))]
    (-> (remove-keys c (map key-fn deletes))
        (add-items inserts))))

;; ------------------------------
;;   Constructor
;; ------------------------------

(def secondary-index-defaults
  {:index-type :compound/one-to-many})

(defn add-secondary-index
  "Adds an additional secondary index to an existing compound.
   Throws if there is there is a conflict on the index id."
  [compound index-def]
  (let [index-def (merge secondary-index-defaults index-def)
        id (csi/id index-def)]
    (s/assert ::csi/secondary-index-def index-def)
    (if (secondary-index compound id)
      (throw (ex-info (str "Cannot add secondary index - index with id " id " already exists")
                      {:id id :index-def index-def :compound compound}))
      (let [new-secondary-index (csi/add (csi/empty index-def) index-def (items compound))]
        (-> compound
            (update :secondary-index-defs-by-id assoc id index-def)
            (update :secondary-indexes-by-id assoc id new-secondary-index))))))

(defn remove-secondary-index
  "Remove the secondary index with the provided id from the compound"
  [compound id]
  (-> compound
      (update :secondary-index-defs-by-id dissoc id)
      (update :secondary-indexes-by-id dissoc id)))

(s/def ::secondary-index-defs
  (s/coll-of map?))

(s/fdef compound
        :args (s/cat :opts (s/keys :req-un [::primary-index-def]
                                   :opt-un [::secondary-index-defs]))
        :ret ::compound)

(def primary-index-defaults
  {:on-conflict :compound/replace})

(defn ^:deprecated compound [opts]
  (let [{:keys [primary-index-def secondary-index-defs]} opts
        primary-index-def (merge primary-index-defaults primary-index-def)]
    (s/assert ::primary-index-def primary-index-def)
    (reduce add-secondary-index
            {:primary-index-def primary-index-def
             :primary-index {}
             :secondary-indexes-by-id {}
             :secondary-index-defs-by-id {}}
            secondary-index-defs)))
