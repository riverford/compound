![Compound](https://raw.githubusercontent.com/riverford/compound/master/docs/img/compound.png)

**noun** *kɒmpaʊnd*

1. a thing that is composed of two or more separate elements; a mixture.

**verb** *kəmˈpaʊnd*

1. make up (a composite whole); constitute.
2. make (something bad) worse.

Compound is a micro structure for data used in reframe applications,
based on the idea that [worse is better](https://en.wikipedia.org/wiki/Worse_is_better).

It maintains a hash-map of indexes for your data. This has two benefits:

 1. Accessing your data in multiple ways (e.g. by name _and_ id) is easier than if you have a map just indexed by primary key (a common approach in reframe applications to keep subscriptions fast).
 2. The plain map indexes are easily inspectable in tools like re-frisk or re-frame-trace.

You can have as many indexes as you like. A bunch of useful index types are built in, and adding extra ones is not too much work.

There is no query engine, the aim instead is to make it possible to pick indexes that make queries trivial.

## Current Version:

```clojure
[riverford/compound "2019.10.04"]
```

For previous versions - see [changelog](https://github.com/riverford/compound/blob/master/CHANGELOG.md)

## Basic Usage


```clojure

(require '[compound2.core :as c])

(-> (c/compound [{:kfn :name}
                 {:kfn :colour}])

    (c/add-items [{:name :strawberry
                   :colour :red}

                  {:name :raspberry
                   :colour :red}

                  {:name :banana
                   :colour :yellow}]))
;; => {:name
;;     {:strawberry {:name :strawberry, :colour :red},
;;      :raspberry {:name :raspberry, :colour :red},
;;      :banana {:name :banana, :colour :yellow}},
;;     :colour
;;     {:red
;;      #{{:name :raspberry, :colour :red}
;;        {:name :strawberry, :colour :red}},
;;      :yellow #{{:name :banana, :colour :yellow}}}}
```

## Why is this useful with re-frame?

Over the lifetime of a re-frame app, the amount of data stored tends to grow, becoming more database-like, filling with sets of users, products, transactions and fruits.

As it grows, the maintainer of the app can either:

 1. Keep sets and vectors of maps, scanning over them `#(filter (comp #{:red} :colour) fruit)` in subscriptions and handlers.
 2. Look for a database solution, such as [datascript](https://github.com/tonsky/datascript), and run queries or entity lookups to find entities.

(1) is possibly the clearest solution, however processing time can scale both with the number of items and the number of subscriptions which perform scans. Writing filter/sequence comprehension code again and again in subscriptions and handlers can also get tedious.

(2) is a great solution for querying but is not necessarily a perfect fit for re-frame subscriptions.
The datascript db is opaque to visualisation tools, and although solutions like [posh](https://github.com/mpdairy/posh) exist, out of the box performance of
subscriptions is typically bad because *every* subscription depends on the db.

Using compound is a possible third option, as close as possible to (1). It adds a convention to the maps and provides support for multiple access patterns, different cardinalities, composite keys, whilst staying open to extension.

## API

Create a compound using `compound`. It will returns a map extended with metadata to implement `add-items`, `remove-keys` and `items`.

 - `compound` [primary-index-opts & secondary-index-opts] => compound
 - `add-items` [compound items-to-add] => new-compound
 - `remove-keys` [compound keys-to-remove] => new-compound
 - `items` [compound] => seq of items in compound

### Add items

To add items, call `add-items`. Items will be added to all the defined indexes.

If you add an item with the same primary key as an item already in the compound, the `on-conflict` function will be called to get a new item. The old item will be removed from all indexes, and the new item added.

```clojure
(-> (c/compound [{:id :by-name
                  :index-type :one-to-one
                  :kfn :name
                  :on-conflict (fn [a b] (merge a b))}
                 {:id :by-colour
                  :index-type :one-to-many
                  :kfn :colour}
                 {:id :by-tastiness
                  :index-type :one-to-many
                  :kfn :tastiness}])

    (c/add-items [{:name :strawberry
                   :colour :red
                   :tastiness 4}

                  {:name :strawberry
                   :tastiness 5}

                  {:name :banana
                   :tastiness 3
                   :colour :yellow}]))

;; => {:by-name
;;     {:strawberry {:name :strawberry, :colour :red, :tastiness 5},
;;      :banana {:name :banana, :tastiness 3, :colour :yellow}},
;;     :by-colour
;;     {:red #{{:name :strawberry, :colour :red, :tastiness 5}},
;;      :yellow #{{:name :banana, :tastiness 3, :colour :yellow}}},
;;     :by-tastiness
;;     {5 #{{:name :strawberry, :colour :red, :tastiness 5}},
;;      3 #{{:name :banana, :tastiness 3, :colour :yellow}}}}
```
### Remove keys

To remove items from a compound, call `remove-keys` with the primary keys of the items you would like to remove.

```clojure
(-> (c/compound [{:id :by-name ;; defaults to :kfn if :id not provided
                  :index-type :one-to-one ;; defaults to :one-to-one for primary index
                  :kfn :name
                  :on-conflict (fn [a b] (merge a b))} ;; defaults to (fn [old new] new)
                 {:id :by-colour
                  :index-type :one-to-many ;; defaults to :one-to-many for secondary index
                  :kfn :colour}
                 {:id :by-tastiness
                  :index-type :one-to-many ;; defaults to :one-to-many for secondary index
                  :kfn :tastiness}])

    (c/add-items [{:name :strawberry
                   :colour :red
                   :tastiness 4}

                  {:name :strawberry
                   :tastiness 5}

                  {:name :banana
                   :tastiness 3
                   :colour :yellow}])
    (c/remove-keys [:strawberry]))

;; => {:by-name {:banana {:name :banana, :tastiness 3, :colour :yellow}},
;;     :by-colour
;;     {:yellow #{{:name :banana, :tastiness 3, :colour :yellow}}},
;;     :by-tastiness {3 #{{:name :banana, :tastiness 3, :colour :yellow}}}}
```

### Items

To list all the items in the compound, without the indexes, use `items`. This is useful for e.g. serialising or storing the compound for later use.

```clojure
(-> (c/compound [{:id :by-name
                  :index-type :one-to-one
                  :kfn :name
                  :on-conflict (fn [a b] (merge a b))}
                 {:id :by-colour
                  :index-type :one-to-many
                  :kfn :colour}
                 {:id :by-tastiness
                  :index-type :one-to-many
                  :kfn :tastiness}])

    (c/add-items [{:name :strawberry
                   :colour :red
                   :tastiness 4}

                  {:name :strawberry
                   :tastiness 5}

                  {:name :banana
                   :tastiness 3
                   :colour :yellow}])
    (c/items))

;; => ({:name :strawberry, :colour :red, :tastiness 5}
;;     {:name :banana, :tastiness 3, :colour :yellow})
```

## Different types of index

### Index type: one-to-one

Demonstrated above, the one-to-one index will maintain a hash-map of `key -> item` pairs.
It is the default index type for primary indexes.

#### Required keys
  - `kfn` - the function to call to generate the key

#### Optional keys
  - `id` - the id for the index in the compound
  - `on-conflict` - called for primary indexes only, when an item with the same key is added.

### Index type: one-to-many

Demonstrated above, the one-to-many index will maintain a hash-map of `key -> set` pairs, where the
set contains all the items that share the key.

#### Required keys
  - `kfn` - the function to call to generate the key

#### Optional keys
  - `id` - the id for the index in the compound

### Index type: nested-to-one

Like a one-to-one index except that a nested hash-map of `path* -> item` is maintained.

#### Required keys
 - `path` - a seq of functions to call that will generate the path into the nested map for the item

#### Optional keys
 - `id` - the id for the index in the compound

```clojure
(-> (c/compound [{:index-type :one-to-one
                  :id :delivery
                  :kfn (juxt :customer :delivery-date :product)}
                 {:index-type :nested-to-one
                  :path [:customer :delivery-date :product]}])
    (c/add-items [{:customer 1 :delivery-date "2012-03-03" :product :bananas}
                  {:customer 1 :delivery-date "2012-03-03" :product :apples}
                  {:customer 1 :delivery-date "2012-03-10" :product :potatoes}
                  {:customer 2 :delivery-date "2012-03-04" :product :bananas}
                  {:customer 2 :delivery-date "2012-03-11" :product :potatoes}]))
;; => {:delivery
;;     {[1 "2012-03-03" :bananas]
;;      {:customer 1, :delivery-date "2012-03-03", :product :bananas},
;;      [1 "2012-03-03" :apples]
;;      {:customer 1, :delivery-date "2012-03-03", :product :apples},
;;      [1 "2012-03-10" :potatoes]
;;      {:customer 1, :delivery-date "2012-03-10", :product :potatoes},
;;      [2 "2012-03-04" :bananas]
;;      {:customer 2, :delivery-date "2012-03-04", :product :bananas},
;;      [2 "2012-03-11" :potatoes]
;;      {:customer 2, :delivery-date "2012-03-11", :product :potatoes}},
;;     [:customer :delivery-date :product]
;;     {1
;;      {"2012-03-03"
;;       {:bananas
;;        {:customer 1, :delivery-date "2012-03-03", :product :bananas},
;;        :apples
;;        {:customer 1, :delivery-date "2012-03-03", :product :apples}},
;;       "2012-03-10"
;;       {:potatoes
;;        {:customer 1, :delivery-date "2012-03-10", :product :potatoes}}},
;;      2
;;      {"2012-03-04"
;;       {:bananas
;;        {:customer 2, :delivery-date "2012-03-04", :product :bananas}},
;;       "2012-03-11"
;;       {:potatoes
;;        {:customer 2, :delivery-date "2012-03-11", :product :potatoes}}}}};; => {:delivery-date-product
;;     {[1 "2012-03-03" :bananas]
;;      {:customer 1, :delivery-date "2012-03-03", :product :bananas},
;;      [1 "2012-03-03" :apples]
;;      {:customer 1, :delivery-date "2012-03-03", :product :apples},
;;      [1 "2012-03-10" :potatoes]
;;      {:customer 1, :delivery-date "2012-03-10", :product :potatoes},
;;      [2 "2012-03-04" :bananas]
;;      {:customer 2, :delivery-date "2012-03-04", :product :bananas},
;;      [2 "2012-03-11" :potatoes]
;;      {:customer 2, :delivery-date "2012-03-11", :product :potatoes}},
;;     [:customer :delivery-date :product]
;;     {1
;;      {"2012-03-03"
;;       {:bananas
;;        {:customer 1, :delivery-date "2012-03-03", :product :bananas},
;;        :apples
;;        {:customer 1, :delivery-date "2012-03-03", :product :apples}},
;;       "2012-03-10"
;;       {:potatoes
;;        {:customer 1, :delivery-date "2012-03-10", :product :potatoes}}},
;;      2
;;      {"2012-03-04"
;;       {:bananas
;;        {:customer 2, :delivery-date "2012-03-04", :product :bananas}},
;;       "2012-03-11"
;;       {:potatoes
;;        {:customer 2, :delivery-date "2012-03-11", :product :potatoes}}}}};; =>

(-> (c/compound [{:index-type :one-to-one
                  :id :delivery
                  :kfn (juxt :customer :delivery-date :product)}
                 {:index-type :nested-to-many
                  :path [:customer :delivery-date]}])
    (c/add-items [{:customer 1 :delivery-date "2012-03-03" :product :bananas}
                  {:customer 1 :delivery-date "2012-03-03" :product :apples}
                  {:customer 1 :delivery-date "2012-03-10" :product :potatoes}
                  {:customer 2 :delivery-date "2012-03-04" :product :bananas}
                  {:customer 2 :delivery-date "2012-03-11" :product :potatoes}]))
```

### Index type: nested-to-many

Like a one-to-many index except that a nested hash-map is `path* -> set` is maintained.

#### Required keys
 - `path` - a seq of functions to call that will generate the path into the nested map for the item

#### Optional keys:
 - `id` - the id for the index in the compound

``` clojure
(-> (c/compound [{:index-type :one-to-one
                  :id :delivery
                  :kfn (juxt :customer :delivery-date :product)}
                 {:index-type :nested-to-many
                  :path [:customer :delivery-date]}])
    (c/add-items [{:customer 1 :delivery-date "2012-03-03" :product :bananas}
                  {:customer 1 :delivery-date "2012-03-03" :product :apples}
                  {:customer 1 :delivery-date "2012-03-10" :product :potatoes}
                  {:customer 2 :delivery-date "2012-03-04" :product :bananas}
                  {:customer 2 :delivery-date "2012-03-11" :product :potatoes}]))
;; => {:delivery
;;     {[1 "2012-03-03" :bananas]
;;      {:customer 1, :delivery-date "2012-03-03", :product :bananas},
;;      [1 "2012-03-03" :apples]
;;      {:customer 1, :delivery-date "2012-03-03", :product :apples},
;;      [1 "2012-03-10" :potatoes]
;;      {:customer 1, :delivery-date "2012-03-10", :product :potatoes},
;;      [2 "2012-03-04" :bananas]
;;      {:customer 2, :delivery-date "2012-03-04", :product :bananas},
;;      [2 "2012-03-11" :potatoes]
;;      {:customer 2, :delivery-date "2012-03-11", :product :potatoes}},
;;     [:customer :delivery-date]
;;     {1
;;      {"2012-03-03"
;;       #{{:customer 1, :delivery-date "2012-03-03", :product :bananas}
;;         {:customer 1, :delivery-date "2012-03-03", :product :apples}},
;;       "2012-03-10"
;;       #{{:customer 1,
;;          :delivery-date "2012-03-10",
;;          :product :potatoes}}},
;;      2
;;      {"2012-03-04"
;;       #{{:customer 2, :delivery-date "2012-03-04", :product :bananas}},
;;       "2012-03-11"
;;       #{{:customer 2,
;;          :delivery-date "2012-03-11",
;;          :product :potatoes}}}}}
```

### Index type: many-to-many

Like a one-to-many index, except the kfn should return a seq of values, and the item will be indexed under each of these.

#### Required keys:
  - `kfn` - a functions to call that will generate a seq of keys for the item

#### Optional keys:
  - `id` - the id for the index in the compound

```clojure
(-> (c/compound [{:kfn :id}
                 {:kfn :tags
                  :index-type :many-to-many}])
    (c/add-items [{:id 1
                   :name "Peanuts"
                   :tags ["Nut" "New" "Yellow"]}
                  {:id 2
                   :name "Bananas"
                   :tags ["Fruit" "Yellow"]}
                  {:id 3
                   :name "Plums"
                   :tags ["Purple" "Fruit" "New"]}
                  {:id 4
                   :name "Kiwi"
                   :tags ["Green" "Fruit"]}]))
;; => {:id
;;     {1 {:id 1, :name "Peanuts", :tags ["Nut" "New" "Yellow"]},
;;      2 {:id 2, :name "Bananas", :tags ["Fruit" "Yellow"]},
;;      3 {:id 3, :name "Plums", :tags ["Purple" "Fruit" "New"]},
;;      4 {:id 4, :name "Kiwi", :tags ["Green" "Fruit"]}},
;;     :tags
;;     {"Nut" #{{:id 1, :name "Peanuts", :tags ["Nut" "New" "Yellow"]}},
;;      "New"
;;      #{{:id 3, :name "Plums", :tags ["Purple" "Fruit" "New"]}
;;        {:id 1, :name "Peanuts", :tags ["Nut" "New" "Yellow"]}},
;;      "Yellow"
;;      #{{:id 1, :name "Peanuts", :tags ["Nut" "New" "Yellow"]}
;;        {:id 2, :name "Bananas", :tags ["Fruit" "Yellow"]}},
;;      "Fruit"
;;      #{{:id 3, :name "Plums", :tags ["Purple" "Fruit" "New"]}
;;        {:id 4, :name "Kiwi", :tags ["Green" "Fruit"]}
;;        {:id 2, :name "Bananas", :tags ["Fruit" "Yellow"]}},
;;      "Purple" #{{:id 3, :name "Plums", :tags ["Purple" "Fruit" "New"]}},
;;      "Green" #{{:id 4, :name "Kiwi", :tags ["Green" "Fruit"]}}}}
```

## Macros vs function implementation

The default implementation for compound is now a macro. This gives about a 10% speedup over the function implementation (by splicing all the indexes into the loop variables of a single loop/recur).

If you need to use dynamic index definitions, or to store the index definitions in a var rather than passing as a literal map, you can use the function implementation, `compound*`

```clojure
(def indexes [{:kfn :name} {:kfn :colour}])

;; Macro implementation only works for literal index definitions

(-> (c/compound indexes)
    (c/add-items [{:name :strawberry
                   :colour :red}

                  {:name :raspberry
                   :colour :red}

                  {:name :banana
                   :colour :yellow}]))

;; Unhandled clojure.lang.Compiler$CompilerException
;; Caused by java.lang.IllegalArgumentException
;;   Don't know how to create ISeq from: clojure.lang.Symbol

;; Need to use the function implementation instead.

(-> (c/compound* indexes)
    (c/add-items [{:name :strawberry
                   :colour :red}

                  {:name :raspberry
                   :colour :red}

                  {:name :banana
                   :colour :yellow}]))
;; => {:name
;;     {:strawberry #{{:name :strawberry, :colour :red}},
;;      :raspberry #{{:name :raspberry, :colour :red}},
;;      :banana #{{:name :banana, :colour :yellow}}},
;;     :colour
;;     {:red
;;      #{{:name :raspberry, :colour :red}
;;        {:name :strawberry, :colour :red}},
;;      :yellow #{{:name :banana, :colour :yellow}}}}
```

### Additional indexes

To provide additional indexers to, implement `compound2.core/indexer`

### Performance

Compound 2 improves on performance over compound 1 primarily via use of a macro.

The `compound2.performance` namespace contains a performance test that sets up a compound with 4 indexes, and performs 10,000 add operations and ~3,300 replace operations.

The criterium results on a 2015 MBP are as follows

```
Compound 1:

Evaluation count : 1320 in 60 samples of 22 calls.
             Execution time mean : 49.062765 ms
    Execution time std-deviation : 1.435390 ms
   Execution time lower quantile : 46.896612 ms ( 2.5%)
   Execution time upper quantile : 51.598378 ms (97.5%)
                   Overhead used : 10.219163 ns

Compound 2, macro implementation:

Evaluation count : 1560 in 60 samples of 26 calls.
             Execution time mean : 38.588245 ms
    Execution time std-deviation : 681.449051 µs
   Execution time lower quantile : 37.948861 ms ( 2.5%)
   Execution time upper quantile : 40.416067 ms (97.5%)
                   Overhead used : 10.219163 ns

Compound 2: function implementation:

Evaluation count : 18 in 6 samples of 3 calls.
             Execution time mean : 48.717964 ms
    Execution time std-deviation : 444.575917 µs
   Execution time lower quantile : 48.252249 ms ( 2.5%)
   Execution time upper quantile : 49.262951 ms (97.5%)
                   Overhead used : 10.219163 ns
```

And the simple benchmark js as follows.

```
Timing compound 1
[], (-> (c1/add-items c1 test-data) (c1/add-items replace-data)), 100 runs, 11316 msecs
Timing compound 2 - macro
[], (-> (c2/add-items c2 test-data) (c2/add-items replace-data)), 100 runs, 9554 msecs
Timing compound 2 - function
[], (-> (c2/add-items c2* test-data) (c2/add-items replace-data)), 100 runs, 10405 msecs
```

## Influences

It is influenced by Christophe Grand's [indexed set](https://github.com/cgrand/indexed-set).

## License

Copyright © 2017 Riverford Organic Farmers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
