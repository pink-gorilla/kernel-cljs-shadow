(ns demo.examples)


;; Source text to eval

(def source-examples 
  ["(ns demo.user
      (:require re-view.hiccup.core
                [cells.cell :refer [cell]]
                [cells.lib :as cell
                 :refer [interval timeout fetch geo-location with-view]
                 :refer-macros [wait]]
                [shapes.core :as shapes :refer [listen
                                                circle square rectangle triangle path text image
                                                position opacity rotate scale
                                                colorize stroke no-stroke fill no-fill
                                                color-names rgb hsl rescale
                                                layer beside above
                                                        ; for functional geometry demo
                                            ;; are these internal only? -jar
                                                assure-shape-seq shape-bounds bounds shape->vector]]
                [re-view.core :include-macros true]
                #_[thi.ng.geom.svg.core :as svg]
                ;[cljs.js]
                [fortune.core]
                )
      (:require-macros [cells.cell :refer [defcell cell]])) 
"
   
   
   "(circle 40)"
   "(for [n (range 10)] n)"
   "(defcell x 10)"
   "(cell (interval 100 inc))"
   "(require '[fortune.core])
    (fortune.core/cookie)"])