(ns compound.spec
  (:require [clojure.spec.alpha :as s]))

(defmulti index-def-spec :compound.index/type)

(s/def :compound/index-def
  (s/multi-spec index-def-spec :compound.index/type))

(s/def :compound.index/conflict-behaviour
  #{:compound.conflict-behaviours/upsert :compound.conflict-behaviours/throw})

(s/def :compound/index-defs
  (s/and
   (s/coll-of :compound/index-def)
   ;; only one primary index allowed
   #(= 1 (count (filter (comp #{:compound.index.types/primary} :compound.index/type) %)))))

(s/def :compound.index.behaviour/empty any?)

(s/def :compound.index.behaviour/add fn?)

(s/def :compound.index.behaviour/remove fn?)

(s/def :compound.index/behaviour
  (s/keys :req [:compound.index.behaviour/empty :compound.index.behaviour/add :compound.index.behaviour/remove]))

(s/def :compound/compound
  (s/keys :req [:compound/indexes :compound/index-defs :compound/primary-index-id]))
