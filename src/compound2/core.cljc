(ns compound2.core)

(defprotocol Index
  (id [this])
  (extract-key [this x])
  (index [this coll k x])
  (unindex [this coll k x])
  (before [this coll])
  (after [this coll]))

(defprotocol PrimaryIndex
  (get-by-key [this coll k])
  (get-all [this coll]))

(defprotocol Compound
  :extend-via-metadata true
  (items [c])
  (add-items [c xs])
  (remove-keys [c ks]))

(defmulti indexer :index-type)

(defmethod indexer :unique
  [opts]
  (let [{:keys [id kfn]} opts]
    (reify
      PrimaryIndex
      (get-by-key [this coll k]
        (get coll k))
      (get-all [this coll]
        (vals coll))

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

(defmethod indexer :multi
  [opts]
  (let [{:keys [id kfn]} opts]
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

(defmethod indexer :nested.unique
  [opts]
  (let [{:keys [id path]} opts]
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

(defmethod indexer :nested.multi
  [opts]
  (let [{:keys [id path]} opts]
    (reify
      Index
      (id [this]
        id)
      (extract-key [this x]
        (into [] (for [p path]
                   (p x))))
      (index [this coll path x]
        (update-in coll path (fnil conj #{})x))
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

(defn compound* [indexes]
  (let [[pt & sts] (map indexer indexes)]
    (assert (satisfies? PrimaryIndex pt) "First index must be a primary index")
    (with-meta {}
      {`items (fn [m]
                (get-all pt (get m (id pt))))
       `add-items (fn [m xs]
                    (loop [pi (before pt (get m (id pt)))
                           sis (map (fn [st]
                                      (before st (get m (id st))))
                                    sts)
                           [x & more] xs]
                      (if (nil? x)
                        (with-meta
                          (into
                           {(id pt) (after pt pi)}
                           (map (fn [st si] [(id st) (after st si)]) sts sis))
                          (meta m))
                        (let [k (extract-key pt x)
                              existing (get-by-key pt pi k)]
                          (if existing
                            (let [without (unindex pt pi k existing)]
                              (recur
                               (index pt without k x)
                               (mapv (fn [st si]
                                       (let [k1 (extract-key st existing)
                                             k2 (extract-key st x)
                                             without (unindex st si k1 existing)]
                                         (index st without k2 x)))
                                     sts
                                     sis)
                               more))
                            (recur
                             (index pt pi k x)
                             (mapv (fn [st si]
                                     (let [k (extract-key st x)]
                                       (index st si k x)))
                                   sts
                                   sis)
                             more))))))
       `remove-keys (fn [m ks]
                      (loop [pi (before pt (get m (id pt)))
                             sis (map (fn [st]
                                        (before st (get m (id st))))
                                      sts)
                             [k & more] ks]
                        (if (nil? k)
                          (with-meta
                            (into {(id pt) (after pt pi)}
                                  (map (fn [st si]
                                         [(id st)
                                          (after st si)])
                                       sts
                                       sis))
                            (meta m))
                          (let [existing (get-by-key pt pi k)]
                            (if existing
                              (recur
                               (unindex pt pi k existing)
                               (mapv (fn [st si]
                                       (let [k (extract-key st existing)]
                                         (unindex st si k existing)))
                                     sts
                                     sis)
                               more)
                              (recur pi sis more))))))})))
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
                                   (let [without# (unindex ~pt-sym ~pi-sym k# ~existing-sym)]
                                     (index ~pt-sym without# k# ~x-sym))
                                   ~@(map (fn [st si]
                                            `(let [k1# (extract-key ~st ~existing-sym)
                                                   k2# (extract-key ~st ~x-sym)
                                                   without# (unindex ~st ~si k1# ~existing-sym)]
                                               (index ~st without# k2# ~x-sym)))
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


(defn compound2* [indexes]
  (let [[pt & sts] (map indexer indexes)]
    (assert (satisfies? PrimaryIndex pt) "First index must be a primary index")
    (with-meta {}
      {`items (fn [m]
                (get-all pt (get m (id pt))))
       `add-items (fn [m xs]
                    (let [[pi sis]
                          (reduce
                           (fn [acc x]
                             (let [[pi sis] acc]
                               (let [k (extract-key pt x)
                                     existing (get-by-key pt pi k)]
                                 (if existing
                                   (let [without (unindex pt pi k existing)]
                                     (list
                                      (index pt without k x)
                                      (doall (map (fn [st si]
                                                   (let [k1 (extract-key st existing)
                                                         k2 (extract-key st x)
                                                         without (unindex st si k1 existing)]
                                                     (index st without k2 x)))
                                                 sts
                                                 sis))))
                                   (list (index pt pi k x)
                                         (doall (map (fn [st si]
                                                      (let [k (extract-key st x)]
                                                        (index st si k x)))
                                                    sts
                                                    sis)))))))
                           (list (before pt (get m (id pt)))
                                 (doall (map (fn [st]
                                               (before st (get m (id st))))
                                             sts)))
                           xs)]
                      (with-meta
                        (into
                         {(id pt) (after pt pi)}
                         (map (fn [st si] [(id st) (after st si)]) sts sis))
                        (meta m))))
       `remove-keys (fn [m ks]
                      (let [[pi sis]
                            (reduce
                             (fn [acc k]
                               (let [[pi sis] acc]
                                 (if (nil? k)
                                   (let [existing (get-by-key pt pi k)]
                                     (if existing
                                       (list (unindex pt pi k existing)
                                             (doall (map (fn [st si]
                                                           (let [k (extract-key st existing)]
                                                             (unindex st si k existing)))
                                                         sts
                                                         sis)))
                                       (list pi sis))))))
                             (list
                              (before pt (get m (id pt)))
                              (doall (map (fn [st]
                                            (before st (get m (id st))))
                                          sts)))
                             ks)]
                        (with-meta
                          (into {(id pt) (after pt pi)}
                                (map (fn [st si]
                                       [(id st)
                                        (after st si)])
                                     sts
                                     sis))
                          (meta m))))})))

(cons 2 (sequence (map (fn [a b] [a b])) (range 5) (range 10)))


(defn compound3* [indexes]
  (let [[pt & sts] (map indexer indexes)]
    (assert (satisfies? PrimaryIndex pt) "First index must be a primary index")
    (with-meta {}
      {`items (fn [m]
                (get-all pt (get m (id pt))))
       `add-items (fn [m xs]
                    (let [[pi added removed] (reduce
                                              (fn [acc x]
                                                (let [[pi added removed] acc]
                                                  (let [k (extract-key pt x)
                                                        existing (get-by-key pt pi k)]
                                                    (if existing
                                                      (let [without (unindex pt pi k existing)]
                                                        (list
                                                         (index pt without k x)
                                                         (conj! added x)
                                                         (conj! removed existing)))
                                                      (list
                                                       (index pt pi k x)
                                                       (conj! added x)
                                                       removed)))))
                                              (list (before pt (get m (id pt)))
                                                    (transient #{})
                                                    (transient #{}))
                                              xs)
                          sis (reduce (fn [sis x]
                                        (mapv (fn [st si]
                                                (let [k (extract-key st x)]
                                                  (unindex st si k x)))
                                              sts
                                              sis))
                                      (mapv (fn [st]
                                              (before st (get m (id st))))
                                            sts)
                                      (persistent! removed))
                          sis (reduce (fn [sis x]
                                        (mapv (fn [st si]
                                                (let [k (extract-key st x)]
                                                  (index st si k x)))
                                              sts
                                              sis))
                                      sis
                                      (persistent! added))]
                      (with-meta
                        (into
                         {(id pt) (after pt pi)}
                         (map (fn [st si] [(id st) (after st si)]) sts sis))
                        (meta m))))})))
