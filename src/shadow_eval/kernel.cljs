(ns shadow-eval.kernel
  (:require

    ;; evaluate
   [cljs.js :as cljs]
   [shadow.cljs.bootstrap.browser :as boot]

   [clojure.string :as string]))


;; Set up eval environment

(defonce c-state (cljs/empty-state))

(defn eval-str [source cb]
  (cljs/eval-str
   c-state
   source
   "[test]"
   {:eval cljs/js-eval
    :load (partial boot/load c-state)
    :ns   (symbol "demo.user")}
   cb))


(defn init [config cb]
  (boot/init c-state config cb))