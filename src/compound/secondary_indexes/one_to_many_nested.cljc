(ns compound.secondary-indexes.one-to-many-nested
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [compound.secondary-indexes :as csi]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-many-nested
  [_]
  (s/keys :req [::key-fn]))

(defmethod csi/empty :compound.secondary-index.types/one-to-many-nested
  [index-def]
  {})

(defmethod csi/add :compound.secondary-index.types/one-to-many-nested
  [index index-def added]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn add-items [index item]
                            (let [ks (key-fn item)]
                              (update-in index ks (fnil conj #{}) item)))
                          index
                          added)]
    new-index))

(defmethod csi/remove :compound.secondary-index.types/one-to-many-nested
  [index index-def removed]
  (let [{::keys [key-fn]} index-def
        new-index (reduce (fn remove-items [index item]
                            (let [ks (key-fn item)]
                              (update-in index ks disj item)))
                          index
                          removed)]
    new-index))
