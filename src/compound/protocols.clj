(ns compound.protocols)

;; Primary index used for storing data by a primary key
(defprotocol IIndexPrimary
  (-add-items [this items])
  (-remove-by-keys [this ks]))

;; Secondary indexes are called when data
;; is added to our removed from the primary index,
;; and they can respond as required.

(defprotocol IIndexSecondary
  (-on-add [this added])
  (-on-remove [this removed]))

;; They can implement a queries accept a data structure representing
;; a query to perform.

(defprotocol IQuery
  (-query [this q]))

(defprotocol IQueryRefs
  (-query-refs [this q]))

(defmulti make-index (fn [index-def] (get index-def :type)))
