(ns compound.docs
  (:require  [clojure.test :as t]
             [lucid.publish :as publish]
             [lucid.publish.theme :as theme]
             [clojure.java.io :as io]
             [hara.io.project :as project]
             [hara.test :refer [fact throws-info]]
             [clojure.spec.alpha :as s]))


[[:chapter {:title "Getting started"}]]

(require '[compound.core :as c])

"We're gonna have to handle a lot of fruit today.
We should probably set up somewhere to store all the information about it."

(fact
  (c/compound {:primary-index-def {:key :id}}) =>
  
  {:primary-index-def {:key :id
                       :on-conflict :compound/replace},
   :primary-index {},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})


[[:chapter {:title "Operating on the compound"}]]


[[:section {:title "Adding"}]]

"This compound is a feeling a bit empty, time to add some data."

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])) =>

  {:primary-index-def {:on-conflict :compound/replace, :key :id},
   :primary-index {1 {:id 1, :name "bananoes"},
                   2 {:id 2, :name "grapes"},
                   3 {:id 3, :name "tomatoes"}},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

[[:section {:title "Removing"}]]

"Wait, what are bananoes?? Let's get rid of them."

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])) =>
  
  {:primary-index-def {:on-conflict :compound/replace, :key :id},
   :primary-index {3 {:id 3, :name "tomatoes"},
                   2 {:id 2, :name "grapes"}},
   :secondary-indexes-by-id {},
   :secondary-index-defs-by-id {}})

[[:section {:title "Reading"}]]

"Looking at the whole data structure every time is a bit visually distracting."

"Get all the indexes using `indexes-by-id`, and particular indexes using `index`"

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/indexes-by-id)) =>

  {:id {3 {:id 3, :name "tomatoes"},
        2 {:id 2, :name "grapes"}}})

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/index :id)) =>

  {3 {:id 3, :name "tomatoes"},
   2 {:id 2, :name "grapes"}})

"Because these built in indexes are just maps, we can operate on them using standard clojure functions"

(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "bananoes"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}])
      (c/remove-keys [1])
      (c/index :id)
      (get 2)) =>
  
  {:id 2, :name "grapes"})

"Isn't that just grapes!"

[[:chapter {:title "Secondary indexes"}]]

"Looking up fruit by id is all well and good, but what if we want to be able to find all fruits of a certain colour, to make a seasonal display? "

"We'll need to add a secondary index, on `:colour`."

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :colour}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

"*Note: secondary indexes don't have to be added at construction time. Adding them later will index all of the items currently in the compound into the new secondary index.*"
(fact
  (-> (c/compound {:primary-index-def {:key :id}})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/add-secondary-index {:key :colour})
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

[[:section {:title "Built in index types"}]]

"There are a bunch of different index types built in, to cover the common use cases"

[[:subsection {:title "One to many"}]]

"The one to many index is used when there may be more than one item with the same `:key`. The index is a map with a set for each key value."

"This is also, the default secondary index type if the `:index-type` is not specified, so the
result here may be familiar."

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :colour
                                           :index-type :compound/one-to-many}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:colour
   {"green" #{{:id 1, :name "grapes", :colour "green"}},
    "yellow" #{{:id 2, :name "bananas", :colour "yellow"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})

[[:subsection {:title "One to one"}]]

"The one to one index is used when there can only be one item for each `:key`. An error is thrown if an item with a duplicate key is added without first removing the existing one"


(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :name
                                           :index-type :compound/one-to-one}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green"}
                    {:id 2 :name "bananas" :colour "yellow"}])
      (c/indexes-by-id)) =>
  
  {:name
   {"grapes" {:id 1, :name "grapes", :colour "green"},
    "bananas" {:id 2, :name "bananas", :colour "yellow"}},
   :id
   {1 {:id 1, :name "grapes", :colour "green"},
    2 {:id 2, :name "bananas", :colour "yellow"}}})


[[:subsection {:title "Many to one"}]]

"The many to one index is used when there an item's `:key` can have multiple values, but each value can occur at most once"


