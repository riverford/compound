(ns compound.core-test
  (:require [clojure.test :refer :all]
            [compound.indexes.one-to-one]
            [compound.indexes.one-to-many]
            [compound.indexes.many-to-many]
            [compound.indexes.one-to-one-nested]
            [compound.indexes.one-to-many-nested]
            [compound.core :as c]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.alpha :as s]))

(st/instrument)
(s/check-asserts true)

(deftest empty-compound
  (let [compound (c/empty-compound #{#:compound.index{:id :id
                                                      :conflict-behaviour :compound.conflict-behaviours/replace
                                                      :key-fn :id
                                                      :type :compound.index.types/primary}
                                     #:compound.index{:id :name
                                                      :key-fn :name
                                                      :type :compound.index.types/one-to-one}})]
    (is (= (c/index-defs-by-id compound)  {:name
                                           #:compound.index{:id :name,
                                                            :key-fn :name,
                                                            :type :compound.index.types/one-to-one},
                                           :id
                                           #:compound.index{:id :id,
                                                            :conflict-behaviour :compound.conflict-behaviours/replace,
                                                            :key-fn :id,
                                                            :type :compound.index.types/primary}}))
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
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/indexes)))))

(deftest removing
  (is (= {:name
          {"Squirrel" {:id 3, :name "Squirrel"}},
          :id
          {3 {:id 3, :name "Squirrel"}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove-keys [1 2])
             (c/indexes)))))

(deftest updating
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Gerald" {:id 1, :name "Gerald"}},
          :id
          {3 {:id 3, :name "Squirrel"},
           2 {:id 2, :name "Terry"},
           1 {:id 1, :name "Gerald"}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/update-item 1 assoc :name "Gerald")
             (c/indexes)))))

(deftest replacing
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Red Squirrel" {:id 3, :name "Red Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Red Squirrel"}}},
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
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
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Green Squirrel"}])
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
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/add-items [{:id 3 :name "Blue Squirrel"}])
             (c/indexes)))))

(deftest conflict
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/throw
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/one-to-one}})
                                              (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
                                              (get :compound/indexes))))
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/empty-compound #{#:compound.index{:id :id
                                                                                   :conflict-behaviour :compound.conflict-behaviours/replace
                                                                                   :key-fn :id
                                                                                   :type :compound.index.types/primary}
                                                                  #:compound.index{:id :name
                                                                                   :key-fn :name
                                                                                   :type :compound.index.types/one-to-one}})
                                              (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"}])
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
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}
                                 #:compound.index{:id :alias
                                                  :key-fn :aliases
                                                  :type :compound.index.types/many-to-many}})
             (c/add-items [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}}])
             (c/indexes)))))

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
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}
                                 #:compound.index{:id :alias
                                                  :key-fn :aliases
                                                  :type :compound.index.types/many-to-many}})
             (c/add-items [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}}])
             (c/indexes)))))

(deftest one-to-one-nested-index
  (is (= {:product-delivery-date
          {:potatoes
           {"2012-03-04" #{{:delivery-date "2012-03-04", :product :potatoes}},
            "2012-03-06" #{{:delivery-date "2012-03-06", :product :potatoes}}},
           :apples {"2012-03-03" #{{:delivery-date "2012-03-03", :product :apples}}},
           :bananas
           {"2012-03-03" #{{:delivery-date "2012-03-03", :product :bananas}},
            "2012-03-04" #{{:delivery-date "2012-03-04", :product :bananas}}}},
          :delivery-date-product
          {"2012-03-04"
           {:potatoes {:delivery-date "2012-03-04", :product :potatoes},
            :bananas {:delivery-date "2012-03-04", :product :bananas}},
           "2012-03-03"
           {:apples {:delivery-date "2012-03-03", :product :apples},
            :bananas {:delivery-date "2012-03-03", :product :bananas}},
           "2012-03-06" {:potatoes {:delivery-date "2012-03-06", :product :potatoes}}},
          :id
          {["2012-03-03" :bananas] {:delivery-date "2012-03-03", :product :bananas},
           ["2012-03-03" :apples] {:delivery-date "2012-03-03", :product :apples},
           ["2012-03-04" :potatoes] {:delivery-date "2012-03-04", :product :potatoes},
           ["2012-03-04" :bananas] {:delivery-date "2012-03-04", :product :bananas},
           ["2012-03-06" :potatoes] {:delivery-date "2012-03-06", :product :potatoes}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/replace
                                                  :key-fn (juxt :delivery-date :product)
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :delivery-date-product
                                                  :key-fn (juxt :delivery-date :product)
                                                  :type :compound.index.types/one-to-one-nested}
                                 #:compound.index{:id :product-delivery-date
                                                  :key-fn (juxt :product :delivery-date)
                                                  :type :compound.index.types/one-to-many-nested}})
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                           {:delivery-date "2012-03-03" :product :apples}
                           {:delivery-date "2012-03-04" :product :potatoes}
                           {:delivery-date "2012-03-04" :product :bananas}
                           {:delivery-date "2012-03-06" :product :potatoes}])
             (c/indexes)))))

(deftest merging
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrek" {:id 3, :name "Squirrek", :color :red},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrek", :color :red}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour :compound.conflict-behaviours/merge
                                                  :key-fn :id
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :name
                                                  :key-fn :name
                                                  :type :compound.index.types/one-to-one}})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrek"} {:id 3 :color :red}])
             (c/indexes)))))

(deftest merge-custom
  (is (= {:product
          {:potatoes
           #{{:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}},
           :bananas
           #{{:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2}
             {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1}},
           :apples
           #{{:delivery-date "2012-03-03", :product :apples, :quantity-delta 2}}},
          :delivery-date
          {"2012-03-06"
           #{{:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}},
           "2012-03-04"
           #{{:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2}},
           "2012-03-03"
           #{{:delivery-date "2012-03-03", :product :apples, :quantity-delta 2}
             {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1}}},
          :id
          {["2012-03-03" :bananas]
           {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1},
           ["2012-03-03" :apples]
           {:delivery-date "2012-03-03", :product :apples, :quantity-delta 2},
           ["2012-03-04" :bananas]
           {:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2},
           ["2012-03-06" :potatoes]
           {:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}}}
         (-> (c/empty-compound #{#:compound.index{:id :id
                                                  :conflict-behaviour [:compound.conflict-behaviours/merge-using (fn [a b]
                                                                                                                   (assoc (merge a b) :quantity-delta
                                                                                                                          (+ (get a :quantity-delta) (get b :quantity-delta))))] 
                                                  :key-fn (juxt :delivery-date :product)
                                                  :type :compound.index.types/primary}
                                 #:compound.index{:id :product
                                                  :key-fn :product
                                                  :type :compound.index.types/one-to-many}
                                 #:compound.index{:id :delivery-date
                                                  :key-fn :delivery-date
                                                  :type :compound.index.types/one-to-many}})
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas :quantity-delta 1}
                           {:delivery-date "2012-03-03" :product :apples :quantity-delta 2}
                           {:delivery-date "2012-03-04" :product :bananas :quantity-delta 3}
                           {:delivery-date "2012-03-04" :product :bananas :quantity-delta -1}
                           {:delivery-date "2012-03-06" :product :potatoes :quantity-delta 2}
                           {:delivery-date "2012-03-06" :product :potatoes :quantity-delta 10}])
             (c/indexes)))))
