![Compound](https://raw.githubusercontent.com/danielneal/compound/master/compound4.png)

Compound is a micro structure for data used in reagent (and re-frame etc) applications.

It is nowhere close to as expressive or powerful as datascript, but it provides a little extra functionality over storing data in a map indexed by one thing. It is useful if you have relational data that has more than one access pattern, and want to avoid repeated linear scans, but for some reason datascript is not an ideal fit (e.g. with reframe, to avoid recomputing every query whenever the database changes)

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
And I want to access them (in various bits of the ui), by source, by difficulty, by pattern.

I can set up a compound, using `(empty-compound <index-defs>)`

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
                                       
                      #:compound.index{:type :compound.index.types/one-to-many
                                       :key-fn :appropriate-materials
                                       :id :appropriate-materials}}))

```

Add and remove items as follows.

```clojure
(-> (c/add patterns pattern-data) ;; add the items
    (c/remove [1])) ;; remove using the primary key

#:compound{:index-defs
           {:id
            #:compound.index{:type :compound.index.types/primary,
                             :conflict-behaviour
                             :compound.conflict-behaviours/upsert,
                             :key-fn :id,
                             :id :id},
            :difficulty
            #:compound.index{:type :compound.index.types/one-to-many,
                             :key-fn :difficulty,
                             :id :difficulty},
            :source
            #:compound.index{:type :compound.index.types/one-to-many,
                             :key-fn :source,
                             :id :source},
            :appropriate-materials
            #:compound.index{:type :compound.index.types/many-to-many,
                             :key-fn :appropriate-materials,
                             :id :appropriate-materials}},
           :indexes
           {:difficulty
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
              :pattern :shirt-basic}}},
           :primary-index-id :id}

```

## Built in indexes

### [One to One](https://github.com/danielneal/compound/blob/master/src/compound/indexes/one_to_one.clj)

Use when `(key-fn item)` returns a single key for each item.
Stores the item against the `(key-fn item)`; throws if `(key-fn item)` returns a duplicate key to a previous item. 

### [One to Many](https://github.com/danielneal/compound/blob/master/src/compound/indexes/one_to_many.clj)

Use when `(key-fn item)` returns a single key for each item, but duplicates are permitted.
Stores a set of items against `(key-fn item)`; if `(key-fn item)` returns a duplicate, add to the set of items stored against `(key-fn item)`

### [Many to Many](https://github.com/danielneal/compound/blob/master/src/compound/indexes/many_to_many.clj)

Use when `(key-fn item)` returns multiple values, and the index will store the item against each of them.


## Extending with additional custom indexes

## License

Copyright © 2017 Daniel Neal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
