(ns compound.secondary-indexes
  (:require [clojure.spec.alpha :as s])
  (:refer-clojure :exclude [empty remove]))

(defmulti empty :index-type)

(defmulti id :index-type)

(defmulti spec :index-type)

(defmulti add
  (fn [index index-def added] (:index-type index-def)))

(defmulti remove
  (fn [index index-def removed] (:index-type index-def)))

(s/def ::index-type keyword?)

(s/def ::secondary-index-def
  (s/and
    (s/keys :req-un [::index-type])
    (s/multi-spec spec :index-type)))

