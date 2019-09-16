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

#?(:clj
   (defmacro compound [indexes]
     (let [[p-opts & s-opts] indexes
           pt-sym (gensym "pt")
           pi-sym (gensym "pi")
           st-syms (for [_ s-opts]
                     (gensym "st"))
           si-syms (for [_ s-opts]
                     (gensym "si"))
           m-sym (gensym "m")
           x-sym (gensym "x")
           k-sym (gensym "k")
           ex-sym (gensym "ex")
           new-sym (gensym "new")]
       `(let [~pt-sym (indexer ~p-opts)
              ~@(mapcat (fn [sym opts]
                          [sym `(indexer ~opts)])
                        st-syms
                        s-opts)]
          (assert (satisfies? PrimaryIndex ~pt-sym) "First index must be a primary index")
          (with-meta {}
            {`items (fn [~m-sym]
                      (get-all ~pt-sym (get ~m-sym (id ~pt-sym))))
             `add-items (fn [~m-sym xs#]
                          (loop [~pi-sym (before ~pt-sym (get ~m-sym (id ~pt-sym)))
                                 ~@(mapcat (fn [st si]
                                             [si `(before ~st (get ~m-sym (id ~st)))])
                                           st-syms
                                           si-syms)
                                 [~x-sym & xs#] xs#]
                            (if (nil? ~x-sym)
                              (with-meta ~(into {`(id ~pt-sym) `(after ~pt-sym ~pi-sym)}
                                                (map (fn [st si]
                                                       [`(id ~st)
                                                        `(after ~st ~si)])
                                                     st-syms
                                                     si-syms))
                                (meta ~m-sym))
                              (let [k# (extract-key ~pt-sym ~x-sym)
                                    ~ex-sym (get-by-key ~pt-sym ~pi-sym k#)]
                                (if ~ex-sym
                                  (let [~new-sym (on-conflict ~pt-sym ~ex-sym ~x-sym)]
                                    (recur
                                     (index ~pt-sym ~pi-sym k# ~new-sym)
                                     ~@(map (fn [st si]
                                              `(let [k1# (extract-key ~st ~ex-sym)
                                                     k2# (extract-key ~st ~new-sym)]
                                                 (index ~st (unindex ~st ~si k1# ~ex-sym) k2# ~new-sym)))
                                            st-syms
                                            si-syms)
                                     xs#))
                                  (recur
                                   (index ~pt-sym ~pi-sym k# ~x-sym)
                                   ~@(map (fn [st si]
                                            `(let [k# (extract-key ~st ~x-sym)]
                                               (index ~st ~si k# ~x-sym)))
                                          st-syms
                                          si-syms)
                                   xs#))))))
             `remove-keys (fn [~m-sym ks#]
                            (loop [~pi-sym (before ~pt-sym (get ~m-sym (id ~pt-sym)))
                                   ~@(mapcat (fn [st si]
                                               [si `(before ~st (get ~m-sym (id ~st)))])
                                             st-syms
                                             si-syms)
                                   [~k-sym & ks#] ks#]
                              (if (nil? ~k-sym)
                                (with-meta ~(into {`(id ~pt-sym) `(after ~pt-sym ~pi-sym)}
                                                  (map (fn [st si]
                                                         [`(id ~st)
                                                          `(after ~st ~si)])
                                                       st-syms
                                                       si-syms))
                                  (meta ~m-sym))
                                (if-let [~ex-sym (get-by-key ~pt-sym ~pi-sym ~k-sym)]
                                  (recur
                                   (unindex ~pt-sym ~pi-sym ~k-sym ~ex-sym)
                                   ~@(map (fn [st si]
                                            `(let [k# (extract-key ~st ~ex-sym)]
                                               (unindex ~st ~si k# ~ex-sym)))
                                          st-syms
                                          si-syms)
                                   ks#)
                                  (recur
                                   ~pi-sym
                                   ~@si-syms
                                   ks#)))))})))))

(defn compound* [indexes]
  (let [[pi & sis] (doall (map indexer indexes))]
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
