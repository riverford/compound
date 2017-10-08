(ns compound.core-test
  (:require [clojure.test :refer :all]
            [compound.indexes.multi]
            [compound.indexes.unique]
            [compound.core :as c]))

(deftest empty
  (is (= #:compound{:index-defs
                    {:id
                     #:compound.index{:id :id,
                                      :conflict-behaviour
                                      :compound.conflict-behaviours/upsert,
                                      :key-fn :id,
                                      :type :compound.index.types/primary},
                     :name
                     #:compound.index{:id :name
                                      :key-fn :name,
                                      :type :compound.index.types/unique}},
                    :indexes {:id {}, :name {}},
                    :primary-index-id :id}
         (c/empty-compound #{#:compound.index{:id :id
                                              :conflict-behaviour :compound.conflict-behaviours/upsert
                                              :key-fn :id
                                              :type :compound.index.types/primary}
                             #:compound.index{:id :name
                                              :key-fn :name
                                              :type :compound.index.types/unique}}))))

(deftest adding
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrel"}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/unique}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (get :compound/indexes)))))

(deftest removing
  (is (= {:name
          {"Squirrel" {:id 3, :name "Squirrel"}},
          :id
          {3 {:id 3, :name "Squirrel"}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/unique}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove [1 2])
             (get :compound/indexes)))))

(deftest upserting
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Red Squirrel" {:id 3, :name "Red Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Red Squirrel"}}},
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/unique}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
             (get :compound/indexes))))
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Green Squirrel" {:id 3, :name "Green Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Green Squirrel"}}},
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/unique}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Green Squirrel"}])
             (get :compound/indexes))))
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Blue Squirrel" {:id 3, :name "Blue Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Blue Squirrel"}}},
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/unique}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/add [{:id 3 :name "Blue Squirrel"}])
             (get :compound/indexes)))))

(deftest conflict
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/throw
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/unique}})
                                              (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
                                              (get :compound/indexes))))
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/upsert
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/unique}})
                                              (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"}])
                                              (get :compound/indexes)))))
