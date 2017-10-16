(ns compound.secondary-indexes.one-to-many
  (:require [compound.core :as c]
            [compound.spec :as cs]
            [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-many
  [_]
  (s/keys :req [::key-fn]))

(defmethod c/secondary-index-def->behaviour :compound.secondary-index.types/one-to-many
  [index-def]
  (let [{::keys [key-fn]} index-def]
    #:compound.secondary-index.behaviour{:empty {}
                                         :add (fn [index added]
                                                (let [new-index (reduce (fn add-items [index item]
                                                                          (let [k (key-fn item)
                                                                                existing-items (get index k #{})]

                                                                            (assoc! index k (conj existing-items item))))
                                                                        (transient index)
                                                                        added)]
                                                  (persistent! new-index)))
                                         :remove (fn [index removed]
                                                   (let [new-index (reduce (fn remove-items [index item]
                                                                             (let [k (key-fn item)
                                                                                   existing-items (get index k #{})]
                                                                               (assoc! index k (disj existing-items item))))
                                                                           (transient index)
                                                                           removed)]
                                                     (persistent! new-index)))}))
