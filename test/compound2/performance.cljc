(ns compound2.performance
  (:require [compound.secondary-indexes :as csi]
            [compound.core :as c1]
            #?@(:cljs [[compound2.core :as c2]]
                :clj [[compound2.core :as c2]
                      [criterium.core :as crit]])))

#?(:cljs (enable-console-print!))

(def fruit
  ["Açaí"
   "Akee"
   "Apple"
   "Apricot"
   "Avocado"
   "Banana"
   "Bilberry"
   "Blackberry"
   "Blackcurrant"
   "Blueberry"
   "Boysenberry"
   "Buddha's hand (fingered citron)"
   "Crab apples"
   "Currant"
   "Cherry"
   "Cherimoya (Custard Apple)"
   "Cloudberry"
   "Coconut"
   "Cranberry"
   "Cucumber"
   "Damson"
   "Date"
   "Dragonfruit (or Pitaya)"
   "Durian"
   "Elderberry"
   "Feijoa"
   "Fig"
   "Goji berry"
   "Gooseberry"
   "Grape"
   "Raisin"
   "Grapefruit"
   "Guava"
   "Honeyberry"
   "Huckleberry"
   "Jabuticaba"
   "Jackfruit"
   "Jambul"
   "Japanese plum"
   "Jostaberry"
   "Jujube"
   "Juniper berry"
   "Kiwano (horned melon)"
   "Kiwifruit"
   "Kumquat"
   "Lemon"
   "Lime"
   "Loganberry"
   "Loquat"
   "Longan"
   "Lychee"
   "Mango"
   "Mangosteen"
   "Marionberry"
   "Melon"
   "Mulberry"
   "Nectarine"
   "Nance"
   "Orange"
   "Papaya"
   "Passionfruit"
   "Peach"
   "Pear"
   "Persimmon"
   "Plantain"
   "Plum"
   "Pineapple"
   "Pineberry"
   "Pomegranate"
   "Pomelo"
   "Quince"
   "Raspberry"
   "Salmonberry"
   "Redcurrant"
   "Salak"
   "Satsuma"
   "Soursop"
   "Strawberry"
   "Tamarillo"
   "Tamarind"
   "Tayberry"
   "Yuzu"])

(def names
  ["Isla"
   "Olivia"
   "Aurora"
   "Ada"
   "Charlotte"
   "Amara"
   "Maeve"
   "Cora"
   "Amelia"
   "Posie"
   "Luna"
   "Ophelia"
   "Ava"
   "Rose"
   "Eleanor"
   "Genevieve"
   "Alice"
   "Elodie"
   "Lucy"
   "Ivy"
   "Archie"
   "Milo"
   "Asher"
   "Jasper"
   "Silas"
   "Theodore"
   "Atticus"
   "Jack"
   "Aarav"
   "Finn"
   "Oliver"
   "Felix"
   "Henry"
   "Wyatt"
   "Aryan"
   "Leo"
   "Oscar"
   "Levi"
   "Ethan"
   "James"])

(def test-data
  (for [i (range 10000)]
    {:id i
     :code (str "PERSON:" i)
     :name (rand-nth names)
     :likes (rand-nth fruit)}))

(def replace-data
  (for [i (range 0 10000 3)]
    {:id i
     :code (str "PERSON:" i)
     :name (rand-nth names)
     :likes (rand-nth fruit)
     :extended true}))

(def c1 (c1/compound {:primary-index-def {:key :id}
                      :secondary-index-defs [{:key :code
                                              :index-type :compound/one-to-one}
                                             {:key :name
                                              :index-type :compound/one-to-many}
                                             {:key :likes
                                              :index-type :compound/one-to-many}]}))

(def c2 (c2/compound
         [{:index-type :one-to-one
           :kfn :id}
          {:index-type :one-to-one
           :kfn :code}
          {:index-type :one-to-many
           :kfn :name}
          {:index-type :one-to-many
           :kfn :likes}]))

(def c2* (c2/compound*
          [{:index-type :one-to-one
            :id :id
            :kfn :id}
           {:index-type :one-to-one
            :id :code
            :kfn :code}
           {:index-type :one-to-many
            :id :name
            :kfn :name}
           {:index-type :one-to-many
            :id :likes
            :kfn :likes}]))

#?(:clj
   (defn performance-test []
     (println "checking equality")
     (assert (= (c1/indexes-by-id (c1/add-items c1 test-data))
                (c2/add-items c2 test-data)
                (c2/add-items c2* test-data)))
     (println "profiling compound 1")
     (crit/with-progress-reporting
       (crit/bench
        (-> (c1/add-items c1 test-data)
            (c1/add-items replace-data))))
     (println "profiling compound c2 - macro")
     (crit/with-progress-reporting
       (crit/bench
        (-> (c2/add-items c2 test-data)
            (c2/add-items replace-data))))
     (println "profiling compound c2 - function")
     (crit/with-progress-reporting
       (crit/quick-bench
        (-> (c2/add-items c2* test-data)
            (c2/add-items replace-data))))))

#?(:cljs
   (defn performance-test []
     (let [c1 (c1/compound {:primary-index-def {:key :id}
                            :secondary-index-defs [{:key :code
                                                    :index-type :compound/one-to-one}
                                                   {:key :name
                                                    :index-type :compound/one-to-many}
                                                   {:key :likes
                                                    :index-type :compound/one-to-many}]})
           c2* (c2/compound* [{:index-type :one-to-one
                               :kfn :id}
                              {:index-type :one-to-one
                               :kfn :code}
                              {:index-type :one-to-many
                               :kfn :name}
                              {:index-type :one-to-many
                               :kfn :likes}])
           c2 (c2/compound [{:index-type :one-to-one
                             :kfn :id}
                            {:index-type :one-to-one
                             :kfn :code}
                            {:index-type :one-to-many
                             :kfn :name}
                            {:index-type :one-to-many
                             :kfn :likes}])]
       (println "checking equality")
       (assert (= (-> (c1/add-items c1 test-data)
                      (c1/add-items replace-data)
                      (c1/indexes-by-id))
                  (-> (c2/add-items c2* test-data)
                      (c2/add-items replace-data))
                  (-> (c2/add-items c2 test-data)
                      (c2/add-items replace-data))))
       (println "Timing compound 1")
       (simple-benchmark []
                         (-> (c1/add-items c1 test-data)
                             (c1/add-items replace-data)) 100)
       (println "Timing compound 2 - macro")
       (simple-benchmark []
                         (-> (c2/add-items c2 test-data)
                             (c2/add-items replace-data)) 100)
       (println "Timing compound 2 - function")
       (simple-benchmark []
                         (-> (c2/add-items c2* test-data)
                             (c2/add-items replace-data)) 100))))
