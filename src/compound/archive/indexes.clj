(ns compound.indexes
  (:require [clojure.set :as set]))

(defprotocol IIndexPrimary
  (-query-primary [this q])
  (-add [this items])
  (-remove [this q]))

(defprotocol IIndexSecondary
  (-query-secondary [this q])
  (-on-add [this added])
  (-on-remove [this removed]))

(defmulti make-index
  (fn [index-def] (get index-def :compound.index/type)))

;; ----------------- Primary Indexes

(defrecord IndexPrimary [index index-def])

(extend-type IndexPrimary
  IIndexPrimary
  (-query-primary [this q] (let [ks q
                                 {:keys [index]} this]
                             (into #{} (comp (map #(get index %))
                                             (remove nil?)) ks)))

  (-add [this items] (let [{:keys [index index-def]} this
                           {:keys [:compound.index/key-fn]} index-def
                           [new-index added] (reduce (fn add-items [[index added] item]
                                                       (let [k (key-fn item)]
                                                         [(assoc! index k item)
                                                          (assoc! added k item)]))
                                                     [(transient index) (transient {})]
                                                     items)]
                       [(->IndexPrimary (persistent! new-index) index-def) (persistent! added)]))

  (-remove [this q] (let [ks q
                          {:keys [index index-def]} this
                          [new-index removed] (reduce (fn remove-items [[index removed] k]
                                                        (let [item (get index k)]
                                                          [(dissoc! index k)
                                                           (assoc! removed k item)]))
                                                      [(transient index) (transient {})]
                                                      ks)]
                      [(->IndexPrimary (persistent! new-index) index-def) (persistent! removed)])))

(defmethod make-index :compound.index.types/primary
  [index-def]
  (->IndexPrimary {} index-def))

;; ----------------- Unique Secondary Index

(defrecord IndexUnique [index index-def])

(extend-type IndexUnique
  IIndexSecondary
  (-query-secondary [this q] (let [ks q
                                   {:keys [index]} this]
                               (into #{} (comp (map #(get index %))
                                               (remove nil?)) ks)))
  (-on-add [this added] (let [{:keys [index index-def]} this
                              {:keys [:compound.index/key-fn]} index-def
                              new-index (reduce (fn add-items [index [pk item]]
                                                  (let [k (key-fn item)]
                                                    (assoc! index k pk)))
                                                (transient index)
                                                added)]
                          (->IndexUnique (persistent! new-index) index-def)))
  (-on-remove [this removed] (let [{:keys [index index-def]} this
                                   new-index (reduce (fn remove-items [index [pk item]]
                                                       (dissoc! index pk))
                                                     (transient index)
                                                     removed)]
                               (->IndexUnique (persistent! new-index) index-def))))

(defmethod make-index :compound.index.types/unique
  [index-def]
  (->IndexUnique {} index-def))

;; ----------------- Non unique multi secondary index

(defrecord IndexMulti [index index-def])

(extend-type IndexMulti
  IIndexSecondary
  (-query-secondary [this q] (let [ks q
                                   {:keys [index]} this]
                               (into #{} (comp (mapcat #(get index %))
                                               (remove nil?)) ks)))
  (-on-add [this added] (let [{:keys [index index-def]} this
                              {:keys [:compound.index/key-fn]} index-def
                              new-index (reduce (fn add-items [index [pk item]]
                                                  (let [k (key-fn item)
                                                        pks (get index k #{})]
                                                    (assoc! index k (conj pks pk))))
                                                (transient index)
                                                added)]
                          (->IndexMulti (persistent! new-index) index-def)))

  (-on-remove [this removed] (let [{:keys [index index-def]} this
                                   {:keys [:compound.index/key-fn]} index-def
                                   new-index (reduce (fn remove-items [index [pk item]]
                                                       (let [k (key-fn item)
                                                             pks (get index k #{})]
                                                         (assoc! index k (disj pks pk))))
                                                     (transient index)
                                                     removed)]
                               (->IndexMulti (persistent! new-index) index-def))))

(defmethod make-index :compound.index.types/multi
  [index-def]
  (->IndexMulti {} index-def))
