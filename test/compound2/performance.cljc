(ns compound2.performance
  (:require [compound.secondary-indexes :as csi]
            [compound.core :as c1]
           #?(:cljs [compound2.core :as c2 :include-macros true])
           #?(:clj [compound2.core :as c2])
           #?(:clj [criterium.core :as crit])))

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
  (for [i (range 50000)]
    {:id i
     :name (rand-nth names)
     :likes (rand-nth fruit)}))

(def c1 (c1/compound {:primary-index-def {:key :id}
                      :secondary-index-defs [{:key :name
                                              :index-type :compound/one-to-many}
                                             {:key :likes
                                              :index-type :compound/one-to-many}]}))

(def c2 (c2/compound
         [{:index-type :unique
           :id :id
           :kfn :id}
          {:index-type :multi
           :id :name
           :kfn :name}
          {:index-type :multi
           :id :likes
           :kfn :likes}]))

(def c3 (c2/compound*
         [{:index-type :unique
           :id :id
           :kfn :id}
          {:index-type :multi
           :id :name
           :kfn :name}
          {:index-type :multi
           :id :likes
           :kfn :likes}]))

(def c4 (c2/compound2*
         [{:index-type :unique
           :id :id
           :kfn :id}
          {:index-type :multi
           :id :name
           :kfn :name}
          {:index-type :multi
           :id :likes
           :kfn :likes}]))


(def c5 (c2/compound3*
        [{:index-type :unique
          :id :id
          :kfn :id}
         {:index-type :multi
          :id :name
          :kfn :name}
         {:index-type :multi
          :id :likes
          :kfn :likes}]))

(c2/add-items c5 (take 10 test-data))


#?(:clj (defn performance-test []
          (println "checking equality")
          (assert (= (c2/add-items c2 test-data)
                     (c2/add-items c3 test-data)
                     (c2/add-items c4 test-data)
                     (c2/add-items c5 test-data)
                     (c1/indexes-by-id (c1/add-items c1 test-data))))
          (println "profiling compound 1")
          (crit/with-progress-reporting
            (crit/quick-bench
             (c1/add-items c1 test-data)))
          (println "profiling compound 2 - macro")
          (crit/with-progress-reporting
            (crit/quick-bench
             (c2/add-items c2 test-data)))
          (println "profiling compound 2 - function")
          (crit/with-progress-reporting
            (crit/quick-bench
             (c2/add-items c3 test-data)))
          (println "profiling compound 5")
          (crit/with-progress-reporting
            (crit/quick-bench
             (c2/add-items c5 test-data)))))

#?(:cljs (defn performance-test []
           (println "checking equality")
           (assert (= (c2/add-items c2 test-data)
                      (c1/indexes-by-id (c1/add-items c1 test-data))))))
(performance-test)
