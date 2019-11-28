(ns demo.core
  (:require

   [clojure.string :as string]

    ;; view
   [re-view.core :as v :refer [defview]]
   [re-view.hiccup.core :refer [element]]

    ;; things to eval and display
   [lark.value-viewer.core :as views]
   [re-db.d :as d]
   [re-db.patterns :as patterns]
   [cells.cell :as cell]
   [shapes.core :as shapes]
   [thi.ng.geom.svg.core :as svg]

   [pinkgorilla.kernel.cljs-shadow :refer [init!]]
   [demo.examples :refer [source-examples]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol extensions to enable rendering of cells and shapes

(extend-type cells.cell/Cell
  cells.cell/ICellStore
  (put-value! [this value]
    (d/transact! [[:db/add :cells (name this) value]]))
  (get-value [this]
    (d/get :cells (name this)))
  (invalidate! [this]
    (patterns/invalidate! d/*db* :ea_ [:cells (name this)]))
  lark.value-viewer.core/IView
  (view [this] (cells.cell/view this)))

(extend-protocol lark.value-viewer.core/IView
  Var
  (view [this] (@this)))

(extend-type shapes/Shape
  re-view.hiccup.core/IEmitHiccup
  (to-hiccup [this] (shapes/to-hiccup this)))

(extend-protocol cells.cell/IRenderHiccup
  object
  (render-hiccup [this] (re-view.hiccup.core/element this)))

;; kernel setup

(defonce _
  (init!
   {:path         "http://localhost:2705/out/mariacloud"
    :load-on-init '#{fortune.core}}
   (fn []
     (println "ready!!")
     (d/transact! [[:db/add ::eval-state :ready? true]]))))

(defn eval-str [source cb]
  (pinkgorilla.kernel.cljs-shadow/eval-str "demo.user" source cb))





;; Views

(defview show-example
  "Shows a single example, with an editable textarea and value-view."
  {:key                (fn [_ source] source)
   :view/initial-state (fn [_ source]
                         {:source source})
   :view/did-mount     (fn [this source]
                         (eval-str source (partial swap! (:view/state this) assoc :result)))}
  [{:keys [view/state]}]
  (let [{:keys [source result]} @state]
    [:.ma3.flex
     [:.bg-near-white.pa3.flex-none
      {:style {:width 450}}
      [:textarea.bn.pre.w-100.f6.lh-copy.bg-near-white.outline-0.monospace.overflow-auto
       {:value     (:source @state)
        :style     {:height (str (+ 1.75 (* 1.3125 (count (re-seq #"\n|\r\n" source)))) "rem")}
        :on-change #(let [source (.. % -target -value)]
                      (swap! state assoc :source source)
                      (eval-str source (partial swap! state assoc :result)))}]]

     (let [{:keys [error value]} result]
       [:.pre-wrap
        (if error (element [:.pa3.bg-washed-red
                            [:.b (ex-message error)]
                            [:div (str (ex-data error))]
                            (pr-str (ex-cause error))])
            [:.pa3 (views/format-value value)])])]))


(defview examples
  "Root view for the page"
  []
  (if-not (d/get ::eval-state :ready?)
    "Loading..."
    [:.monospace.f6
     (map show-example source-examples)])) ;; TODO:  the first cell needs to be loaded first



(defn render []
  (v/render-to-dom (examples) "shadow-eval"))


