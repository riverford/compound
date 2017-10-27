(ns compound.secondary-indexes.one-to-one-nested
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-one-nested
  [_]
  (s/keys :req [::key-fn]))

(defmethod csi/empty :compound.secondary-index.types/one-to-one-nested
  [index-def]
  {})

(defmethod csi/add :compound.secondary-index.types/one-to-one-nested
  [index index-def added]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn add-items [index item]
                            (let [ks (key-fn item)
                                  existing-item (get-in index ks)]
                              (if existing-item
                                (throw (ex-info "Duplicate key" {:path ks, :index-def index-def, :item item}))
                                (assoc-in index ks item))))
                          index
                          added)]
    new-index))

(defmethod csi/remove :compound.secondary-index.types/one-to-one-nested
  [index index-def removed]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn remove-items [index item]
                            (let [ks (key-fn item)]
                              (update-in index (butlast ks) dissoc (last ks))))
                          index
                          removed)]
    new-index))
