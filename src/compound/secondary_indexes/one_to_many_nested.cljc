(ns compound.secondary-indexes.one-to-many-nested
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-many-nested
  [_]
  (s/keys :req [::key-fn]))

(defmethod c/secondary-index-def->behaviour :compound.secondary-index.types/one-to-many-nested
  [index-def]
  (let [{::keys [key-fn]} index-def]
    #:compound.secondary-index.behaviour{:empty {}
                                         :add (fn [index added]
                                                (let [new-index (reduce (fn add-items [index item]
                                                                          (let [ks (key-fn item)]
                                                                            (update-in index ks (fnil conj #{}) item)))
                                                                        index
                                                                        added)]
                                                  new-index))
                                         :remove (fn [index removed]
                                                   (let [new-index (reduce (fn remove-items [index item]
                                                                             (let [ks (key-fn item)]
                                                                               (update-in index ks disj item)))
                                                                           index
                                                                           removed)]
                                                     new-index))}))
