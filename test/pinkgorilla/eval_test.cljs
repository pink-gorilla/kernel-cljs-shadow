(ns pinkgorilla.eval-test
  (:require-macros
   [pinkgorilla.kernel.macros :refer [dbg]]
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.test :refer [deftest is are async use-fixtures]]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]
   [pinkgorilla.kernel.repl :refer [reset-state-eval! reset-ns-eval!]]
   [pinkgorilla.kernel.clojure :refer [read-string init! create-state-eval  the-eval result-as-str split-expressions eval-full]]))

(def config
  {:path         "http://localhost:2705/out/gorilla"
   :load-on-init '#{fortune.core
                    awb99.shapes.core
                    quil.core
                    quil.middleware
                    ;quil.sketch
                    ;quil.util
                    reagent.core
                    pinkgorilla.shadow
                    module$node_modules$moment$moment   ; namespace convention of shadow-cljs for npm modules
                    ;ajax.core ; http requests
                    pinkgorilla.ui.leaflet
                    pinkgorilla.ui.player ; video player
                    }})




; (def ^:dynamic *klipse-settings* {})
; (def ^:dynamic *verbose?* false)
; (set! *klipse-settings* {;:cached_ns_root "http://localhost:8080/"
;                         ;:bundled_ns_root "cljs-out/dev/"
;                         :verbose true})
;(set! *verbose?* true)


(defn remove-chars [s]
  (if (string? s)
    (string/replace s #"\n|\s" "")
    s))

(use-fixtures :each
  {:before #(async done
                   (go ;(reset-state-eval!)
                     (init! config)
                     (reset-ns-eval!)
                     (<! (create-state-eval))
                     (println "test setup done.")
                     (done)))})

(defn a= [& args]
  (apply = (map remove-chars args)))

(defn b= [[status-a a] [status-b b]]
  (and (= status-a status-b)
       (a= a b)))

(deftest test-eval-full-errors
  "eval calling a non existing function should produce an error"
  (async done
         (go (are [input-clj output-clj]
                  (b= (<! (eval-full input-clj {})) [[:error (first output-clj)] (last output-clj)])

               "(ns hatschie)
                (not-so-beautiful 7)
                 "
               ["Execution error.\nERROR: TypeError: hatschie.not_so_beautiful is undefined"
               ;["Execution error.\nERROR: TypeError: Cannot read property 'call' of undefined"
                nil])
             (done))))

(deftest test-eval-expressions-multiple
  "eval with several expressions"
  (async done
         (go (are [input-clj output-clj]
                  (b= (<! (the-eval input-clj)) [:ok output-clj])

               "(if (> 100 10) 1 2)" 1

               "(ns aa.cc) 
                (def x 12)
                (+ x 5)" 17

               "(+ 1 2)" 3

               "(+ 1 2)\n\n   \n" 3

               "(if 1 2 3)" 2

               "(map inc [1 2 3])" '(2 3 4)

               "(defn append-cyclic [lst a] (concat (rest lst) [a]))
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

(deftest test-eval-fortune-cookie
  "eval fortune cookie"
  (with-redefs [rand-int (constantly 1)]  ;; redefs need to be done nside the self hosted clojurescript!!
    (async done
           (go (are [input-clj output-clj]
                    (b= (<! (the-eval input-clj)) [:ok output-clj])

                 "(ns bongo.trott (:require [fortune.db] [fortune.core]))
                  (println \"ggg\")
                  (fortune.core/cookie 7)"
                 "Don’t pursue happiness – create it.")
               (done)))))

(deftest test-eval-full-warnings
  "eval fortune cookie with warnings"
  (async done
         (go (are [input-clj output-clj]
                  (b= (<! (eval-full input-clj {})) [[:ok (first output-clj)] (last output-clj)])

               "(ns bongo.trott2 (:require [fortune.db] [fortune.core]))
                  (println \"ggg \")
                  (println \"ggg \")
                  (+ x 7)
                  (fortune.core/cookie 7)"
               ["\"Don’t pursue happiness – create it.\"\n"
                "WARNING: Use of undeclared Var bongo.trott2/x at line 1 \n"])
             (done))))


;(b= [[:ok "\"Don’t pursue happiness – create it.\"\n"] "WARNING: Use of undeclared Var bongo.trott2/x at line 1 \n"] 
;     [:ok "\"Don’t pursue happiness – create it.\"\n" "WARNING: Use of undeclared Var bongo.trott2/x at line 1 \n"]))




