(ns bench.full
  "making full use of normalized DB and EQL queries"
  (:require
    [shadow.grove :as sg :refer (defc <<)]
    [shadow.grove.db :as db]
    [shadow.grove.runtime :as rt]
    [bench.util :as u]))

(defc row [row-ident]
  (bind data
    (sg/query-ident row-ident))

  (render
    (let [{:keys [is-selected? ^:text id ^:text label]} data]
      (<< [:tr {:class (if is-selected? "danger" "")}
           [:td.col-md-1 id]
           [:td.col-md-4
            [:a {:on-click {:e ::select! :id row-ident}} label]]
           [:td.col-md-1
            [:a {:on-click {:e ::delete! :id row-ident}}
             [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]]]
           [:td.col-md-6]]))))

(defc ui-root []
  (bind data
    (sg/query-root
      [::items]))

  (render
    (let [{::keys [items]} data]
      (<< [:div.container
           [:div.jumbotron
            [:div.row
             [:div.col-md-6
              [:h1 "shadow-grove"]]
             [:div.col-md-6
              [:div.row
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "run"
                  :on-click {:e ::run!}}
                 "Create 1,000 rows"]]
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "runlots"
                  :on-click {:e ::run-lots!}}
                 "Create 10,000 rows"]]
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "add"
                  :on-click {:e ::add!}}
                 "Append 1,000 rows"]]
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "update"
                  :on-click {:e ::update-some!}}
                 "Update every 10th row"]]
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "clear"
                  :on-click {:e ::clear!}}
                 "Clear"]]
               [:div.col-sm-6.smallpad
                [:button.btn.btn-primary.btn-block
                 {:type "button"
                  :id "swaprows"
                  :on-click {:e ::swap-rows!}}
                 "Swap rows"]]]]]]

           [:table.table.table-hover.table-striped.test-data
            [:tbody
             (sg/keyed-seq items identity row)]]

           [:span.preloadicon.glyphicon.glyphicon-remove
            {:aria-hidden "true"}]]))))

(defonce root-el
  (js/document.getElementById "main"))

(def schema
  {::item
   {:type :entity
    :primary-key :id
    :attrs {}}})

(defonce data-ref
  (-> {::editing nil
       ::items []}
      (db/configure schema)
      (atom)))

(defonce rt-ref
  (-> {:id-seq-ref (atom 0)}
      (sg/prepare data-ref ::db)))

(defn reset-db [{::keys [items] :as db}]
  (-> (reduce db/remove db items)
      (assoc ::items [])))

(sg/reg-event rt-ref ::run!
  (fn [{:keys [id-seq-ref] :as env}]
    (update env :db
      (fn [db]
        (reduce
          (fn [db _]
            (let [item {:id (swap! id-seq-ref inc)
                        :label (u/make-label)}]
              (db/add db ::item item [::items])))
          (reset-db db)
          (range 1000))))))

(sg/reg-event rt-ref ::run-lots!
  (fn [{:keys [id-seq-ref] :as env}]
    (update env :db
      (fn [db]
        (reduce
          (fn [db _]
            (let [item {:id (swap! id-seq-ref inc)
                        :label (u/make-label)}]
              (db/add db ::item item [::items])))
          (reset-db db)
          (range 10000))))))

(sg/reg-event rt-ref ::add!
  (fn [{:keys [id-seq-ref] :as env}]
    (update env :db
      (fn [db]
        (reduce
          (fn [db _]
            (let [item {:id (swap! id-seq-ref inc)
                        :label (u/make-label)}]
              (db/add db ::item item [::items])))
          db
          (range 1000))))))

(sg/reg-event rt-ref ::update-some!
  (fn [env {:keys [id]}]
    (update env :db
      (fn [db]
        (let [{::keys [items]} db
              to-update (range 0 (count items) 10)]
          (reduce
            (fn [db idx]
              (let [ident (nth items idx)]
                (update-in db [ident :label] str " !!!")))
            db
            to-update))))))

(sg/reg-event rt-ref ::swap-rows!
  (fn [env {:keys [id]}]
    (update env :db
      (fn [db]
        (let [{::keys [items]} db

              front-idx 1
              back-idx 998

              front (get items front-idx)
              back (get items back-idx)]

          (-> db
              (assoc-in [::items front-idx] back)
              (assoc-in [::items back-idx] front)))))))

(sg/reg-event rt-ref ::select!
  (fn [env {:keys [id]}]
    (update env :db
      (fn [db]
        (let [{::keys [selected]} db]
          (-> db
              (assoc ::selected id)
              (assoc-in [id :is-selected?] true)
              (cond->
                selected
                (update selected dissoc :is-selected?))))))))

(defn without [current id]
  (->> current
       (remove #(= id %))
       (into [])))

(sg/reg-event rt-ref ::delete!
  (fn [env {:keys [id]}]
    (update env :db
      (fn [db]
        (-> db
            (db/remove id)
            (update ::items without id))))))

;; this is playing benchmark games a bit, but it highlights an interesting case
;; in the "clear rows" benchmark

;; if we remove all items immediately there are still 1000 active queries,
;; which all get invalidated since the ident they used got removed.
;; they never get to run their update, but even just the invalidation
;; more than doubles the time spent.
;; if we instead just set ::items [] the queries will all unmount
;; and the :run-after actual removal then has much less work to do since
;; the 1000 queries are already gone and no invalidation needs to be done.

;; not doing this is not the end of the world. the query invalidation is actually
;; fast enough to not worry. but it is still something to think about.
;; wonder if that can be handled automatically in some way? maybe smarter
;; scheduling can cover this somehow?
(sg/reg-event rt-ref ::clear!
  (fn [env _]
    (-> env
        (sg/queue-fx :run-after {:e ::clear-idents! :idents (get-in env [:db ::items])})
        (update :db assoc ::items [])
        )))

(sg/reg-fx rt-ref :run-after
  (fn [{:keys [transact!] :as env} e]
    (transact! e)))

(sg/reg-event rt-ref ::clear-idents!
  (fn [env {:keys [idents]}]
    (update env :db
      (fn [db]
        (reduce db/remove db idents)))))

(defn render []
  (sg/render rt-ref root-el (ui-root)))

(defn init []
  (render))

(defn ^:dev/after-load reload! []
  (render))