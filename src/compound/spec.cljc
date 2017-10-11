(ns compound.spec
  (:require [clojure.spec.alpha :as s]))

(defmulti index-def-spec :compound.index/type)

(s/def :compound/index-def
  (s/multi-spec index-def-spec :compound.index/type))

(s/def :compound.index/conflict-behaviour
  #{:compound.conflict-behaviours/replace :compound.conflict-behaviours/merge :compound.conflict-behaviours/throw})

(s/def :compound/index-defs
  (s/and
   (s/coll-of :compound/index-def)
   ;; only one primary index allowed
   #(= 1 (count (filter (comp #{:compound.index.types/primary} :compound.index/type) %)))))

(s/def :compound.index/id keyword?)

(s/def :compound.index.behaviour/empty any?)

(s/def :compound.index.behaviour/add fn?)

(s/def :compound.index.behaviour/remove fn?)

(s/def :compound.index/behaviour
     (s/keys :req [:compound.index.behaviour/empty :compound.index.behaviour/add :compound.index.behaviour/remove]))

(s/def :compound/index-defs-by-id
  (s/map-of :compound.index/id :compound/index-def))

(s/def :compound/index-behaviours-by-id
  (s/map-of :compound.index/id :compound.index/behaviour))

(s/def :compound/compound
  (s/keys :req [:compound/indexes :compound/index-defs-by-id :compound/index-behaviours-by-id :compound/primary-index-id]))
