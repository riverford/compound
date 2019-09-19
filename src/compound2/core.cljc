(ns compound2.core)

#?(:cljs (enable-console-print!))

(defprotocol Index
  (id [this])
  (extract-key [this x])
  (index [this coll k x])
  (unindex [this coll k x])
  (before [this coll])
  (after [this coll]))

(defprotocol PrimaryIndex
  (get-by-key [this coll k])
  (get-all [this coll])
  (on-conflict [this old new]))

(defprotocol Compound
  :extend-via-metadata true
  (items [c])
  (add-items [c xs])
  (remove-keys [c ks]))

(defmulti indexer :index-type)

(defmethod indexer :one-to-one
  [opts]
  (let [{:keys [id kfn on-conflict]} opts
        id (or id (when (keyword? kfn) kfn))
        on-conflict (or on-conflict (fn [_ new] new))]
    (assert (some? id) "Must provide an id")
    (reify
      PrimaryIndex
      (get-by-key [this coll k]
        (get coll k))
      (get-all [this coll]
        (vals coll))
      (on-conflict [this old new]
        (on-conflict old new))
      Index
      (id [this]
        id)
      (extract-key [this x]
        (kfn x))
      (index [this coll k x]
        (assoc! coll k x))
      (unindex [this coll k x]
        (dissoc! coll k))
      (after [this coll]
        (persistent! coll))
      (before [this coll]
        (transient (or coll {}))))))

