(ns compound.core-test
  (:require [clojure.test :refer :all]
            [compound.secondary-indexes.one-to-one]
            [compound.secondary-indexes.one-to-many]
            [compound.secondary-indexes.many-to-many]
            [compound.secondary-indexes.one-to-one-nested]
            [compound.secondary-indexes.one-to-many-nested]
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

(defmethod c/custom-key-fn ::delivery-date-product
  [k item]
  [(:delivery-date item) (:product item)])

(deftest nested-indexes
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
          :compound.core-test/delivery-date-product
          {["2012-03-03" :bananas] {:delivery-date "2012-03-03", :product :bananas},
           ["2012-03-03" :apples] {:delivery-date "2012-03-03", :product :apples},
           ["2012-03-04" :potatoes] {:delivery-date "2012-03-04", :product :potatoes},
           ["2012-03-04" :bananas] {:delivery-date "2012-03-04", :product :bananas}}}
         (-> (c/compound {:primary-index-def {:custom-key ::delivery-date-product
                                              :on-conflict :compound/replace}
                          :secondary-index-defs [{:keys [:delivery-date :product]
                                                  :index-type :compound/one-to-one-nested}
                                                 {:keys [:product :delivery-date]
                                                  :index-type :compound/one-to-many-nested}]})
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
          :compound.core-test/delivery-date-product
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


(deftest readme
  (= (-> (c/compound {:primary-index-def {:key :id
                                          :on-conflict :compound/replace}
                      :secondary-index-defs [{:key :source
                                              :index-type :compound/one-to-many}
                                             {:key :difficulty
                                              :index-type :compound/one-to-many}
                                             {:key :appropriate-materials
                                              :index-type :compound/many-to-many}]})
         (c/add-items #{{:id 1 :source :instituto-di-moda :difficulty :medium,
                         :appropriate-materials #{:cotton :linen} :pattern :bodice-basic}

                        {:id 2 :source :instituto-di-moda :difficulty :easy,
                         :appropriate-materials #{:cotton} :pattern :shirt-basic}

                        {:id 3 :source :instituto-di-moda, :difficulty :easy
                         :appropriate-materials #{:cotton :linen} :pattern :bodice-dartless}

                        {:id 4 :source :winifred-aldrich :difficulty :medium
                         :appropriate-materials #{:silk :cotton} :pattern :dress-princess-seam}

                        {:id 5 :source :winifred-aldrich :pattern :winter-coat
                         :appropriate-materials #{:wool} :difficulty :hard}})
         (c/indexes-by-id))
     {:source
      {:winifred-aldrich
       #{{:id 5,
          :source :winifred-aldrich,
          :pattern :winter-coat,
          :appropriate-materials #{:wool},
          :difficulty :hard}
         {:id 4,
          :source :winifred-aldrich,
          :difficulty :medium,
          :appropriate-materials #{:silk :cotton},
          :pattern :dress-princess-seam}},
       :instituto-di-moda
       #{{:id 1,
          :source :instituto-di-moda,
          :difficulty :medium,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-basic}
         {:id 2,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton},
          :pattern :shirt-basic}
         {:id 3,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-dartless}}},
      :difficulty
      {:hard
       #{{:id 5,
          :source :winifred-aldrich,
          :pattern :winter-coat,
          :appropriate-materials #{:wool},
          :difficulty :hard}},
       :medium
       #{{:id 1,
          :source :instituto-di-moda,
          :difficulty :medium,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-basic}
         {:id 4,
          :source :winifred-aldrich,
          :difficulty :medium,
          :appropriate-materials #{:silk :cotton},
          :pattern :dress-princess-seam}},
       :easy
       #{{:id 2,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton},
          :pattern :shirt-basic}
         {:id 3,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-dartless}}},
      :appropriate-materials
      {:wool
       #{{:id 5,
          :source :winifred-aldrich,
          :pattern :winter-coat,
          :appropriate-materials #{:wool},
          :difficulty :hard}},
       :cotton
       #{{:id 1,
          :source :instituto-di-moda,
          :difficulty :medium,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-basic}
         {:id 4,
          :source :winifred-aldrich,
          :difficulty :medium,
          :appropriate-materials #{:silk :cotton},
          :pattern :dress-princess-seam}
         {:id 2,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton},
          :pattern :shirt-basic}
         {:id 3,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-dartless}},
       :linen
       #{{:id 1,
          :source :instituto-di-moda,
          :difficulty :medium,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-basic}
         {:id 3,
          :source :instituto-di-moda,
          :difficulty :easy,
          :appropriate-materials #{:cotton :linen},
          :pattern :bodice-dartless}},
       :silk
       #{{:id 4,
          :source :winifred-aldrich,
          :difficulty :medium,
          :appropriate-materials #{:silk :cotton},
          :pattern :dress-princess-seam}}},
      :id
      {5
       {:id 5,
        :source :winifred-aldrich,
        :pattern :winter-coat,
        :appropriate-materials #{:wool},
        :difficulty :hard},
       1
       {:id 1,
        :source :instituto-di-moda,
        :difficulty :medium,
        :appropriate-materials #{:cotton :linen},
        :pattern :bodice-basic},
       4
       {:id 4,
        :source :winifred-aldrich,
        :difficulty :medium,
        :appropriate-materials #{:silk :cotton},
        :pattern :dress-princess-seam},
       2
       {:id 2,
        :source :instituto-di-moda,
        :difficulty :easy,
        :appropriate-materials #{:cotton},
        :pattern :shirt-basic},
       3
       {:id 3,
        :source :instituto-di-moda,
        :difficulty :easy,
        :appropriate-materials #{:cotton :linen},
        :pattern :bodice-dartless}}}))
