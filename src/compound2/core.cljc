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
  (let [{:keys [id kfn]} opts
        id (or id (when (keyword? kfn) kfn))]
    (assert (some? id) "Must provide an id")
    (reify
      PrimaryIndex
      (get-by-key [this coll k]
        (get coll k))
      (get-all [this coll]
        (vals coll))
      (on-conflict [this old new]
        new)
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
        (let [existing (get coll k)]
          (assoc! coll k (conj (or existing #{}) x))))
      (unindex [this coll k x]
        (let [existing (get coll k)
              new (disj existing x)]
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
      (index [this coll path x]
        (assoc-in coll path x))
      (unindex [this coll path x]
        (dissoc-in coll path))
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
      (index [this coll path x]
        (update-in coll path (fnil conj #{})))
      (unindex [this coll path x]
        (let [existing (get-in coll path)
              new (disj existing x)]
          (if (seq new)
            (assoc-in coll path new)
            (update-in coll (butlast path) dissoc (last path)))))
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
           (assoc! acc k x))
         coll
         ks))
      (unindex [this coll ks x]
        (reduce
         (fn [acc k]
           (dissoc! acc k x))
         coll
         ks))
      (after [this coll]
        (persistent! coll))
      (before [this coll]
        (transient (or coll {}))))))

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
           existing-sym (gensym "ex")]
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
                                 [~x-sym & more#] xs#]
                            (if (nil? ~x-sym)
                              (with-meta ~(into {`(id ~pt-sym) `(after ~pt-sym ~pi-sym)}
                                                (map (fn [st si]
                                                       [`(id ~st)
                                                        `(after ~st ~si)])
                                                     st-syms
                                                     si-syms))
                                (meta ~m-sym))
                              (let [k# (extract-key ~pt-sym ~x-sym)
                                    ~existing-sym (get-by-key ~pt-sym ~pi-sym k#)]
                                (if ~existing-sym
                                  (recur
                                   (index ~pt-sym ~pi-sym k# ~x-sym)
                                   ~@(map (fn [st si]
                                            `(let [k1# (extract-key ~st ~existing-sym)
                                                   k2# (extract-key ~st ~x-sym)]
                                               (index ~st (unindex ~st ~si k1# ~existing-sym) k2# ~x-sym)))
                                          st-syms
                                          si-syms)
                                   more#)
                                  (recur
                                   (index ~pt-sym ~pi-sym k# ~x-sym)
                                   ~@(map (fn [st si]
                                            `(let [k# (extract-key ~st ~x-sym)]
                                               (index ~st ~si k# ~x-sym)))
                                          st-syms
                                          si-syms)
                                   more#))))))
             `remove-keys (fn [~m-sym ks#]
                            (loop [~pi-sym (before ~pt-sym (get ~m-sym (id ~pt-sym)))
                                   ~@(mapcat (fn [st si]
                                               [si `(before ~st (get ~m-sym (id ~st)))])
                                             st-syms
                                             si-syms)
                                   [~k-sym & more#] ks#]
                              (if (nil? ~k-sym)
                                (with-meta ~(into {`(id ~pt-sym) `(after ~pt-sym ~pi-sym)}
                                                  (map (fn [st si]
                                                         [`(id ~st)
                                                          `(after ~st ~si)])
                                                       st-syms
                                                       si-syms))
                                  (meta ~m-sym))
                                (let [~existing-sym (get-by-key ~pt-sym ~pi-sym ~k-sym)]
                                  (if ~existing-sym
                                    (recur
                                     (unindex ~pt-sym ~pi-sym ~k-sym ~existing-sym)
                                     ~@(map (fn [st si]
                                              `(let [k# (extract-key ~st ~existing-sym)]
                                                 (unindex ~st ~si k# ~existing-sym)))
                                            st-syms
                                            si-syms)
                                     more#)
                                    (recur
                                     ~pi-sym
                                     ~@si-syms
                                     more#))))))})))))
(println (rand-int 50))

(defn compound* [indexes]
  (let [[pt & sts] (map indexer indexes)]
    (assert (satisfies? PrimaryIndex pt) "First index must be a primary index")
    (with-meta {}
      {`items (fn [m]
                (get-all pt (get m (id pt))))
       `add-items (fn [m xs]
                    (loop [pi (before pt (get m (id pt)))
                           sis (doall (map (fn [st]
                                             (before st (get m (id st))))
                                           sts))
                           [x & xs] xs]
                      (if (nil? x)
                        (with-meta
                          (into
                           {(id pt) (after pt pi)}
                           (map (fn [st si] [(id st) (after st si)]) sts sis))
                          (meta m))
                        (let [k (extract-key pt x)
                              existing (get-by-key pt pi k)]
                          (if existing
                            (recur (index pt pi k (on-conflict pt existing x))
                                   (doall (map (fn [st si]
                                           (let [k1 (extract-key st existing)
                                                 k2 (extract-key st x)]
                                             (index st (unindex st si k1 existing) k2 x)))
                                         sts
                                         sis))
                                   xs)
                            (recur (index pt pi k x)
                                   (doall (map (fn [st si]
                                                 (let [k (extract-key st x)]
                                                   (index st si k x)))
                                               sts
                                               sis))
                                   xs))))))
       `remove-keys (fn [m ks]
                      (loop [pi (before pt (get m (id pt)))
                             sis (doall (map (fn [st]
                                               (before st (get m (id st))))
                                             sts))
                             [k & ks] ks]
                        (if (nil? k)
                          (with-meta
                            (into {(id pt) (after pt pi)}
                                  (map (fn [st si]
                                         [(id st)
                                          (after st si)])
                                       sts
                                       sis))
                            (meta m)))
                        (if-let [existing (get-by-key pt pi k)]
                          (recur (unindex pt pi k existing)
                                 (doall (map (fn [st si]
                                              (let [k (extract-key st existing)]
                                                (unindex st si k existing)))
                                            sts
                                            sis))
                                 ks)
                          (recur pi sis ks))))})))
