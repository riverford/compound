(ns compound2.test
  (:require [clojure.test :refer :all]
            [compound2.core :as c]))

(deftest adding
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound [{:index-type :unique
                           :id :id
                           :kfn :id}
                          {:index-type :unique
                           :id :name
                           :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])))))

(deftest removing
  (is (= {:name
          {"Squirrel" {:id 3, :name "Squirrel"}},
          :id
          {3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound [{:index-type :unique
                           :id :id
                           :kfn :id}
                          {:index-type :unique
                           :id :name
                           :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove-keys [1 2]))))
  (is (= {:name
          {"Terry" #{{:id 2, :name "Terry"}},
           "Squirrel" #{{:id 4, :name "Squirrel"} {:id 5, :name "Squirrel"}}},
          :id
          {5 {:id 5, :name "Squirrel"},
           2 {:id 2, :name "Terry"},
           4 {:id 4, :name "Squirrel"}}}
         (-> (c/compound [{:index-type :unique
                           :kfn :id
                           :id :id}
                          {:index-type :multi
                           :kfn :name
                           :id :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"} {:id 5 :name "Squirrel"}])
             (c/remove-keys [1 3])))))

(deftest nested
  (is (= {[:delivery-date :product]
          {"2012-03-04"
           {:potatoes {:delivery-date "2012-03-04", :product :potatoes},
            :bananas {:delivery-date "2012-03-04", :product :bananas}},
           "2012-03-03"
           {:apples {:delivery-date "2012-03-03", :product :apples},
            :bananas {:delivery-date "2012-03-03", :product :bananas}},},
          [:product :delivery-date]
          {:potatoes
           {"2012-03-04"
            #{{:delivery-date "2012-03-04", :product :potatoes}}},
           :apples {"2012-03-03" #{{:delivery-date "2012-03-03", :product :apples}}},
           :bananas
           {"2012-03-03" #{{:delivery-date "2012-03-03", :product :bananas}},
            "2012-03-04" #{{:delivery-date "2012-03-04", :product :bananas}}}},
          :delivery-date-product
          {["2012-03-03" :bananas] {:delivery-date "2012-03-03", :product :bananas},
           ["2012-03-03" :apples] {:delivery-date "2012-03-03", :product :apples},
           ["2012-03-04" :potatoes] {:delivery-date "2012-03-04", :product :potatoes},
           ["2012-03-04" :bananas] {:delivery-date "2012-03-04", :product :bananas}}}
         (-> (c/compound [{:index-type :unique
                           :id :delivery-date-product
                           :kfn (juxt :delivery-date :product)}
                          {:index-type :nested.unique
                           :path [:delivery-date :product]
                           :id [:delivery-date :product]}
                          {:index-type :nested.multi
                           :path [:product :delivery-date]
                           :id [:product :delivery-date]}])
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                           {:delivery-date "2012-03-03" :product :apples}
                           {:delivery-date "2012-03-04" :product :potatoes}
                           {:delivery-date "2012-03-04" :product :bananas}
                           {:delivery-date "2012-03-06" :product :potatoes}])
             (c/remove-keys [["2012-03-06" :potatoes]])))))
