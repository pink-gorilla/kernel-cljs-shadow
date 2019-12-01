(ns pinkgorilla.kernel.clojure
  (:require-macros
   [pinkgorilla.kernel.macros :refer [dbg]]
   [cljs.core.async.macros :refer [go go-loop]])
  (:require

   [goog.dom :as gdom]
   [clojure.string :refer [blank?]]
   [clojure.pprint :as pprint]
   [clojure.string :as s]
   [cljs.core.async :refer [timeout chan close! put! <!]]
   [cljs-await.core :refer [await-cb]]

   [cljs.analyzer :as ana]
   [cljs.tools.reader :as r]
   [cljs.tools.reader.reader-types :as rt]
   [cljs.compiler :as compiler]

   [cljs.env :as env]
   [cljs.js :as cljs]
   [cljs.tagged-literals :as tags]



   [pinkgorilla.kernel.errors :refer [error->str]] ;; once error->str is in cljs, take it from there
   [pinkgorilla.kernel.repl :refer [get-completions current-alias-map
                                    st create-state-compile
                                    current-ns-eval current-ns-compile
                                    reset-ns-eval! reset-ns-compile!]]
   [pinkgorilla.kernel.guard :refer [min-max-eval-duration my-emits watchdog]]

   [shadow.cljs.bootstrap.browser :as boot]

   ;[klipse-clj.lang.clojure.io :as io]
   ;klipse-clj.lang.clojure.bundled-namespaces
   ;gadjett.core-fn
   ))

;shadow.js.
; shadow.js.jsRequire ("full-string-name")

(defn test! []
  (.log js/console "hello, world!")
  )
;  (j/call js/goog :getObjectByName (str name))

(declare core-eval-an-exp)


;; (defn load-core-macros-cache []
;;   (io/load-ns-from-file @st 'cljs.core$macros (str (io/bundled-ns-root) "/cljs/core$macros.cljc.cache.json")))

;; (defn init-custom-macros []
;;   (go
;;    (<! (load-core-macros-cache))
;;    (doseq [my-macros ["(require '[klipse-clj.repl :refer-macros [doc]])"
;;                       "(require-macros '[klipse-clj.macros :refer [dbg inferred-type]])"]]
;;      (<! (first (core-eval-an-exp my-macros {:st @st :ns current-ns-eval}))))))

   "(ns demo.user)"
  (defn- ->callback [cst config]
    (fn [cb] (boot/init cst config cb)))


(def fff (atom true))

(defn init-boot [cst config]
  (go
    (let [[err res] (<! (await-cb  (->callback cst config)))]
      (println "init-bool loader result: " res))))


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
                    }})

(defn create-state-eval []
  (if @st
    (go @st)
    (do
      (println "creating new cljs state..")
      (reset! st (cljs/empty-state))
      (if  @fff
        (do
          (reset! fff false)
          (init-boot @st config))
        (go @st)
      ;;(init-custom-macros)
          ))))

(defn- reader-error?
  [e]
  (= :reader-exception (:type (ex-data e))))

(defn- error-message [error]
  (if (instance? ExceptionInfo error)
    (ex-message error)
    (.-message error)))

(defn display-err [error]
  (try
    (cond
      (-> (ex-data (ex-cause error))
          (contains? :clojure.error/phase))
      (error->str (ex-cause error))
      (reader-error? error)  (str "Syntax error reading source." "\n" (error-message error))
      :else
      (str "Execution error.\n"
           (ex-message error)
           (when (ex-cause error) (str ": " (ex-cause error)))))
    (catch js/Object e
      (str "Exception: ") e)))

