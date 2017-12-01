(ns compound.test
  (:require [clojure.test :refer :all]
            [compound.secondary-indexes.one-to-one]
            [compound.secondary-indexes.one-to-many]
            [compound.secondary-indexes.many-to-many]
            [compound.secondary-indexes.one-to-one-composite]
            [compound.secondary-indexes.one-to-many-composite]
            [compound.custom-key :as cu]
            [compound.core :as c]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.alpha :as s]))

(st/instrument)
(s/check-asserts true)

(deftest creating
  (is (= {:primary-index-def {:key :id, :on-conflict :compound/replace},
          :primary-index {},
          :secondary-index-defs-by-id {:name {:key :name, :index-type :compound/one-to-one}},
          :secondary-indexes-by-id {:name {}}}
         (c/compound {:primary-index-def {:key :id
                                          :on-conflict :compound/replace}
                      :secondary-index-defs [{:key :name
                                              :index-type :compound/one-to-one}]}))))

(deftest adding
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/indexes-by-id)))))

(deftest removing
  (is (= {:name
          {"Squirrel" {:id 3, :name "Squirrel"}},
          :id
          {3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove-keys [1 2])
             (c/indexes-by-id)))))

(deftest clearing
  (is (= {:primary-index-def {:key :id, :on-conflict :compound/replace},
          :primary-index {},
          :secondary-index-defs-by-id {:name {:key :name, :index-type :compound/one-to-one}},
          :secondary-indexes-by-id {:name {}}}
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/clear)))))

(deftest updating
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Gerald" {:id 1, :name "Gerald"}},
          :id
          {3 {:id 3, :name "Squirrel"},
           2 {:id 2, :name "Terry"},
           1 {:id 1, :name "Gerald"}}}
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/update-item 1 assoc :name "Gerald")
             (c/indexes-by-id)))))

(deftest replacing
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Red Squirrel" {:id 3, :name "Red Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Red Squirrel"}}},
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}])
             (c/indexes-by-id))))

  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Green Squirrel" {:id 3, :name "Green Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Green Squirrel"}}},
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Green Squirrel"}])
             (c/indexes-by-id))))

  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Blue Squirrel" {:id 3, :name "Blue Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Blue Squirrel"}}},
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/add-items [{:id 3 :name "Blue Squirrel"}])
             (c/indexes-by-id)))))

(deftest conflict
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/compound {:primary-index-def {:key :id
                                                                               :on-conflict :compound/throw}
                                                           :secondary-index-defs [{:key :name
                                                                                   :index-type :compound/one-to-one}]})
                                              (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 3 :name "Red Squirrel"}]))))
  (is (thrown? clojure.lang.ExceptionInfo (-> (c/compound {:primary-index-def {:key :id
                                                                               :on-conflict :compound/replace}
                                                           :secondary-index-defs [{:key :name
                                                                                   :index-type :compound/one-to-one}]})
                                              (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"}])))))

(deftest many-to-many-index
  (is (= {:name
          {"Terry" {:id 2, :name "Terry", :aliases #{:terence :t-man}},
           "Squirrel" {:id 3, :name "Squirrel", :aliases #{:terence}},
           "Bob" {:id 1, :name "Bob", :aliases #{:bobby :robert}}},
          :aliases
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
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/throw}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}
                                                 {:key :aliases
                                                  :index-type :compound/many-to-many}]})
             (c/add-items [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}}])
             (c/indexes-by-id)))))

(defmethod cu/custom-key-fn ::delivery-date-product
  [k item]
  [(:delivery-date item) (:product item)])

(deftest composite-indexes
  (is (= {[:delivery-date :product]
          {"2012-03-04"
           {:potatoes {:delivery-date "2012-03-04", :product :potatoes},
            :bananas {:delivery-date "2012-03-04", :product :bananas}},
           "2012-03-03"
           {:apples {:delivery-date "2012-03-03", :product :apples},
            :bananas {:delivery-date "2012-03-03", :product :bananas}},
           "2012-03-06" {}},
          [:product :delivery-date]
          {:potatoes
           {"2012-03-04" #{{:delivery-date "2012-03-04", :product :potatoes}},
            "2012-03-06" #{}},
           :apples {"2012-03-03" #{{:delivery-date "2012-03-03", :product :apples}}},
           :bananas
           {"2012-03-03" #{{:delivery-date "2012-03-03", :product :bananas}},
            "2012-03-04" #{{:delivery-date "2012-03-04", :product :bananas}}}},
          :compound.test/delivery-date-product
          {["2012-03-03" :bananas] {:delivery-date "2012-03-03", :product :bananas},
           ["2012-03-03" :apples] {:delivery-date "2012-03-03", :product :apples},
           ["2012-03-04" :potatoes] {:delivery-date "2012-03-04", :product :potatoes},
           ["2012-03-04" :bananas] {:delivery-date "2012-03-04", :product :bananas}}}
         (-> (c/compound {:primary-index-def {:custom-key ::delivery-date-product
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:keys [:delivery-date :product]
                                                  :index-type :compound/one-to-one-composite}
                                                 {:keys [:product :delivery-date]
                                                  :index-type :compound/one-to-many-composite}]})
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                           {:delivery-date "2012-03-03" :product :apples}
                           {:delivery-date "2012-03-04" :product :potatoes}
                           {:delivery-date "2012-03-04" :product :bananas}
                           {:delivery-date "2012-03-06" :product :potatoes}])
             (c/remove-keys [["2012-03-06" :potatoes]])
             (c/indexes-by-id)))))

(deftest merging
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrek" {:id 3, :name "Squirrek", :color :red},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrek", :color :red}}}
         (-> (c/compound {:primary-index-def {:key :id
                                              :on-conflict :compound/merge}
                          :secondary-index-defs [{:key :name
                                                  :index-type :compound/one-to-one}]})
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrek"} {:id 3 :color :red}])
             (c/indexes-by-id)))))

