(ns compound.secondary-indexes.one-to-one-nested
  (:require [compound.core :as c]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]))

(s/def ::keys (s/coll-of keyword? :kind vector?))
(s/def ::custom-key keyword?)
(s/def ::id any?)

(defmethod csi/spec :compound/one-to-one-nested
  [_]
  (s/keys :req-un [(or ::keys ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/one-to-one-nested
  [index-def]
  {})

(defmethod csi/id :compound/one-to-one-nested
  [index-def]
  (or (:id index-def)
      (:custom-key index-def)
      (:keys index-def)))

(defmethod csi/add :compound/one-to-one-nested
  [index index-def added]
  (let [{:keys [keys custom-key]} index-def
        key-fn (if keys (apply juxt keys) (partial c/custom-key-fn custom-key))
        new-index (reduce (fn add-items [index item]
                            (let [ks (key-fn item)
                                  existing-item (get-in index ks)]
                              (if existing-item
                                (throw (ex-info "Duplicate key" {:path ks, :index-def index-def, :item item}))
                                (assoc-in index ks item))))
                          index
                          added)]
    new-index))

(defmethod csi/remove :compound/one-to-one-nested
  [index index-def removed]
  (let [{:keys [keys custom-key]} index-def
        key-fn (if keys (apply juxt keys) (partial c/custom-key-fn custom-key))
        new-index (reduce (fn remove-items [index item]
                            (let [ks (key-fn item)]
                              (update-in index (butlast ks) dissoc (last ks))))
                          index
                          removed)]
    new-index))
