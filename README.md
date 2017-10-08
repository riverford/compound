# Compound

Compound is a micro structure for data used in reagent (and re-frame etc) applications.

It is nowhere close to as expressive or powerful as datascript, but it provides a little extra functionality over storing data in a plain map (indexed by one thing)
. It is useful if you have relational data that has more than one access pattern, and want to avoid linear scans, but for some reason
datascript may not be an ideal fit (e.g. with reframe, to avoid recomputing every query whenever the database changes)

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
(def patterns
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

(def p
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

#:compound{:index-defs
           {:id
            #:compound.index{:type :compound.index.types/primary,
                             :conflict-behaviour
                             :compound.conflict-behaviours/upsert,
                             :key-fn :id,
                             :id :id},
            :difficulty
            #:compound.index{:type :compound.index.types/multi,
                             :conflict-behaviour
                             :compound.conflict-behaviours/upsert,
                             :key-fn :difficulty,
                             :id :difficulty},
            :source
            #:compound.index{:type :compound.index.types/multi,
                             :conflict-behaviour
                             :compound.conflict-behaviours/upsert,
                             :key-fn :source,
                             :id :source}},
           :indexes {:id {}, :difficulty {}, :source {}},
           :primary-index-id :id}
```

Then, add items as follows.

```
(-> (c/add p patterns)
    (c/indexes))

{:difficulty
 {:medium
  #{{:id 1,
     :source :instituto-di-moda,
     :difficulty :medium,
     :pattern :bodice-basic}
    {:id 4,
     :source :winifred-aldrich,
     :difficulty :medium,
     :appropriate-materials #{:silk :cotton},
     :pattern :dress-princess-seam}},
  :easy
  #{{:id 3,
     :source :instituto-di-moda,
     :difficulty :easy,
     :pattern :bodice-dartless}
    {:id 2,
     :source :instituto-di-moda,
     :difficulty :easy,
     :pattern :shirt-basic}},
  :hard
  #{{:id 5,
     :source :natalie-bray,
     :pattern :winter-coat,
     :difficulty :hard,
     :appropriate-materials #{:wool}}}},
 :source
 {:instituto-di-moda
  #{{:id 1,
     :source :instituto-di-moda,
     :difficulty :medium,
     :pattern :bodice-basic}
    {:id 3,
     :source :instituto-di-moda,
     :difficulty :easy,
     :pattern :bodice-dartless}
    {:id 2,
     :source :instituto-di-moda,
     :difficulty :easy,
     :pattern :shirt-basic}},
  :winifred-aldrich
  #{{:id 4,
     :source :winifred-aldrich,
     :difficulty :medium,
     :appropriate-materials #{:silk :cotton},
     :pattern :dress-princess-seam}},
  :natalie-bray
  #{{:id 5,
     :source :natalie-bray,
     :pattern :winter-coat,
     :difficulty :hard,
     :appropriate-materials #{:wool}}}},
 :id
 {1
  {:id 1,
   :source :instituto-di-moda,
   :difficulty :medium,
   :pattern :bodice-basic},
  4
  {:id 4,
   :source :winifred-aldrich,
   :difficulty :medium,
   :appropriate-materials #{:silk :cotton},
   :pattern :dress-princess-seam},
  3
  {:id 3,
   :source :instituto-di-moda,
   :difficulty :easy,
   :pattern :bodice-dartless},
  5
  {:id 5,
   :source :natalie-bray,
   :pattern :winter-coat,
   :difficulty :hard,
   :appropriate-materials #{:wool}},
  2
  {:id 2,
   :source :instituto-di-moda,
   :difficulty :easy,
   :pattern :shirt-basic}}}

```

## License

Copyright © 2017 Daniel Neal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
