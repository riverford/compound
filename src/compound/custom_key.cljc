(ns compound.custom-key)

(defmulti custom-key-fn
  (fn [k item] k))

(defmethod custom-key-fn :default
  [k item]
  (throw (ex-info (str "Implementation of custom key-fn for " k " not found") {:k k, :item item})))

