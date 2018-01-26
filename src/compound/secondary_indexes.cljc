(ns compound.secondary-indexes
  (:require [clojure.spec.alpha :as s]
            [compound.custom-key :as cu])
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

(defn extract-fn [key]
  (cond
    (keyword? key) key
    (vector? key) (fn [x] (get-in x key))
    :else (throw (ex-info "Unsupported key type" {:key key}))))

(defn key-fn [index-def]
  (let [{:keys [key custom-key]} index-def]
    (cond
      key (extract-fn key)
      custom-key (partial cu/custom-key-fn custom-key)
      :else (throw (ex-info "Index must provide key or custom-key" index-def)))))

(defn keys-fn [index-def]
  (let [{:keys [keys custom-key]} index-def]
    (cond
      keys (apply juxt (map extract-fn keys))
      custom-key (partial cu/custom-key-fn custom-key)
      :else (throw (ex-info "Unsupported key type" {:key key})))))
