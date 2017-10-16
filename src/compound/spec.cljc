(ns compound.spec
  (:require [clojure.spec.alpha :as s]))

(s/def :compound.index/id keyword?)

;; Primary index definition
(s/def :compound.primary-index/id :compound.index/id)

(s/def :compound.primary-index/key-fn ifn?)

(s/def :compound.primary-index/conflict-behaviour
  (s/or :simple #{:compound.primary-index.conflict-behaviours/replace
                  :compound.primary-index.conflict-behaviours/merge
                  :compound.primary-index.conflict-behaviours/throw}
        :custom (s/cat :merge-with #{:compound.primary-index.conflict-behaviours/merge-using} :merge-fn fn?)))

(s/def :compound/primary-index-def
  (s/keys :req [:compound.primary-index/id
                :compound.primary-index/key-fn
                :compound.primary-index/conflict-behaviour]))

;; Secondary index definitions
(defmulti secondary-index-def-spec :compound.secondary-index/type)

(s/def :compound.secondary-index/id :compound.index/id)

(s/def :compound.secondary-index/type keyword?)

(s/def :compound/secondary-index-def
  (s/and
    (s/keys :req [:compound.secondary-index/id :compound.secondary-index/type])
    (s/multi-spec secondary-index-def-spec :compound.secondary-index/type)))

;; Secondary index behaviour
(s/def :compound.secondary-index.behaviour/empty any?)

(s/def :compound.secondary-index.behaviour/add fn?)

(s/def :compound.secondary-index.behaviour/remove fn?)

(s/def :compound.secondary-index/behaviour
  (s/keys :req [:compound.secondary-index.behaviour/empty
                :compound.secondary-index.behaviour/add
                :compound.secondary-index.behaviour/remove]))

;; Compound

(s/def :compound/primary-index map?)

(s/def :compound/secondary-index-defs-by-id
  (s/map-of :compound.secondary-index/id :compound/secondary-index-def))

(s/def :compound/secondary-indexes-by-id (s/map-of keyword? any?))

(s/def :compound/compound
  (s/keys :req [:compound/primary-index-def
                :compound/primary-index
                :compound/secondary-index-defs-by-id
                :compound/secondary-indexes-by-id]))


