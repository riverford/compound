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
         (-> (c/compound [{:index-type :one-to-one
                           :kfn :id}
                          {:index-type :one-to-one
                           :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}]))
         (-> (c/compound* [{:index-type :one-to-one
                            :kfn :id}
                           {:index-type :one-to-one
                            :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])))))

(deftest removing
  (is (= {:name
          {"Squirrel" {:id 3, :name "Squirrel"}},
          :id
          {3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound [{:index-type :one-to-one
                           :kfn :id}
                          {:index-type :one-to-one
                           :id :name
                           :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"}])
             (c/remove-keys [1 2]))
         (-> (c/compound* [{:index-type :one-to-one
                            :kfn :id}
                           {:index-type :one-to-one
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
         (-> (c/compound [{:index-type :one-to-one
                           :kfn :id
                           :id :id}
                          {:index-type :one-to-many
                           :kfn :name
                           :id :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel"} {:id 4 :name "Squirrel"} {:id 5 :name "Squirrel"}])
             (c/remove-keys [1 3]))
         (-> (c/compound* [{:index-type :one-to-one
                            :kfn :id
                            :id :id}
                           {:index-type :one-to-many
                            :kfn :name
                            :id :name}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrel3"} {:id 4 :name "Squirrel"} {:id 5 :name "Squirrel"}])
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
         (-> (c/compound [{:index-type :one-to-one
                           :id :delivery-date-product
                           :kfn (juxt :delivery-date :product)}
                          {:index-type :nested-to-one
                           :path [:delivery-date :product]
                           :id [:delivery-date :product]}
                          {:index-type :nested-to-many
                           :path [:product :delivery-date]
                           :id [:product :delivery-date]}])
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                           {:delivery-date "2012-03-03" :product :apples}
                           {:delivery-date "2012-03-04" :product :potatoes}
                           {:delivery-date "2012-03-04" :product :bananas}
                           {:delivery-date "2012-03-06" :product :potatoes}])
             (c/remove-keys [["2012-03-06" :potatoes]]))
         (-> (c/compound* [{:index-type :one-to-one
                            :id :delivery-date-product
                            :kfn (juxt :delivery-date :product)}
                           {:index-type :nested-to-one
                            :path [:delivery-date :product]
                            :id [:delivery-date :product]}
                           {:index-type :nested-to-many
                            :path [:product :delivery-date]
                            :id [:product :delivery-date]}])
             (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                           {:delivery-date "2012-03-03" :product :apples}
                           {:delivery-date "2012-03-04" :product :potatoes}
                           {:delivery-date "2012-03-04" :product :bananas}
                           {:delivery-date "2012-03-06" :product :potatoes}])
             (c/remove-keys [["2012-03-06" :potatoes]])))))

(deftest many-to-many
  (is (= {:name
          {"Terry" {:id 2, :name "Terry", :aliases #{:terence :t-man}},
           "Squirrel" {:id 3, :name "Squirrel", :aliases #{:terence}},
           "Jim" {:id 4, :name "Jim", :aliases #{}},
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
           3 {:id 3, :name "Squirrel", :aliases #{:terence}}
           4 {:id 4, :name "Jim", :aliases #{}}}}
         (-> (c/compound [{:kfn :id
                           :index-type :one-to-one}
                          {:kfn :name
                           :index-type :one-to-one}
                          {:kfn :aliases
                           :index-type :many-to-many}])
             (c/add-items [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}} {:id 4, :name "Jim", :aliases #{}}]))
         (-> (c/compound* [{:kfn :id
                           :index-type :one-to-one}
                          {:kfn :name
                           :index-type :one-to-one}
                          {:kfn :aliases
                           :index-type :many-to-many}])
             (c/add-items [{:id 1 :name "Bob" :aliases #{:robert :bobby}} {:id 2 :name "Terry" :aliases #{:terence :t-man}} {:id 3 :name "Squirrel" :aliases #{:terence}} {:id 4, :name "Jim", :aliases #{}}])))))

(deftest merging
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrek" {:id 3, :name "Squirrek", :color :red},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrek", :color :red}}}
         (-> (c/compound [{:kfn :id
                           :index-type :one-to-one
                           :on-conflict (fn [old new]
                                          (merge old new))}
                          {:kfn :name
                           :index-type :one-to-one}])
             (c/add-items [{:id 1 :name "Bob"} {:id 2 :name "Terry"} {:id 3 :name "Squirrek"} {:id 3 :color :red}])))))

(deftest dont-terminate-early-on-nil
  []
  (is (= {:name
          {"Terry" {:id 2, :name "Terry"},
           "Squirrel" {:id 3, :name "Squirrel"},
           "Bob" {:id 1, :name "Bob"}},
          :id
          {1 {:id 1, :name "Bob"},
           2 {:id 2, :name "Terry"},
           3 {:id 3, :name "Squirrel"}}}
         (-> (c/compound [{:index-type :one-to-one
                           :kfn :id}
                          {:index-type :one-to-one
                           :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"}  nil {:id 2 :name "Terry"} nil {:id 3 :name "Squirrel"}]))
         (-> (c/compound* [{:index-type :one-to-one
                            :kfn :id}
                           {:index-type :one-to-one
                            :kfn :name}])
             (c/add-items [{:id 1 :name "Bob"} nil {:id 2 :name "Terry"} nil {:id 3 :name "Squirrel"}])))))