(defn display [value {:keys [print-length beautify-strings]}]
  (try
    (with-redefs [*print-length* print-length]
      [:ok (with-out-str (pprint/pprint
                          (if (and (string? value) beautify-strings)
                            (symbol value)
                            value)))])
    (catch js/Object e
      [:error (display-err (ex-info (str e)
                                    {:tag :klipse/print-error}
                                    (ex-info (str e)
                                             #:clojure.error{:phase :print-eval-result})))])))

(defn update-current-ns [{:keys [ns form warning error value success?]} verbose? current-ns]
  (when-not error
    (when verbose? (js/console.info "update-current-ns:" (str ns)))
    (reset! current-ns ns)))

(defn result-as-str [{:keys [ns form warning error value success?] :as args} opts]
  (let [status (if error :error :ok)]
    (if-not error
      (display value opts)
      [:error (display-err error)])))

(defn result-as-prepl-map [form-str {:keys [ns form warning error value success?] :as args} opts]
  (let [[status val] (result-as-str args opts)]
    {:tag (if (= :error status) :err :ret)
     :ns ns
     :val val
     :form form-str}))

(defn read-result [{:keys [form warning error value success?]}]
  (let [status (if error :error :ok)
        res (if-not error
              value
              error)]
    [status res]))

(defn advanced-compile [code]
  code
  #_(let [flags  (clj->js {:jsCode [{:src code}]
                           :compilationLevel "ADVANCED"})
          _ (js/console.log flags)
          result (aget (js/compile flags) "compiledCode")]
      result))

(defn convert-compile-res [{:keys [value error] :as foo}]
  (let [status (if error :error :ok)
        res (if error
              error
              (advanced-compile value))]
    [status res]))

(defn my-eval [{:keys [file source file lang name path cache] :as args}]
  (watchdog)
  (cljs/js-eval args))

(defn eval-for-compilation [{:keys [source]}]
  source)

; store the original compiler/emits - as I'm afraid things might get wrong with all the with-redefs (especially with core.async. See http://dev.clojure.org/jira/browse/CLJS-1634
(def original-emits compiler/emits)

(defn core-compile-an-exp [s {:keys [static-fns external-libs max-eval-duration compile-display-guard verbose? st ns]
                              :or {static-fns false external-libs nil max-eval-duration min-max-eval-duration compile-display-guard false verbose? false st nil}}]
  (let [c (chan)
        max-eval-duration (max max-eval-duration min-max-eval-duration)
        the-emits (if compile-display-guard (partial my-emits max-eval-duration) original-emits)]
    (with-redefs [;compiler/emits the-emits
                  ]
      (cljs/eval-str st
                     s
                     "compile.klipse"
                     {:eval       eval-for-compilation
                      :ns         @ns
                      :static-fns static-fns
                      :*compiler* (set! env/*compiler* st)
                      :verbose    verbose?
                      :load      (partial boot/load @st) ; (partial io/load-ns external-libs)
                      }
                     (fn [res]
                       (update-current-ns res verbose? ns)
                       (put! c res))))
    c))


(defn warning-handler [c warning-type env extra]
  (when (warning-type ana/*cljs-warnings*)
    (when-let [s (ana/error-message warning-type extra)]
      (put! c (str (ana/message env (str "WARNING: " s)) "\n")))))

(defn read-until-closed! [c]
  (let [d (chan)]
    (go-loop
     [res []]
      (let [x (<! c)]
        (if (nil? x)
          (put! d res)
          (recur (conj res x)))))
    d))

(defn core-eval-an-exp [s {:keys [static-fns external-libs max-eval-duration verbose? st ns] :or {static-fns false external-libs nil max-eval-duration min-max-eval-duration verbose? false st nil}}]
  (let [res-chan (chan)
        warnings-chan (chan 1024)
        agg-warnings-chan (chan)
        max-eval-duration (max max-eval-duration min-max-eval-duration)]
    (binding [ana/*cljs-warning-handlers* [(partial warning-handler warnings-chan)]]
      (with-redefs [;compiler/emits (partial my-emits max-eval-duration) ;; TODO Dec 19 2018 - it breaks simple compilation
                    ]
        ; we have to set `env/*compiler*` because `binding` and core.async don't play well together (https://www.reddit.com/r/Clojure/comments/4wrjw5/withredefs_doesnt_play_well_with_coreasync/) and the code of `eval-str` uses `binding` of `env/*compiler*`.
        (cljs/eval-str st
                       s
                       "my.klipse"
                       {:eval          my-eval
                        :ns            @ns
                        :def-emits-var true
                        :verbose       verbose?
                        :*compiler*    (set! env/*compiler* st)
                        :context       :expr
                        :static-fns    static-fns
                        :load        (partial boot/load @st) ;;  (partial io/load-ns external-libs)
                        }
                       (fn [res]
                         (close! warnings-chan)
                         (go
                           (let [warnings (<! (read-until-closed! warnings-chan))]
                             (update-current-ns res verbose? ns)
                             (put! res-chan res)
                             (put! agg-warnings-chan (s/join "" warnings))))))
        [res-chan agg-warnings-chan]))))

(defn- read-chars
  [reader]
  (loop [res []]
    (if-let [ch (rt/read-char reader)]
      (recur (conj res ch))
      res)))

(defn reader-content [r]
  (apply str (read-chars r)))

(defn- data-readers []
  tags/*cljs-data-readers*)

(defn first-exp-and-rest [s st ns]
  (binding [r/*alias-map* (current-alias-map ns st)
            *ns* ns
            ana/*cljs-ns* ns
            env/*compiler* st
            r/resolve-symbol ana/resolve-symbol
            r/*data-readers* (data-readers)                 ;; see relevant code in Planck
            ]
    (let [sentinel (js-obj)
          reader (rt/string-push-back-reader s)
          res (r/read reader false sentinel)]
      (if (= sentinel res)
        ["" ""]
        (let [rest-s (reader-content reader)
              first-exp (subs s 0 (- (count s) (count rest-s)))]
          [(s/replace first-exp #"^[\s\n]*" "")
           rest-s])))))


(defn read-string
  "A good way to read a string as cljs.reader/read-string has many bugs."
  ([s] (read-string s @st @current-ns-eval))
  ([s st ns]
   (binding [r/*alias-map* (current-alias-map ns st)
             *ns* ns
             ana/*cljs-ns* ns
             env/*compiler* st
             r/resolve-symbol ana/resolve-symbol
             r/*data-readers* (data-readers)                 ;; see relevant code in Planck
             ]
     (let [reader (rt/string-push-back-reader s)]
       (r/read reader)))))


(defn split-expressions [s]
  (loop [s s res []]
    (if (empty? s)
      res
      (let [[exp rest-s] (first-exp-and-rest s @st @current-ns-eval)]
        (if (empty? exp)
          (recur rest-s res)
          (recur rest-s (conj res exp)))))))



(defn populate-err [err {:keys [result-element container]}]
  (when (and container (not result-element))
    (gdom/setTextContent container (display-err (:error err))))
  err)


(def completions get-completions)

(defn core-eval [s opts]
  (let [res-chan (chan)
        warnings-chan (chan)]
    (go
      (try
        (<! (create-state-eval))
        (loop [[exp rest-exps]  (first-exp-and-rest s @st @current-ns-eval)
               last-res nil
               warnings ""]
          (if (not (empty? exp))
            (let [[c d] (core-eval-an-exp exp (assoc opts :st @st :ns current-ns-eval))
                  res (<! c)]
              (if (:error res)
                (do
                  (close! warnings-chan)
                  (put! res-chan res)
                  (populate-err res opts))
                (recur (first-exp-and-rest rest-exps @st @current-ns-eval)
                       res
                       (str warnings (<! d)))))
            (do
              (put! warnings-chan warnings)
              (put! res-chan last-res))))
        (catch js/Object e
          (close! warnings-chan)
          (put! res-chan {:error e})
          (populate-err {:error e} opts))))
    [res-chan warnings-chan]))


(defn core-compile [s opts]
  (go
    (try
      (loop [[exp rest-exps] (first-exp-and-rest s (create-state-compile) @current-ns-compile)
             all-res ""]
        (if (not (empty? exp))
          (let [res (<! (core-compile-an-exp exp (assoc opts :st (create-state-compile) :ns current-ns-compile)))]
            (if (:error res)
              res
              (recur (first-exp-and-rest rest-exps (create-state-compile) @current-ns-compile)
                     (str all-res (:value res)))))
          {:value all-res}))
      (catch js/Object e
        {:error e}))))


(defn eval-async-map [s opts]
  (go
    (let [[res-chan warnings-chan] (core-eval s opts)
          res-str (-> (<! res-chan)
                      (result-as-str opts))
          warnings (<! warnings-chan)]
      {:warnings warnings
       :res res-str})))


(defn eval-async [s opts]
  (go
    (let [[res-chan warnings-chan] (core-eval s opts)
          res-str (-> (<! res-chan)
                      (result-as-str opts))
          _ (<! warnings-chan)]
      res-str)))


(defn eval-full [s opts]
  (go
    (let [[res-chan warnings-chan] (core-eval s opts)
          res-str (-> (<! res-chan)
                      (result-as-str opts))
          w (<! warnings-chan)]
      [res-str w])))



(defn the-eval
  "used for testing"
  ([s] (the-eval s {}))
  ([s opts] (go (-> (<! (first (core-eval s opts)))
                    read-result))))

(defn eval-and-callback
  "to be called from javascript"
  ^{:export true}
  [s cb]
  (go (-> (<! (the-eval s))
          clj->js
          cb)))

(defn str-compile "useful for testing and js export"
  ^{:export true}
  ([exp] (str-compile exp {}))
  ([exp opts] (go (-> (<! (core-compile exp opts))
                      convert-compile-res))))

(defn compile-async [exp opts]
  (go (-> (<! (core-compile exp opts))
          (convert-compile-res))))

(defn str-compile-async [exp opts]
  (go (-> (<! (compile-async exp opts))
          second
          str)))

(defn str-eval-async [exp {:keys [container-id setup-container-fn verbose?] :as opts}]
  (let [c (chan)]
    (when verbose? (js/console.info "[clojure] evaluating" exp))
    (go
      (if (blank? exp)
        (put! c "")
        (do
          (when setup-container-fn
            (setup-container-fn container-id))
          (binding [*print-newline* true
                    *print-fn* #(put! c %)]
            (let [{:keys [warnings res]} (<! (eval-async-map exp opts))]
              (put! c (str warnings (second res))))))))
    c))

(defn eval-async-prepl
  ([s] (eval-async-prepl s {}))
  ([s opts]
   (let [c (chan)]
     (go
       (binding [*print-fn* (fn [s]
                              (put! c {:val s :tag :out}))]
         (as-> (<! (first (core-eval s opts))) $
           (put! c (result-as-prepl-map s $ opts)))))
     c)))

(defn main []
  (js/console.log "main"))

(comment
  (enable-console-print!)
  (go (<! (create-state-eval))
      (println "init done"))
  (go (println (<! (the-eval "(ns my.hello$macros)
            (defmacro hello
            [x]
            `(inc ~x))
            (hello nil nil 13)" {:verbose? false}))))
  (go (println (<! (the-eval "(inferred-type (if x 2 \"a\"))" {:verbose? true}))))
  (go (println (<! (eval-async-map "(map inc [1 2 3])" {}))))
  (go (def a (<! (eval-async-prepl "(map inc [1 2 3])" {:print-length 1}))))
  a
  (go (def b (<! (eval-async-prepl "(map inc [1 2 3)" {}))))
  b

  (def c (eval-async-prepl "(do (println (+ 1 2)) (println 87) 89)"))
  (go (def bbb (<! c)))
  bbb
  (println 99))


