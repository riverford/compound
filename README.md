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

## Current Version: Compound2

```clojure
[riverford/compound "2019.09.14"]
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

Once you create a compound using `compound`, it returns a map extended with metadata to provide 3 additional functions.

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

#### Required keys
  `kfn` - the function to call to generate the key

#### Optional keys
  `id` - the id for the index in the compound
  `on-conflict` - called for primary indexes only, when an item with the same key is added.

### Index type: one-to-many

Demonstrated above, the one-to-many index will maintain a hash-map of `key -> set` pairs, where the
set contains all the items that share the key.

#### Required keys
  `kfn` - the function to call to generate the key
#### Optional keys
  `id` - the id for the index in the compound

### Index type: nested-to-one

Like a one-to-one index except that a nested hash-map of `path* -> item` is maintained.

#### Required keys
`path` - a seq of functions to call that will generate the path into the nested map for the item

#### Optional keys
`id` - the id for the index in the compound

```clojure
(-> (c/compound [{:index-type :one-to-one
                  :id :delivery-date-product
                  :kfn (juxt :delivery-date :product)}
                 {:index-type :nested-to-one
                  :path [:delivery-date :product]}])
    (c/add-items [{:delivery-date "2012-03-03" :product :bananas}
                  {:delivery-date "2012-03-03" :product :apples}
                  {:delivery-date "2012-03-04" :product :potatoes}
                  {:delivery-date "2012-03-04" :product :bananas}
                  {:delivery-date "2012-03-06" :product :potatoes}]))
;; => {:delivery-date-product
;;     {["2012-03-03" :bananas] {:delivery-date "2012-03-03", :product :bananas},
;;      ... },
;;     [:delivery-date :product]
;;     {"2012-03-03" {:bananas {:delivery-date "2012-03-03", :product :bananas},
;;                    :apples {:delivery-date "2012-03-03", :product :apples}},
;;      "2012-03-04" {:potatoes {:delivery-date "2012-03-04", :product :potatoes},
;;                    :bananas {:delivery-date "2012-03-04", :product :bananas}},
;;      "2012-03-06" {:potatoes {:delivery-date "2012-03-06", :product :potatoes}}}}
```

### Index type: nested-to-many

Like a one-to-many index except that a nested hash-map is `path* -> set` is maintained.

#### Required keys
`path` - a seq of functions to call that will generate the path into the nested map for the item

#### Optional keys:
`id` - the id for the index in the compound

### Index type: many-to-many

Like a one-to-many index, except the kfn should return a seq of values, and the item will be indexed under each of these.

#### Required keys:
  `kfn` - a functions to call that will generate a seq of keys for the item
#### Optional keys:
  `id` - the id for the index in the compound

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

## Influences

It is influenced by Christophe Grand's [indexed set](https://github.com/cgrand/indexed-set).

## License

Copyright © 2017 Riverford Organic Farmers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
