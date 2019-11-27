(ns pinkgorilla.kernel.cljs-shadow
  (:require
    ;; evaluate
   [cljs.js :as cljs]
   [shadow.cljs.bootstrap.browser :as boot]
   ))


;; Set up eval environment

(defonce c-state (cljs/empty-state))

(defn init! [config cb]
  (boot/init c-state config cb))


(defn eval-str [ns-str source cb] 
  (cljs/eval-str
   c-state
   source
   "[kernel-cljs]"
   {:eval cljs/js-eval
    :load (partial boot/load c-state)
    :ns   (symbol ns-str )}
   cb))


