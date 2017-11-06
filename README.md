![Compound](https://raw.githubusercontent.com/danielneal/compound/master/compound.png)

**noun** *kɒmpaʊnd*

1. a thing that is composed of two or more separate elements; a mixture.

**verb** *kəmˈpaʊnd*

1. make up (a composite whole); constitute.
2. make (something bad) worse. 

Compound is a micro structure for data used in reagent (and re-frame etc) applications, 
based on the idea that [worse is better](https://en.wikipedia.org/wiki/Worse_is_better). 

It maintains a plain hash-map of indexes for your data. This has two benefits: 
 1. Accessing your data in multiple ways (e.g. by name _and_ id) is easier than if you have a map just indexed by primary key (a common approach in reframe applications to keep subscriptions fast). 
 2. The plain map indexes are easily inspectable in tools like re-frisk or re-frame-trace.

You can have as many indexes as you like. Some index types are built in, but adding extra ones is straight forward. 

There is no query engine. 

## Basic Usage

```clojure

(require [compound.core :as c]) 

(def patterns
  (-> (c/compound 
         {:primary-index-def 
            {:key :pattern
             :on-conflict :compound/replace}
             
          :secondary-index-defs 
            [{:key :difficulty
              :index-type :compound/one-to-many}]})
              
       (c/add-items 
         #{{:pattern :bodice-basic
            :difficulty :easy}

           {:pattern :shirt-basic
            :difficulty :medium}

           {:pattern :bodice-dartless
            :difficulty :easy}})))

(get (c/primary-index patterns) :bodice-basic)

;; {:pattern :shirt-basic
;;  :difficulty :medium}


(get (c/index patterns :difficulty) :easy)

;; #{{:pattern :bodice-basic
;;    :difficulty :easy}
;;   {:pattern :bodice-dartless
;;    :difficulty :easy}}

```
## More examples and documentation

Detailed documentation and examples and documentation are found on [github pages](https://danielneal.github.io/compound)

## Influences 

It is influenced by Christophe Grand's [indexed set](https://github.com/cgrand/indexed-set). 

## License

Copyright © 2017 Daniel Neal

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
