(ns compound.core-test
  (:require [clojure.test :refer :all]
            [compound.indexes.one-to-one]
            [compound.indexes.one-to-many]
            [compound.indexes.many-to-many]
            [compound.core :as c]))

(deftest empty
  (let [compound (c/empty-compound #{#:compound.index{:id :id
                                                      :conflict-behaviour :compound.conflict-behaviours/upsert
                                                      :key-fn :id
                                                      :type :compound.index.types/primary}
                                     #:compound.index{:id :name
                                                      :key-fn :name
                                                      :type :compound.index.types/one-to-one}})]
    (is (= (c/index-defs compound)  #{#:compound.index{:id :id
                                                       :conflict-behaviour :compound.conflict-behaviours/upsert
                                                       :key-fn :id
                                                       :type :compound.index.types/primary}
                                      #:compound.index{:id :name
                                                       :key-fn :name
                                                       :type :compound.index.types/one-to-one}}))
    (is (= (c/indexes compound) {:id {}, :name {}}))
    (is (= (c/primary-index-id compound) :id))))

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
                                                  :type :compound.index.types/one-to-one}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/indexes)))))

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
                                                  :type :compound.index.types/one-to-one}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove [1 2])
             (c/indexes)))))

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
                                                  :type :compound.index.types/one-to-one}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
             (c/indexes))))
  
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
                                                  :type :compound.index.types/one-to-one}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Green Squirrel"}])
             (c/indexes))))
  
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
                                                  :type :compound.index.types/one-to-one}})
             (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/add [{:id 3 :name "Blue Squirrel"}])
             (c/indexes)))))

(deftest conflict
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/throw
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/one-to-one}})
                                              (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
                                              (get :compound/indexes))))
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/upsert
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/one-to-one}})
                                              (c/add [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"}])
                                              (get :compound/indexes)))))



(deftest many-to-many-index
  (is (= {:name
          {"Terry" {:id 2, :name "Terry", :aliases #{:terence :t-man}},
           "Squirrel" {:id 3, :name "Squirrel", :aliases #{:terence}},
           "Bob" {:id 1, :name "Bob", :aliases #{:bobby :robert}}},
          :alias
          {:terence
           #{{:id 2, :name "Terry", :aliases #{:terence :t-man}}
             {:id 3, :name "Squirrel", :aliases #{:terence}}},
           :t-man #{{:id 2, :name "Terry", :aliases #{:terence :t-man}}},
           :bobby #{{:id 1, :name "Bob", :aliases #{:bobby :robert}}},
           :robert #{{:id 1, :name "Bob", :aliases #{:bobby :robert}}}},
          :id
          {1 {:id 1, :name "Bob", :aliases #{:bobby :robert}},
           2 {:id 2, :name "Terry", :aliases #{:terence :t-man}},
           3 {:id 3, :name "Squirrel", :aliases #{:terence}}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/upsert
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}
                                 #:compound.index{:id :alias
                                                  :key-fn :aliases
                                                  :type :compound.index.types/many-to-many}})
             (c/add [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}}])
             (c/indexes)))))
