# Compound

Compound is a micro structure for data used in reagent (and re-frame etc) applications.

It is nowhere close to as expressive or powerful as datascript, but it provides a little extra functionality over storing data in a plain map (indexed by one thing). It is useful if you have relational data that has more than one access pattern, and want to avoid linear scans, but for some reason
datascript is not an ideal fit (e.g. with reframe, to avoid recomputing every query whenever the database changes)

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

```
(def pattern-data
  #{{:id 1
     :source :instituto-di-moda
     :difficulty :medium
     :pattern :bodice-basic}

    {:id 2
     :source :instituto-di-moda
     :difficulty :easy
     :pattern :shirt-basic}

    {:id 3
     :source :instituto-di-moda
     :difficulty :easy
     :pattern :bodice-dartless}

    {:id 4
     :source :winifred-aldrich
     :difficulty :medium
     :appropriate-materials #{:cotton :silk}
     :pattern :dress-princess-seam}

    {:id 5
     :source :natalie-bray
     :pattern :winter-coat
     :difficulty :hard
     :appropriate-materials #{:wool}}})

```
And I want to access them (in various bits of the ui), by source, by difficulty, by pattern.

I can set up a compound, using `(empty-compound <index-defs>)`

```
(require [compound.core :as c])
(require [compound.indexes.multi])

(def patterns
  (c/empty-compound #{#:compound.index{:type :compound.index.types/primary
                                       :conflict-behaviour :compound.conflict-behaviours/upsert
                                       :key-fn :id
                                       :id :id}

                    #:compound.index{:type :compound.index.types/multi
                                     :key-fn :source
                                     :id :source}

                    #:compound.index{:type :compound.index.types/multi
                                     :key-fn :difficulty
                                     :id :difficulty}}))

```

Add items as follows.

`(c/add p pattern-data)`

And remove (by primary key) like this.

`(-> (c/add p pattern-data) (c/remove [1 2]))`

The result of that last operation is shown below.

```
#:compound{:index-defs
           {:id
            #:compound.index{:type :compound.index.types/primary,
                             :conflict-behaviour
                             :compound.conflict-behaviours/upsert,
                             :key-fn :id,
                             :id :id},
            :difficulty
            #:compound.index{:type :compound.index.types/multi,
                             :key-fn :difficulty,
                             :id :difficulty},
            :source
            #:compound.index{:type :compound.index.types/multi,
                             :key-fn :source,
                             :id :source}},
           :indexes
           {:difficulty
            {:hard
             #{{:id 5,
                :source :natalie-bray,
                :pattern :winter-coat,
                :difficulty :hard}},
             :medium
             #{{:id 4,
                :source :winifred-aldrich,
                :difficulty :medium,
                :pattern :dress-princess-seam}},
             :easy
             #{{:id 3,
                :source :instituto-di-moda,
                :difficulty :easy,
                :pattern :bodice-dartless}}},
            :source
            {:natalie-bray
             #{{:id 5,
                :source :natalie-bray,
                :pattern :winter-coat,
                :difficulty :hard}},
             :instituto-di-moda
             #{{:id 3,
                :source :instituto-di-moda,
                :difficulty :easy,
                :pattern :bodice-dartless}},
             :winifred-aldrich
             #{{:id 4,
                :source :winifred-aldrich,
                :difficulty :medium,
                :pattern :dress-princess-seam}}},
            :id
            {5
             {:id 5,
              :source :natalie-bray,
              :pattern :winter-coat,
              :difficulty :hard},
             4
             {:id 4,
              :source :winifred-aldrich,
              :difficulty :medium,
              :pattern :dress-princess-seam},
             3
             {:id 3,
              :source :instituto-di-moda,
              :difficulty :easy,
              :pattern :bodice-dartless}}},
           :primary-index-id :id}

```

## Built in indexes

## Extending with additional custom indexes

## License

Copyright © 2017 Daniel Neal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
