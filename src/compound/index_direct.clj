(ns compound.index-direct
  (:require [compound.protocols :as p]))

(defrecord IndexDirect [index index-def])

(defmulti query (fn [this [op & args]] op))

(defmethod query :keys
  [index-primary q]
  (let [{:keys [index]} index-primary
        [op ks] q]
    (into #{} (comp (map #(get index %))
                    (remove nil?)) q)))

(defmethod query :key
  [index-primary q]
  (let [{:keys [index]} index-primary
        [op k] q]
    (get index k)))

(defmulti command (fn [this [op & args]] op))

(defmethod command :add-items
  [index-primary cmd]
  (let [{:keys [index index-def]} index-primary
        {:keys [key-fn]} index-def
        [op items] cmd
        [new-index added] (reduce (fn add-items [[index added] item]
                                    (let [k (key-fn item)]
                                      [(assoc! index k item)
                                       (assoc! added k item)]))
                                  [(transient index) (transient {})]
                                  items)]
    [(->IndexPrimary (persistent! new-index) index-def) [[:added (persistent! added)]]]))

(defmethod command :remove-by-key
  [index-primary cmd]
  (let [{:keys [index index-def]} index-primary
        [op ks] cmd
        [new-index removed] (reduce (fn remove-items [[index removed] k]
                                      (let [item (get index k)]
                                        [(dissoc! index k)
                                         (assoc! removed k item)]))
                                    [(transient index) (transient {})]
                                    ks)]
    [(->IndexPrimary (persistent! new-index) index-def) (persistent! removed)]))

(extend-type IndexPrimary
  p/Query
  (-query [this q] (query this q))
  p/Command
  (-command [this cmd] (command this cmd)))

(comment
  (def idx (IndexPrimary. {} {:id :a :key-fn :a}))
  (p/-command (first (p/-command idx [:add-items [{:a 1} {:b 2}]])) [:add-items [{:a 3} {:a 4} {:a 5}]])
  idx
  (query ))
