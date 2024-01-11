(ns bench.ultralight-interpreted
  "no normlized DB or EQL queries, lowest lvl that is still convenient"
  (:require
    [shadow.grove :as sg]
    [shadow.arborist.protocols :as sap]
    [shadow.arborist :as sa]
    [shadow.arborist.interpreted]
    [bench.util :as u]))

(defn make-items [{:keys [id-seq-ref] :as env} num]
  (->> (range num)
       (map (fn []
              {:id (swap! id-seq-ref inc)
               :label (u/make-label)}))))

(defn reset-items [{:keys [data-ref] :as env} num]
  (let [items
        (->> (make-items env num)
             (vec))]

    (swap! data-ref assoc :items items)))

(defn row [{:keys [is-selected? id label]} idx]
  [:tr {:class (if is-selected? "danger" "")}
   [:td.col-md-1 id]
   [:td.col-md-4
    [:a {:on-click {:e ::select! :idx idx}} label]]
   [:td.col-md-1
    [:a {:on-click {:e ::delete! :id id}}
     [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]]]
   [:td.col-md-6]])

(defn ui-root [{:keys [items] :as data}]
  [:div.container
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
     (sg/keyed-seq items :id row)]]

   [:span.preloadicon.glyphicon.glyphicon-remove
    {:aria-hidden "true"}]])

(defonce root-el
  (js/document.getElementById "main"))

(defonce data-ref
  (-> {:editing nil
       :items []}
      (atom)))

(declare render)

(defonce root-env
  {:data-ref data-ref
   :id-seq-ref (atom 0)
   ::sap/dom-event-handler
   (reify
     sap/IHandleDOMEvents
     (validate-dom-event-value! [this env event value]
       true)
     (handle-dom-event! [this env _ event dom-event]
       (case (:e event)
         ::run!
         (reset-items env 1000)

         ::run-lots!
         (reset-items env 10000)

         ::add!
         (let [new-items (make-items env 1000)]
           (swap! data-ref update :items into new-items))

         ::update-some!
         (swap! data-ref update :items
           (fn [items]
             (let [to-update (range 0 (count items) 10)]
               (reduce
                 (fn [items idx]
                   (let [item (nth items idx)]
                     (assoc items idx (update item :label str " !!!"))))
                 items
                 to-update))))

         ::clear!
         (swap! data-ref assoc :items [])

         ::swap-rows!
         (swap! data-ref
           (fn [{:keys [items] :as db}]
             (let [front-idx 1
                   back-idx 998

                   front (get items front-idx)
                   back (get items back-idx)]

               (assoc db
                 :items
                 (-> items
                     (assoc front-idx back)
                     (assoc back-idx front))))))

         ::select!
         (swap! data-ref
           (fn [{:keys [selected] :as db}]
             (-> db
                 (assoc :selected (:idx event))
                 (assoc-in [:items (:idx event) :is-selected?] true)
                 (cond->
                   selected
                   (update-in [:items selected] dissoc :is-selected?)))))

         ::delete!
         (swap! data-ref update :items
           (fn [items]
             (->> items
                  (remove #(= (:id event) (:id %)))
                  (vec)))))

       ;; could use add-watch I guess?
       (render)))})

(defonce dom-root (sa/dom-root root-el root-env))

(defn render []
  (sa/update! dom-root (ui-root @data-ref)))

(defn init []
  (render))

(defn ^:dev/after-load reload! []
  (render))