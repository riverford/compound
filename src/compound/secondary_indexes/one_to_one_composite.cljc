(ns compound.secondary-indexes.one-to-one-composite
  (:require [compound.custom-key :as cu]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]))

(s/def ::keys (s/coll-of ::csi/key :kind vector?))
(s/def ::custom-key ::csi/custom-key)
(s/def ::id any?)

(defmethod csi/spec :compound/one-to-one-composite
  [_]
  (s/keys :req-un [(or ::keys ::custom-key)]
          :opt-un [::id]))

(defmethod csi/empty :compound/one-to-one-composite
  [index-def]
  {})

(defmethod csi/id :compound/one-to-one-composite
  [index-def]
  (or (:id index-def)
      (:custom-key index-def)
      (:keys index-def)))

(defmethod csi/add :compound/one-to-one-composite
  [index index-def added]
  (let [{:keys [keys custom-key]} index-def
        keys-fn (csi/keys-fn index-def)
        new-index (reduce (fn add-items [index item]
                            (let [ks (keys-fn item)
                                  existing-item (get-in index ks)]
                              (if existing-item
                                (throw (ex-info "Duplicate key" {:path ks, :index-def index-def, :item item}))
                                (assoc-in index ks item))))
                          index
                          added)]
    new-index))

(defmethod csi/remove :compound/one-to-one-composite
  [index index-def removed]
  (let [{:keys [keys custom-key]} index-def
        keys-fn (csi/keys-fn index-def)
        new-index (reduce (fn remove-items [index item]
                            (let [ks (keys-fn item)]
                              (update-in index (butlast ks) dissoc (last ks))))
                          index
                          removed)]
    new-index))

