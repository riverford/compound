(ns compound.core
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [compound.secondary-indexes :as csi]))

;; ------------------------------
;;   Primary index spec
;; ------------------------------

(s/def ::id any?)
(s/def ::key keyword?)
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

(defn index-defs-by-id [compound]
  (assoc (secondary-index-defs-by-id compound) (primary-index-id compound) (primary-index-def compound)))

(defn index-def [compound id]
  (get (index-defs-by-id compound) id))

(defn indexes-by-id [compound]
  (assoc (secondary-indexes-by-id compound) (primary-index-id compound) (primary-index compound)))

(defn index [compound id]
  (get (indexes-by-id compound) id))

;; ------------------------------
;;   Custom key-fns
;; ------------------------------

(defmulti custom-key-fn
  (fn [k item] k))

(defmethod custom-key-fn :default
  [k item]
  (throw (ex-info (str "Implementation of custom key-fn for " k " not found") {:k k, :item item})))

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
        key-fn (or key (partial custom-key-fn custom-key))]
    (throw (ex-info (str "Duplicate key " (key-fn new-item) " in primary index") {:existing-item existing-item, :new-item new-item}))))

(defmethod on-conflict-fn :compound/merge
  [_ existing-item new-item]
  (merge existing-item new-item))

;; ------------------------------
;;   Implementation
;; ------------------------------

(s/fdef add-items
        :args (s/cat :compound ::compound
                     :items (s/coll-of any?))
        :ret ::compound)

(defn add-items [compound items]
  (let [{:keys [id key custom-key conflict-behaviour]} (primary-index-def compound)
        key-fn (or key (partial custom-key-fn custom-key))
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

(defn remove-keys [compound ks]
  (let [{:keys [id key custom-key]} (primary-index-def compound)
        key-fn (or key (partial custom-key-fn custom-key))
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

(defn update-item
  ([compound k f]
   (if-let [new-item (f (get (primary-index compound) k))]
     (-> compound
         (remove-keys [k])
         (add-items [new-item]))
     (throw (ex-info (str "Can't update item, item with key " k " not found" {:compound compound})))))
  ([compound k f & args]
   (update-item compound k #(apply f % args))))

(defn clear [compound]
  (assoc compound
         :primary-index {}
         :secondary-indexes-by-id (reduce-kv (fn clear-secondary-indexes [indexes index-id index-def]
                                               (assoc indexes index-id (csi/empty index-def)))
                                             {}
                                             (secondary-index-defs-by-id compound))))

(defn items [compound]
  (vals (primary-index compound)))

;; ------------------------------
;;   Constructor
;; ------------------------------

(s/def ::secondary-index-defs
  (s/coll-of ::csi/secondary-index-def))

(s/fdef compound
        :args (s/cat :opts (s/keys :req-un [::primary-index-def]
                                   :opt-un [::secondary-index-defs]))
        :ret ::compound)

(defn compound [opts]
  (let [{:keys [primary-index-def secondary-index-defs]} opts
        secondary-indexes  (reduce (fn make-secondary-indexes [m index-def]
                                     (let [id (csi/id index-def)]
                                       (-> (assoc-in m [:secondary-index-defs-by-id id] index-def)
                                           (assoc-in [:secondary-indexes-by-id id] (csi/empty index-def)))))
                                   {}
                                   secondary-index-defs)
        {:keys [secondary-index-defs-by-id secondary-indexes-by-id]} secondary-indexes]
    {:primary-index-def primary-index-def
     :primary-index {}
     :secondary-index-defs-by-id secondary-index-defs-by-id
     :secondary-indexes-by-id secondary-indexes-by-id}))