(defmethod c/on-conflict-fn ::add-quantities
  [index-def existing-item new-item]
  (assoc (merge existing-item new-item) :quantity-delta
         (+ (get existing-item :quantity-delta) (get new-item :quantity-delta))))

(deftest merge-custom
  (is (= {:product
          {:apples
           #{{:delivery-date "2012-03-03", :product :apples, :quantity-delta 2}},
           :bananas
           #{{:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2}
             {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1}},
           :potatoes
           #{{:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}}},
          :delivery-date
          {"2012-03-03"
           #{{:delivery-date "2012-03-03", :product :apples, :quantity-delta 2}
             {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1}},
           "2012-03-04"
           #{{:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2}},
           "2012-03-06"
           #{{:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}}},
          :compound.test/delivery-date-product
          {["2012-03-03" :bananas]
           {:delivery-date "2012-03-03", :product :bananas, :quantity-delta 1},
           ["2012-03-03" :apples]
           {:delivery-date "2012-03-03", :product :apples, :quantity-delta 2},
           ["2012-03-04" :bananas]
           {:delivery-date "2012-03-04", :product :bananas, :quantity-delta 2},
           ["2012-03-06" :potatoes]
           {:delivery-date "2012-03-06", :product :potatoes, :quantity-delta 12}}}
         (-> (c/compound {:primary-index-def {:custom-key ::delivery-date-product
                                              :on-conflict ::add-quantities}
                          :secondary-index-defs [{:key :product
                                                  :index-type :compound/one-to-many}
                                                 {:key :delivery-date
                                                  :index-type :compound/one-to-many}]})
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas :quantity-delta 1}
                           {:delivery-date "2012-03-03" :product :apples :quantity-delta 2}
                           {:delivery-date "2012-03-04" :product :bananas :quantity-delta 3}
                           {:delivery-date "2012-03-04" :product :bananas :quantity-delta -1}
                           {:delivery-date "2012-03-06" :product :potatoes :quantity-delta 2}
                           {:delivery-date "2012-03-06" :product :potatoes :quantity-delta 10}])
             (c/indexes-by-id)))))

(deftest diffing
  (let [source (-> (c/compound {:primary-index-def {:key :id
                                                    :on-conflict :compound/replace}
                                :secondary-index-defs [{:key :name
                                                        :index-type :compound/one-to-one}]})
                   (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}]))
        target (-> (c/compound {:primary-index-def {:key :id
                                                    :on-conflict :compound/replace}
                                :secondary-index-defs [{:key :name
                                                        :index-type :compound/one-to-one}]})
                   (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Bill"} {:id 4 :name "Ahmed"}]))
        some-other-compound (c/compound {:primary-index-def {:key :blerg
                                                             :on-conflict :compound/replace}
                                         :secondary-index-defs [{:key :name
                                                                 :index-type :compound/one-to-one}]})
        diff (c/diff source target)]
    (is (= {:add #{{:id 3, :name "Squirrel"}},
            :modify #{{:source {:id 2, :name "Terry"}, :target {:id 2, :name "Bill"}}},
            :remove #{{:id 4, :name "Ahmed"}}}
           (c/diff source target)))
    (is (= (c/apply-diff target {:add #{{:id 3, :name "Squirrel"}},
                                 :modify #{{:source {:id 2, :name "Terry"}, :target {:id 2, :name "Bill"}}},
                                 :remove #{{:id 4, :name "Ahmed"}}})
           source))
    (is (thrown? java.lang.AssertionError (c/diff source some-other-compound)))))

