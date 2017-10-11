![Compound](https://raw.githubusercontent.com/danielneal/compound/master/compound.png)

Compound is a micro structure for data used in reagent (and re-frame etc) applications.

It is not as expressive as datascript, but it provides extra functionality over storing data in a map indexed by one thing. It is useful if you have data that has more than one access pattern, and want to avoid repeated linear scans, but for some reason datascript is not an ideal fit (e.g. with reframe, to avoid recomputing every query whenever the database changes). 

```

  less expressive                        more expressive

   *-*-----------------------------------------*
   ^ ^                                         ^
   | |                                         |
   | * compound                                *  datascript
   |
   * just a map


```

## Usage

Say I have a bunch of data about dressmaking patterns.

```clojure
(def pattern-data
  #{{:id 1
     :source :instituto-di-moda
     :difficulty :medium
     :appropriate-materials #{:cotton :linen}
     :pattern :bodice-basic}

    {:id 2
     :source :instituto-di-moda
     :difficulty :easy
     :appropriate-materials #{:cotton}
     :pattern :shirt-basic}

    {:id 3
     :source :instituto-di-moda
     :difficulty :easy
     :appropriate-materials #{:cotton :linen}
     :pattern :bodice-dartless}

    {:id 4
     :source :winifred-aldrich
     :difficulty :medium
     :appropriate-materials #{:silk :cotton}
     :pattern :dress-princess-seam}

    {:id 5
     :source :winifred-aldrich
     :pattern :winter-coat
     :appropriate-materials #{:wool}
     :difficulty :hard}})

```
And I want to access them (in various bits of the ui), by source, by difficulty, by id and appropriate material.

First, set up an empty compound with the required index definitions. 

```clojure
(require [compound.core :as c])
(require [compound.indexes.one-to-many])
(require [compound.indexes.many-to-many])

(def patterns
  (c/empty-compound #{#:compound.index{:type :compound.index.types/primary
                                       :conflict-behaviour :compound.conflict-behaviours/upsert
                                       :key-fn :id
                                       :id :id}

                      #:compound.index{:type :compound.index.types/one-to-many
                                       :key-fn :source
                                       :id :source}

                      #:compound.index{:type :compound.index.types/one-to-many
                                       :key-fn :difficulty
                                       :id :difficulty}
                                       
                      #:compound.index{:type :compound.index.types/many-to-many
                                       :key-fn :appropriate-materials
                                       :id :appropriate-materials}}))

```

Then add and remove items as follows.

```clojure

(-> (c/add patterns pattern-data) ; add the items
    (c/remove [1]                 ; remove using the primary key
    (c/indexes))                  ; get the index data out 

#:compound{:difficulty
            {:hard
             #{{:id 5,
                :source :winifred-aldrich,
                :pattern :winter-coat,
                :appropriate-materials #{:wool},
                :difficulty :hard}},
             :medium
             #{{:id 4,
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
           :source
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
             #{{:id 4,
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
             #{{:id 3,
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
             3
             {:id 3,
              :source :instituto-di-moda,
              :difficulty :easy,
              :appropriate-materials #{:cotton :linen},
              :pattern :bodice-dartless},
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
              :pattern :shirt-basic}}}
```

## Built-in indexes

### [One to One](https://github.com/danielneal/compound/blob/master/src/compound/indexes/one_to_one.clj)

Use when `(key-fn item)` returns a single key for each item.
Stores the item against the `(key-fn item)`; throws if `(key-fn item)` returns a duplicate key to a previous item. 

### [One to Many](https://github.com/danielneal/compound/blob/master/src/compound/indexes/one_to_many.clj)

Use when `(key-fn item)` returns a single key for each item, but duplicates are permitted.
Stores a set of items against `(key-fn item)`; if `(key-fn item)` returns a duplicate, add to the set of items stored against `(key-fn item)`

### [Many to Many](https://github.com/danielneal/compound/blob/master/src/compound/indexes/many_to_many.clj)

Use when `(key-fn item)` returns multiple values, and the index will store the item against each of them.


## Extending with additional custom indexes

Compound can be extended with additional indexes, for example if you know of a data structure that provides optimized 
access for the access pattern that you will use (e.g. one of https://github.com/michalmarczyk excellent data structures)

To extend, implement the following multimethods.

 * `index-def-spec` - the spec for the index definition
 * `index-def->behaviour`, for the index behaviour
 
`index-def->behaviour` should return a map with the following keys

 * `:empty` - a constant to initialize the empty index
 * `:add` - a function to add items to the index, called when items are added to the primary index 
 * `:remove` - a function to remove items from the index, called when items are removed from the primary index

See the [built-in indexes](https://github.com/danielneal/compound/tree/master/src/compound/indexes) for examples. 

## Influences 

It is influenced by Christophe Grand's [indexed set](https://github.com/cgrand/indexed-set). 

## License

Copyright © 2017 Daniel Neal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
