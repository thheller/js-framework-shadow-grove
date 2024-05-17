(ns bench.full
  "making full use of normalized DB and EQL queries"
  (:require
    [shadow.grove :as sg :refer (defc <<)]
    [shadow.grove.kv :as kv]
    [bench.util :as u]))

(defonce id-seq-ref (atom 0))

(defonce rt-ref (sg/get-runtime :todomvc))

(defc row [todo-id]
  (bind data
    (sg/kv-lookup :todo todo-id))

  (render
    (let [{:keys [is-selected? ^:text id ^:text label]} data]
      (<< [:tr {:class (if is-selected? "danger" "")}
           [:td.col-md-1 id]
           [:td.col-md-4
            [:a {:on-click {:e ::select! :id todo-id}} label]]
           [:td.col-md-1
            [:a {:on-click {:e ::delete! :id todo-id}}
             [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]]]
           [:td.col-md-6]]))))

(defc ui-root []
  (bind items
    (sg/kv-lookup :db ::items))

  (render
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
          {:aria-hidden "true"}]])))

(defonce root-el
  (js/document.getElementById "main"))

(sg/reg-event rt-ref ::run!
  (fn [env]
    (-> env
        (kv/remove-all :todo)
        (kv/merge-seq :todo
          (->> (range 1000)
               (mapv (fn [_]
                       {:id (swap! id-seq-ref inc)
                        :label (u/make-label)})))
          [:db ::items]))))

(sg/reg-event rt-ref ::run-lots!
  (fn [env]
    (-> env
        (kv/remove-all :todo)
        (kv/merge-seq :todo
          (->> (range 10000)
               (mapv (fn [_]
                       {:id (swap! id-seq-ref inc)
                        :label (u/make-label)})))
          [:db ::items]))))

(sg/reg-event rt-ref ::add!
  (fn [env]
    (let [new-items (->> (range 1000)
                         (mapv (fn [_]
                                 {:id (swap! id-seq-ref inc)
                                  :label (u/make-label)})))]
      (-> env
          (kv/merge-seq :todo new-items)
          (update-in [:db ::items] into (map :id) new-items)))))

(sg/reg-event rt-ref ::update-some!
  (fn [env ev]
    (let [items (get-in env [:db ::items])
          to-update (range 0 (count items) 10)]

      (update env :todo
        (fn [table]
          (reduce
            (fn [table idx]
              (let [id (nth items idx)]
                (update-in table [id :label] str " !!!")))
            table
            to-update))))))

(sg/reg-event rt-ref ::swap-rows!
  (fn [env ev]
    (update-in env [:db ::items]
      (fn [items]
        (let [front-idx 1
              back-idx 998

              front (get items front-idx)
              back (get items back-idx)]

          (-> items
              (assoc front-idx back)
              (assoc back-idx front)))))))

(sg/reg-event rt-ref ::select!
  (fn [env {:keys [id]}]
    (let [selected (get-in env [:db ::selected])]
      (-> env
          (assoc-in [:db ::selected] id)
          (assoc-in [:todo id :is-selected?] true)
          (cond->
            selected
            (update-in [:todo selected] dissoc :is-selected?))))))

(defn without [current id]
  (->> current
       (remove #(= id %))
       (into [])))

(sg/reg-event rt-ref ::delete!
  (fn [env {:keys [id]}]
    (-> env
        (update :todo dissoc id)
        (update-in [:db ::items] without id))))

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
        (sg/queue-fx :run-after {:e ::clear-items! :items (get-in env [:db ::items])})
        (assoc-in [:db ::items] [])
        )))

(sg/reg-fx rt-ref :run-after
  (fn [{:keys [transact!] :as env} e]
    (transact! e)))

(sg/reg-event rt-ref ::clear-items!
  (fn [env {:keys [items]}]
    (update env :todo
      (fn [table]
        (reduce dissoc table items)))))

(defn render []
  (sg/render rt-ref root-el (ui-root)))

(defn init []
  (sg/add-kv-table rt-ref :db
    {}
    {::editing nil
     ::items []})

  (sg/add-kv-table rt-ref :todo
    {:primary-key :id}
    {})

  (render))

(defn ^:dev/after-load reload! []
  (render))