(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :aka
                                           :index-type :compound/many-to-one}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :aka #{"green bunches of joy"}}
                    {:id 2 :name "bananas" :colour "yellow" :aka #{"yellow boomerangs" "monkey nourishers"}}])
      (c/indexes-by-id)) =>

  {:aka
   {"monkey nourishers"
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}},
    "yellow boomerangs"
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}},
    "green bunches of joy"
    {:id 1, :name "grapes", :colour "green", :aka #{"green bunches of joy"}}},
   :id
   {1 {:id 1, :name "grapes", :colour "green", :aka #{"green bunches of joy"}},
    2
    {:id 2,
     :name "bananas",
     :colour "yellow",
     :aka #{"monkey nourishers" "yellow boomerangs"}}}})

[[:subsection {:title "Many to many"}]]

"The many to many index is used when there an item's `:key` can have multiple values, and each value can occur multiple times"

"What goes with cheese?"

(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:key :goes-with
                                           :index-type :compound/many-to-many}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :goes-with #{"cheese"}}
                    {:id 2 :name "figs" :colour "green" :goes-with #{"cheese" "ice cream"}}
                    {:id 3 :name "bananas" :colour "yellow" :goes-with #{"pancakes" "ice cream"}}])
      (c/index :goes-with)
      (get "cheese")) =>

  #{{:id 2, :name "figs", :colour "green", :goes-with #{"ice cream" "cheese"}}
    {:id 1, :name "grapes", :colour "green", :goes-with #{"cheese"}}})

"(the answer is not bananas)"

[[:subsection {:title "Nested indexes"}]]

"The nested indexes form a composite key using nesting"

"The is useful when you want to index something by this *then* by *that*"

[[:subsubsection {:title "One to many (nested)"}]]
(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:keys [:colour :category]
                                           :index-type :compound/one-to-many-nested}]})
      (c/add-items [{:id 1 :name "grapes" :colour "green" :category "berries"}
                    {:id 2 :name "sloe" :colour "blue" :category "berries"}
                    {:id 3 :name "orange" :colour "orange" :category "citrus"}
                    {:id 4 :name "lemon" :colour "yellow" :category "citrus"}
                    {:id 5 :name "lime" :colour "green" :category "citrus"}])
      (c/index [:colour :category])) =>

  {"orange" {"citrus" #{{:id 3, :name "orange", :colour "orange", :category "citrus"}}},
   "green" {"berries" #{{:id 1, :name "grapes", :colour "green", :category "berries"}},
            "citrus" #{{:id 5, :name "lime", :colour "green", :category "citrus"}}},
   "yellow" {"citrus" #{{:id 4, :name "lemon", :colour "yellow", :category "citrus"}}},
   "blue" {"berries" #{{:id 2, :name "sloe", :colour "blue", :category "berries"}}}})

[[:subsubsection {:title "One to one (nested)"}]]
(fact
  (-> (c/compound {:primary-index-def {:key :id}
                   :secondary-index-defs [{:keys [:category :display-index]
                                           :index-type :compound/one-to-one-nested}]})
      (c/add-items [{:id 1 :name "grapes" :display-index 1 :category "berries"}
                    {:id 2 :name "sloe" :display-index 2 :category "berries"}
                    {:id 3 :name "orange" :display-index 1 :category "citrus"}
                    {:id 4 :name "lemon" :display-index 2 :category "citrus"}
                    {:id 5 :name "lime" :display-index 3 :category "citrus"}])
      (c/index [:category :display-index])) =>

  {"berries"
   {1 {:id 1, :name "grapes", :display-index 1, :category "berries"},
    2 {:id 2, :name "sloe", :display-index 2, :category "berries"}},
   "citrus"
   {2 {:id 4, :name "lemon", :display-index 2, :category "citrus"},
    3 {:id 5, :name "lime", :display-index 3, :category "citrus"},
    1 {:id 3, :name "orange", :display-index 1, :category "citrus"}}})

[[:chapter {:title "Handling conflict"}]]

"Sometimes we need more control over what happens when we add an item with a key that already exists to the compound. Compound provides some built in behaviour and an extension point for customisation."

[[:section {:title "Replace"}]]

"Using `:compound/replace` for `:on-conflict` will resolve conflicts by removing the previous item with that key from the primary index and all secondary indexes before adding the new item"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/replace}
                   :secondary-index-defs [{:key :name}]})
      (c/add-items [{:id 1 :name "bananas"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}
                    {:id 3 :name "oranges"}])
      (c/indexes-by-id)) =>

  {:name
   {"grapes" #{{:id 2, :name "grapes"}},
    "oranges" #{{:id 3, :name "oranges"}},
    "bananas" #{{:id 1, :name "bananas"}}},
   :id
   {1 {:id 1, :name "bananas"},
    2 {:id 2, :name "grapes"},
    3 {:id 3, :name "oranges"}}})

[[:section {:title "Throw"}]]

"Using `:compound/throw` for `:on-conflict` will throw an error if we try and add an item with a key that already exists"

(fact
  (throws-info {:existing-item {:id 3 :name "tomatoes"}
                :new-item {:id 3 :name "oranges"}}
               (-> (c/compound {:primary-index-def {:key :id
                                                    :on-conflict :compound/throw}
                                :secondary-index-defs [{:key :name}]})
                   (c/add-items [{:id 1 :name "bananas"}
                                 {:id 2 :name "grapes"}
                                 {:id 3 :name "tomatoes"}
                                 {:id 3 :name "oranges"}])
                   (c/indexes-by-id))))

[[:section {:title "Merge"}]]

"Using `:compound/merge` for `:on-conflict` will call `clojure.core/merge` on the previous item and the new item when the key already exists"

(fact
  (-> (c/compound {:primary-index-def {:key :id
                                       :on-conflict :compound/merge}
                   :secondary-index-defs [{:key :name}]})
      (c/add-items [{:id 1 :name "bananas"}
                    {:id 2 :name "grapes"}
                    {:id 3 :name "tomatoes"}
                    {:id 3 :colour "red"}])
      (c/indexes-by-id)) =>
  
  {:name
   {"grapes" #{{:id 2, :name "grapes"}},
    "bananas" #{{:id 1, :name "bananas"}},
    "tomatoes" #{{:id 3, :name "tomatoes", :colour "red"}}},
   :id
   {1 {:id 1, :name "bananas"},
    2 {:id 2, :name "grapes"},
    3 {:id 3, :name "tomatoes", :colour "red"}}})

[[:section {:title "Custom"}]]

"Custom merge behaviour can also be defined. This is covered in the next section"

[[:chapter {:title "Extension"}]]

[[:section {:title "Custom keys"}]]
[[:section {:title "Custom merge behaviour"}]]
[[:section {:title "Custom indexes"}]]

"Compound can be extended with additional indexes, for example if you know of a data structure that provides optimized access for the access pattern that you will use (e.g. one of https://github.com/michalmarczyk excellent data structures)"

"To extend, implement the following multimethods from the `compound.secondary-indexes` namespace." 

"
* `spec` - the spec for the index definition
* `empty` - the initial value of the index
* `id` - to get a unique id from the index definition
* `add` - to add items to the index, called after items are added to the primary index
* `remove` - to remove items from the index, called when items are removed from the primary index
" 

[[{:hidden? true}]]

(comment

  (publish/load-settings)
  (publish/copy-assets)
  (publish/publish-all))
