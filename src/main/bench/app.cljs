(ns bench.app
  (:require
    [shadow.experiments.grove :as sg :refer (defc <<)]
    [shadow.experiments.grove.events :as ev]
    [shadow.experiments.grove.db :as db]
    [shadow.experiments.grove.eql-query :as eql]
    [shadow.experiments.grove.runtime :as rt]
    [shadow.experiments.grove.local :as local]
    [bench.util :as u]))

(defmethod eql/attr ::is-selected?
  [env {::keys [selected] :as db} {:db/keys [ident] :as current} _ params]
  (= selected ident))

(defc row [row-ident]
  (bind {::keys [is-selected?] :as data}
    (sg/query-ident row-ident
      [:id
       :label
       ::is-selected?]))

  (render
    (<< [:tr {:class (if is-selected? "danger" "")}
         [:td.col-md-1 (:id data)]
         [:td.col-md-4
          [:a {:on-click {:e ::select! :id row-ident}}
           (:label data)]]
         [:td.col-md-1
          [:a {:on-click {:e ::delete! :id row-ident}}
           [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]]]
         [:td.col-md-6]])))

(defc ui-root []
  (event ::run! sg/tx)
  (event ::run-lots! sg/tx)
  (event ::add! sg/tx)
  (event ::update-some! sg/tx)
  (event ::clear! sg/tx)
  (event ::swap-rows! sg/tx)
  (event ::select! sg/tx)
  (event ::delete! sg/tx)

  (bind {::keys [items]}
    (sg/query-root
      [::items]))

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
           (sg/render-seq items identity row)]]

         [:span.preloadicon.glyphicon.glyphicon-remove
          {:aria-hidden "true"}]])))

(defonce root-el (js/document.getElementById "main"))

(def schema
  {::item
   {:type :entity
    :attrs {:id [:primary-key number?]}}})

(defonce data-ref
  (-> {::editing nil
       ::items []}
      (db/configure schema)
      (atom)))

(defonce rt-ref
  (-> {:id-seq-ref (atom 0)}
      (rt/prepare data-ref ::db)))

(defn reset-db [{::keys [items] :as db}]
  (-> (reduce db/remove db items)
      (assoc ::items [])))

(ev/reg-event rt-ref ::run!
  (fn [{:keys [db id-seq-ref] :as env}]
    {:db
     (reduce
       (fn [db _]
         (let [item {:id (swap! id-seq-ref inc)
                     :label (u/make-label)}]
           (db/add db ::item item [::items])))
       (reset-db db)
       (range 1000))}))

(ev/reg-event rt-ref ::run-lots!
  (fn [{:keys [db id-seq-ref] :as env}]
    {:db
     (reduce
       (fn [db _]
         (let [item {:id (swap! id-seq-ref inc)
                     :label (u/make-label)}]
           (db/add db ::item item [::items])))
       (reset-db db)
       (range 10000))}))

(ev/reg-event rt-ref ::add!
  (fn [{:keys [db id-seq-ref] :as env}]
    {:db
     (reduce
       (fn [db _]
         (let [item {:id (swap! id-seq-ref inc)
                     :label (u/make-label)}]
           (db/add db ::item item [::items])))
       db
       (range 10000))}))

(ev/reg-event rt-ref ::update-some!
  (fn [{:keys [db] :as env} {:keys [id]}]
    (let [{::keys [items]} db
          to-update (range 0 (count items) 10)]
      {:db (reduce
             (fn [db idx]
               (let [ident (nth items idx)]
                 (update-in db [ident :label] str " !!!")))
             db
             to-update)})))

(ev/reg-event rt-ref ::swap-rows!
  (fn [{:keys [db] :as env} {:keys [id]}]
    (let [{::keys [items]} db

          front-idx 1
          back-idx 998

          front (get items front-idx)
          back (get items back-idx)]

      {:db (-> db
               (assoc-in [::items front-idx] back)
               (assoc-in [::items back-idx] front))})))

(ev/reg-event rt-ref ::select!
  (fn [{:keys [db] :as env} {:keys [id]}]
    {:db (assoc db ::selected id)}))

(defn without [current id]
  (->> current
       (remove #(= id %))
       (into [])))

(ev/reg-event rt-ref ::delete!
  (fn [{:keys [db] :as env} {:keys [id]}]
    {:db (-> db
             (db/remove id)
             (update ::items without id))}))

(ev/reg-event rt-ref ::clear!
  (fn [{:keys [db] :as env} _]
    {:db (-> (reduce db/remove db (db/all-idents-of db ::item))
             (assoc ::items []))}))

(defn ^:dev/after-load start []
  (sg/start ::ui root-el (ui-root)))

(defn init []
  (sg/init ::ui
    {}
    [(local/init rt-ref)])

  (start))