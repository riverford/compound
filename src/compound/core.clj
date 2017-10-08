(ns compound.core
  (:require [compound.compound :as c]
            [compound.indexes.multi]
            [compound.indexes.unique]))

(c/empty-compound #{{:compound.index/id :id
                     :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                     :compound.index/key-fn :id
                     :compound.index/type :compound.index.types/primary}
                    {:compound.index/id :b
                     :compound.index/key-fn :b
                     :compound.index/type :compound.index.types/unique}})
(comment
  (index-def  :b)

  (-> (empty-compound #{{:compound.index/id :id
                         :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                         :compound.index/key-fn :id
                         :compound.index/type :compound.index.types/primary}
                        {:compound.index/id :a
                         :compound.index/key-fn :a
                         :compound.index/type :compound.index.types/unique}})
      (add [{:id 1 :a 3} {:id 2 :a 2} {:id 4 :a 4}]))

  (-> (empty-compound #{{:compound.index/id :id
                         :compound.index/conflict-behaviour :compound.conflict-behaviours/upsert
                         :compound.index/key-fn :id
                         :compound.index/type :compound.index.types/primary}
                        {:compound.index/id :b
                         :compound.index/key-fn :b
                         :compound.index/type :compound.index.types/unique}
                        {:compound.index/id :c
                         :compound.index/key-fn :c
                         :compound.index/type :compound.index.types/multi}})

      (add [{:id 1 :b 1 :c 3} {:id 2 :c 4 :b 2} {:id 3 :c 3 :b 3}])
      (remove [1 ]))
  )
