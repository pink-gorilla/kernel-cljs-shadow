(ns pinkgorilla.eval-test
  (:require-macros
   [pinkgorilla.kernel.macros :refer [dbg]]
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.test :refer [deftest is are async use-fixtures]]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]
   [pinkgorilla.kernel.repl :refer [reset-state-eval! reset-ns-eval!]]
   [pinkgorilla.kernel.clojure :refer [read-string create-state-eval the-eval result-as-str split-expressions]]))





(def ^:dynamic *klipse-settings* {})
(def ^:dynamic *verbose?* false)

(set! *klipse-settings* {;:cached_ns_root "http://localhost:8080/"
                         ;:bundled_ns_root "cljs-out/dev/"
                         :verbose true})

(set! *verbose?* true)

(defn remove-chars [s]
  (if (string? s)
    (string/replace s #"\n|\s" "")
    s))

(use-fixtures :each
  {:before #(async done
                   (go (reset-state-eval!)
                       (reset-ns-eval!)
                       (<! (create-state-eval))
                       (done)))})

(defn a= [& args]
  (apply = (map remove-chars args)))

(defn b= [[status-a a] [status-b b]]
  (and (= status-a status-b)
       (a= a b)))



(deftest test-eval-fortune-cookie
  "eval with several expressions"
  (with-redefs [rand-int (constantly 1)]
    (async done
           (go (are [input-clj output-clj]
                    (b= (<! (the-eval input-clj)) [:ok output-clj])

                 "(ns bongo.trott (:require [fortune.db] [fortune.core]))
                (fortune.core/cookie)"

                 "nice")
               (done)))))







(deftest test-eval-2
  "eval with several expressions"
  (async done
         (go (are [input-clj output-clj]
                  (b= (<! (the-eval input-clj)) [:ok output-clj])
               "(if (> 100 10) 1 2)" 1
               "(ns aa.cc) (def x 12)
          (+ x 5)" 17
               "(+ 1 2)" 3
               "(+ 1 2)\n\n   \n" 3
               "(if 1 2 3)" 2
               "(map inc [1 2 3])" '(2 3 4)
               "(defn append-cyclic[lst a] (concat (rest lst) [a]))
          (-> (repeat 3 nil)
              (append-cyclic  9)
              (append-cyclic  10)
              (append-cyclic  11)
              (append-cyclic  12))" '(10 11 12)
               "(ns foo.core) ::aa" :foo.core/aa
               "(ns my.aa) (+ 1 2)" 3
               "`(1 2)" '(1 2)
               "(ns my.bb) (def a 1) `(1 a)" '(1 my.bb/a))
             (done))))




