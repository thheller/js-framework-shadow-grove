(ns bench.operators
  (:require
    [shadow.grove :as sg :refer (defc <<)]
    [shadow.grove.db :as db]
    [shadow.grove.operator :as op]
    [bench.util :as u]))

(declare &item)

(defn &items [op _]
  (let [id-seq-ref
        (atom 0)

        make-todos
        (fn [n]
          (->> (range n)
               (mapv
                 (fn [_]
                   (let [id (swap! id-seq-ref inc)]
                     (op/link-other op &item id))))))

        selected-ref
        (atom nil)]

    (op/handle op ::set-selected!
      (fn [item-op]
        (when-some [selected-op @selected-ref]
          (when (not= selected-op item-op)
            (selected-op ::deselect!)))
        (reset! selected-ref item-op)))

    (op/handle op ::clear!
      (fn [_]
        (op/unlink-all op @op)
        (reset! op [])))

    (op/handle op ::add!
      (fn []
        (swap! op into (make-todos 1000))))

    (op/handle op ::run!
      (fn []
        (reset! selected-ref nil)
        (op/unlink-all op @op)
        (reset! op (make-todos 1000))))

    (op/handle op ::run-lots!
      (fn []
        (reset! selected-ref nil)
        (op/unlink-all op @op)
        (reset! op (make-todos 10000))))

    (op/handle op ::remove-item!
      (fn [item-to-remove]
        (swap! op
          (fn [items]
            (reduce
              (fn [acc item]
                (if-not (identical? item item-to-remove)
                  (conj acc item)
                  (do (op/unlink-other op item)
                      acc)))
              []
              items)))))

    (op/handle op ::swap-rows!
      (fn []
        (swap! op
          (fn [items]
            (let [front-idx 1
                  back-idx 998

                  front (nth items front-idx)
                  back (nth items back-idx)]

              (-> items
                  (assoc front-idx back)
                  (assoc back-idx front)))))))

    (op/handle op ::update-some!
      (fn []
        (let [items @op]
          (loop [idx 0]
            (when (< idx (count items))

              (let [other (nth items idx)]
                (other ::update-text!))

              (recur (+ 10 idx))
              )))))))

(defn &item [op id]
  (let [list-op (op/link-other op &items)]

    ;; since these are all randomly generated we might as well do that on init
    (reset! op {:id id :label (u/make-label)})

    (op/handle op ::toggle-selected!
      (fn []
        (swap! op
          (fn [{:keys [is-selected?] :as state}]

            ;; FIXME: side effect in swap! bad
            ;; not actually in CLJS but a problem if ever ported to CLJ
            (list-op ::set-selected! (if is-selected? nil op))

            (update state :is-selected? not)))))

    (op/handle op ::deselect!
      (fn []
        (swap! op assoc :is-selected? false)))

    (op/handle op ::delete!
      (fn []
        (list-op ::remove-item! op)))

    (op/handle op ::update-text!
      (fn []
        (swap! op update :label str " !!!")))
    ))


(defc row [item-op]
  (bind item
    (sg/watch item-op))

  (render
    (let [{:keys [^boolean is-selected? ^:text id ^:text label]} item]
      (<< [:tr {:class (if is-selected? "danger" "")}
           [:td.col-md-1 id]
           [:td.col-md-4
            [:a {:on-click #(item-op ::toggle-selected!)} label]]
           [:td.col-md-1
            [:a {:on-click #(item-op ::delete!)}
             [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]]]
           [:td.col-md-6]]))))

(defc ui-root []
  (bind list-op
    (op/use &items))

  (bind items
    (sg/watch list-op))

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
                :on-click #(list-op ::run!)}
               "Create 1,000 rows"]]
             [:div.col-sm-6.smallpad
              [:button.btn.btn-primary.btn-block
               {:type "button"
                :id "runlots"
                :on-click #(list-op ::run-lots!)}
               "Create 10,000 rows"]]
             [:div.col-sm-6.smallpad
              [:button.btn.btn-primary.btn-block
               {:type "button"
                :id "add"
                :on-click #(list-op ::add!)}
               "Append 1,000 rows"]]
             [:div.col-sm-6.smallpad
              [:button.btn.btn-primary.btn-block
               {:type "button"
                :id "update"
                :on-click #(list-op ::update-some!)}
               "Update every 10th row"]]
             [:div.col-sm-6.smallpad
              [:button.btn.btn-primary.btn-block
               {:type "button"
                :id "clear"
                :on-click #(list-op ::clear!)}
               "Clear"]]
             [:div.col-sm-6.smallpad
              [:button.btn.btn-primary.btn-block
               {:type "button"
                :id "swaprows"
                :on-click #(list-op ::swap-rows!)}
               "Swap rows"]]]]]]

         [:table.table.table-hover.table-striped.test-data
          [:tbody
           (sg/keyed-seq items op/op-key row)]]

         [:span.preloadicon.glyphicon.glyphicon-remove
          {:aria-hidden "true"}]])))

(defonce root-el
  (js/document.getElementById "main"))

(defonce data-ref
  (-> {}
      (db/configure {})
      (atom)))

(defonce rt-ref
  (-> {}
      (sg/prepare data-ref ::db)))

(defn render []
  (sg/render rt-ref root-el (ui-root)))

(defn init []
  (render))

(defn ^:dev/after-load reload! []
  (render))