(defmethod indexer :one-to-many
  [opts]
  (let [{:keys [id kfn]} opts
        id (or id (when (keyword? kfn) kfn))]
    (assert (some? id) "Must provide an id")
    (reify
      Index
      (id [this]
        id)
      (extract-key [this x]
        (kfn x))
      (index [this coll k x]
        (let [ex (get coll k)]
          (assoc! coll k (conj (or ex #{}) x))))
      (unindex [this coll k x]
        (let [ex (get coll k)
              new (disj ex x)]
          (if (seq new)
            (assoc! coll k new)
            (dissoc! coll k))))
      (after [this coll]
        (persistent! coll))
      (before [this coll]
        (transient (or coll {}))))))

(defn dissoc-in
  [m [k & ks]]
  (if ks
    (let [m2 (dissoc-in (m k) ks)]
      (if (empty? m2)
        (dissoc m k)
        (assoc m k m2)))
    (dissoc m k)))

(defmethod indexer :nested-to-one
  [opts]
  (let [{:keys [id path]} opts
        id (or id (when (vector? path) path))]
    (assert (some? id) "Must provide an id")
    (reify
      Index
      (id [this]
        id)
      (extract-key [this x]
        (into [] (for [p path]
                   (p x))))
      (index [this coll ks x]
        (assoc-in coll ks x))
      (unindex [this coll ks x]
        (dissoc-in coll ks))
      (after [this coll]
        coll)
      (before [this coll]
        (or coll {})))))

(defmethod indexer :nested-to-many
  [opts]
  (let [{:keys [id path]} opts
        id (or id (when (vector? path) path))]
    (assert (some? id) "Must provide an id")
    (reify
      Index
      (id [this]
        id)
      (extract-key [this x]
        (into [] (for [p path]
                   (p x))))
      (index [this coll ks x]
        (update-in coll ks (fnil conj #{}) x))
      (unindex [this coll ks x]
        (let [ex (get-in coll ks)
              new (disj ex x)]
          (if (seq new)
            (assoc-in coll ks new)
            (dissoc-in coll ks))))
      (after [this coll]
        coll)
      (before [this coll]
        (or coll {})))))

(defmethod indexer :many-to-many
  [opts]
  (let [{:keys [id kfn]} opts
        id (or id (when (keyword? kfn) kfn))]
    (assert (some? id) "Must provide an id")
    (reify
      Index
      (id [this]
        id)
      (extract-key [this x]
        (kfn x))
      (index [this coll ks x]
        (reduce
          (fn [acc k]
            (update acc k (fnil conj #{}) x))
          coll
          ks))
      (unindex [this coll ks x]
        (reduce
          (fn [acc k]
            (update acc k disj x))
          coll
          ks))
      (after [this coll]
        coll)
      (before [this coll]
        (or coll {})))))

(def primary-defaults
  {:index-type :one-to-one})

(def secondary-defaults
  {:index-type :one-to-many})

#?(:clj
   (defmacro compound [indexes]
     (let [[p-opis & s-opis] indexes
           pi-sym (gensym "pi")
           px-sym (gensym "px")
           si-syms (for [_ s-opis]
                     (gensym "si"))
           sx-syms (for [_ s-opis]
                     (gensym "sx"))
           m-sym (gensym "m")
           x-sym (gensym "x")
           k-sym (gensym "k")
           ex-sym (gensym "ex")
           new-sym (gensym "new")]
       `(let [~pi-sym (indexer (merge primary-defaults ~p-opis))
              ~@(mapcat (fn [sym opis]
                          [sym `(indexer (merge secondary-defaults ~opis))])
                        si-syms
                        s-opis)]
          (assert (satisfies? PrimaryIndex ~pi-sym) "Firsi index musi be a primary index")
          (with-meta {}
            {`items (fn [~m-sym]
                      (get-all ~pi-sym (get ~m-sym (id ~pi-sym))))
             `add-items (fn [~m-sym xs#]
                          (loop [~px-sym (before ~pi-sym (get ~m-sym (id ~pi-sym)))
                                 ~@(mapcat (fn [si sx]
                                             [sx `(before ~si (get ~m-sym (id ~si)))])
                                           si-syms
                                           sx-syms)
                                 [~x-sym & xs#] xs#]
                            (if (nil? ~x-sym)
                              (with-meta ~(into {`(id ~pi-sym) `(after ~pi-sym ~px-sym)}
                                                (map (fn [si sx]
                                                       [`(id ~si)
                                                        `(after ~si ~sx)])
                                                     si-syms
                                                     sx-syms))
                                (meta ~m-sym))
                              (let [k# (extract-key ~pi-sym ~x-sym)
                                    ~ex-sym (get-by-key ~pi-sym ~px-sym k#)]
                                (if ~ex-sym
                                  (let [~new-sym (on-conflict ~pi-sym ~ex-sym ~x-sym)]
                                    (recur
                                      (index ~pi-sym ~px-sym k# ~new-sym)
                                      ~@(map (fn [si sx]
                                               `(let [k1# (extract-key ~si ~ex-sym)
                                                      k2# (extract-key ~si ~new-sym)]
                                                  (index ~si (unindex ~si ~sx k1# ~ex-sym) k2# ~new-sym)))
                                             si-syms
                                             sx-syms)
                                      xs#))
                                  (recur
                                    (index ~pi-sym ~px-sym k# ~x-sym)
                                    ~@(map (fn [si sx]
                                             `(let [k# (extract-key ~si ~x-sym)]
                                                (index ~si ~sx k# ~x-sym)))
                                           si-syms
                                           sx-syms)
                                    xs#))))))
             `remove-keys (fn [~m-sym ks#]
                            (loop [~px-sym (before ~pi-sym (get ~m-sym (id ~pi-sym)))
                                   ~@(mapcat (fn [si sx]
                                               [sx `(before ~si (get ~m-sym (id ~si)))])
                                             si-syms
                                             sx-syms)
                                   [~k-sym & ks#] ks#]
                              (if (nil? ~k-sym)
                                (with-meta ~(into {`(id ~pi-sym) `(after ~pi-sym ~px-sym)}
                                                  (map (fn [si sx]
                                                         [`(id ~si)
                                                          `(after ~si ~sx)])
                                                       si-syms
                                                       sx-syms))
                                  (meta ~m-sym))
                                (if-let [~ex-sym (get-by-key ~pi-sym ~px-sym ~k-sym)]
                                  (recur
                                    (unindex ~pi-sym ~px-sym ~k-sym ~ex-sym)
                                    ~@(map (fn [si sx]
                                             `(let [k# (extract-key ~si ~ex-sym)]
                                                (unindex ~si ~sx k# ~ex-sym)))
                                           si-syms
                                           sx-syms)
                                    ks#)
                                  (recur
                                    ~px-sym
                                    ~@sx-syms
                                    ks#)))))})))))

(defn compound* [indexes]
  (let [pi (indexer (merge primary-defaults (first indexes)))
        sis (map (fn [opts] (indexer (merge secondary-defaults opts))) indexes)]
    (assert (satisfies? PrimaryIndex pi) "First index must be a primary index")
    (with-meta {}
      {`items (fn [c]
                (get-all pi (get c (id pi))))
       `add-items (fn [c xs]
                    (loop [px (before pi (get c (id pi)))
                           sixs (reduce (fn [acc si]
                                          (assoc acc si (before si (get c (id si)))))
                                        {}
                                        sis)
                           [x & xs] xs]
                      (if (nil? x)
                        (with-meta
                          (reduce-kv (fn [acc si sx]
                                       (assoc acc (id si) (after si sx)))
                                     {(id pi) (after pi px)}
                                     sixs)
                          (meta c))
                        (let [k (extract-key pi x)
                              ex (get-by-key pi px k)]
                          (if ex
                            (let [new (on-conflict pi ex x)]
                              (recur (index pi px k new)
                                     (reduce-kv (fn [acc si sx]
                                                  (assoc acc si (let [k1 (extract-key si ex)
                                                                      k2 (extract-key si new)]
                                                                  (index si (unindex si sx k1 ex) k2 new))))
                                                {}
                                                sixs)
                                     xs))
                            (recur (index pi px k x)
                                   (reduce-kv (fn [acc si sx]
                                                (assoc acc si (let [k (extract-key si x)]
                                                                (index si sx k x))))
                                              {}
                                              sixs)
                                   xs))))))
       `remove-keys (fn [c ks]
                      (loop [px (before pi (get c (id pi)))
                             sixs (reduce (fn [acc si]
                                            (assoc acc si (before si (get c (id si)))))
                                          {}
                                          sis)
                             [k & ks] ks]
                        (if (nil? k)
                          (with-meta
                            (reduce-kv (fn [acc si sx]
                                         (assoc acc (id si) (after si sx)))
                                       {(id pi) (after pi px)}
                                       sixs)
                            (meta c))
                          (if-let [ex (get-by-key pi px k)]
                            (recur (unindex pi px k ex)
                                   (reduce-kv (fn [acc si sx]
                                                (assoc acc si (let [k (extract-key si ex)]
                                                                (unindex si sx k ex))))
                                              {}
                                              sixs)
                                   ks)
                            (recur px sixs ks)))))})))
(def fruit
  (-> (compound [{:kfn :name}
                 {:kfn :colour}])

      (add-items #{{:name :strawberry
                    :colour :red}

                   {:name :raspberry
                    :colour :red}

                   {:name :banana
                    :colour :yellow}})))

{:name
 {:banana {:name :banana, :colour :yellow},
  :raspberry {:name :raspberry, :colour :red},
  :strawberry {:name :strawberry, :colour :red}},
 :colour
 {:yellow #{{:name :banana, :colour :yellow}},
  :red #{{:name :raspberry, :colour :red}
         {:name :strawberry, :colour :red}}}}
