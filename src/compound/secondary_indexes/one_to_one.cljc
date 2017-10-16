(ns compound.secondary-indexes.one-to-one
   (:require [compound.core :as c]
             [compound.spec :as cs]
             [clojure.spec.alpha :as s]))

(s/def ::key-fn ifn?)

(defmethod cs/secondary-index-def-spec :compound.secondary-index.types/one-to-one
  [_]
  (s/keys :req [::key-fn]))

(defmethod c/secondary-index-def->behaviour :compound.secondary-index.types/one-to-one
  [index-def]
  (let [{::keys [key-fn]} index-def]
    #:compound.secondary-index.behaviour{:empty {}
                                         :add (fn [index added]
                                                (let [new-index (reduce (fn add-items [index item]
                                                                          (let [k (key-fn item)
                                                                                existing-item (get index k)]
                                                                            (if existing-item
                                                                              (throw (ex-info "Duplicate key" {:key k, :index-def index-def, :item item}))
                                                                              (assoc! index k item))))
                                                                        (transient index)
                                                                        added)]
                                                  (persistent! new-index)))
                                         :remove (fn [index removed]
                                                   (let [new-index (reduce (fn remove-items [index item]
                                                                             (let [k (key-fn item)]
                                                                               (dissoc! index k)))
                                                                           (transient index)
                                                                           removed)]
                                                     (persistent! new-index)))}))