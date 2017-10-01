(ns compound.spec
  (:require [clojure.spec.alpha :as s]))

(s/def :compound.indexes/key-fn ifn?)
(s/def :compound.indexes/id keyword?)
(s/def :compound.indexes/type keyword?)
