![Compound](https://raw.githubusercontent.com/riverford/compound/master/docs/img/compound.png)

**noun** *kɒmpaʊnd*

1. a thing that is composed of two or more separate elements; a mixture.

**verb** *kəmˈpaʊnd*

1. make up (a composite whole); constitute.
2. make (something bad) worse. 

Compound is a micro structure for data used in reframe applications, 
based on the idea that [worse is better](https://en.wikipedia.org/wiki/Worse_is_better). 

It maintains a plain hash-map of indexes for your data. This has two benefits: 
 1. Accessing your data in multiple ways (e.g. by name _and_ id) is easier than if you have a map just indexed by primary key (a common approach in reframe applications to keep subscriptions fast). 
 2. The plain map indexes are easily inspectable in tools like re-frisk or re-frame-trace.

You can have as many indexes as you like. Some index types are built in, but adding extra ones is straight forward. 

There is no query engine. 

## Basic Usage

```clojure

(require [compound.core :as c]) 

(def fruit
  (-> (c/compound {:primary-index-def {:key :name}
                   :secondary-index-defs [{:key :colour}]})
          
      (c/add-items #{{:name :strawberry
                      :colour :red}
                      
                     {:name :raspberry
                      :colour :red}

                     {:name :banana
                      :colour :yellow}})))

(get (c/index fruit :name) :banana)

;; {:name :banana
;;  :colour :yellow}

(get (c/index patterns :colour) :red)

;; #{{:name :strawberry
;;    :colour :red}
;;   {:name :raspberry
;;    :colour :red}}

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


```clojure
(require '[compound.core :as c])

(def app-db {:fruit (c/compound {:primary-index-def {:key :name} 
                                 :secondary-index-defs [{:key :colour}]})
             ... })
                                 
(reg-sub :fruit
  (fn [db] 
    (get db :fruit)))
   
(reg-sub :fruits-with-colour
  :<- [:fruit]
  (fn [fruit [_ selected-colour]]
    (get (c/index fruit :colour) selected-colour)))
```

## Documentation

Full example-based documentation, covering the built-in indexes, extending with additional indexes, composite keys, handling duplicates and custom key functions, etc is found on the [github pages](https://riverford.github.io/compound)

## Influences 

It is influenced by Christophe Grand's [indexed set](https://github.com/cgrand/indexed-set). 

## License

Copyright © 2017 Riverford Organic Farmers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
