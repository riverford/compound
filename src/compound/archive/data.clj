(ns compound.data)


(defprotocol CompoundIndex
  (plus [this item])
  (minus [this item])
  (get [this k]))

(defn index-primary []
  (reify CompoundIndex
    (plus [this item]
      (get-in))))


(defn index-primary
  [opts]
  (let [{:keys [id index-fn]} opts]
    {:id id
     :type :compound.index/primary
     :get (fn [c k]
            (get-in c [:indexes id k]))
     :plus (fn [c [item _]]
             )}
    ))

(defn index-unique
  [opts]
  (let [{:keys [id index-fn]} opts]
    {:id id
     :type :compound.index/unique
     :get (fn [c k]
            (let [index (index c id)
                  primary-index (primary-index c)]
              ()))
     :plus (fn [c [item primary-key]]
             (update-in c [:indexes id (index-fn item)] ))
     :minus (fn [c [item primary-key]]
              (update-in c [:indexes id] dissoc (index-fn item)))}))

(defn index-multiple
  [opts]
  (let [{:keys [id index-fn]} opts]
    {:id id
     :type :compound.index/multiple
     :plus (fn [acc [item primary-key]]
             (update-in acc (index-fn item) (fnil conj #{}) primary-key))
     :minus (fn [acc [item primary-key]]
              (update-in acc (index-fn item) (disj primary-key)))}))


;; hmmmmm
(def index-def-primary
  {:type :compound.index/primary
   :plus (fn [acc [item primary-key]]
           (let [index-type (get type item)]
             (cond
               (and (nil? acc) (= index-type :compound.index/primary)) primary-key
               (and (some? acc) (= index-type :compound.index/primary)) (throw (ex-info "Compound already has a primary key" {:primary-key acc}))
               :else acc)))
   :minus (fn [acc [item primary-key]]
            )})

(def compound
  {:compound/indexes {:a {1 {:a 1, :b 2}
                          2 {:a 2, :b 3}}
                      :b {2 #{1}
                          3 #{2}}}
   :compound/index-defs {:compound/indexes {:id {:a (index-unique {:id :a :index-fn :a})
                                                 :b (index-multiple {:id :b :index-fn :b})}
                                            :primary :a
                                            :non-primary #{:b}}
                         :compound/index-defs {:compound/indexes {:primary :a}
                                               :non-primary #{:b}
                                               :id {:b (index-multiple :b)}}
                         }})
