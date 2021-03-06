(ns pinkgorilla.kernel.errors
  (:require
   [cljs.spec.alpha :as spec]
   [goog.string :as gstring]
   [goog.string.format]))

(defn Error->map
  "Constructs a data representation for a Error with keys:
    :cause - root cause message
    :phase - error phase
    :via - cause chain, with cause keys:
             :type - exception class symbol
             :message - exception message
             :data - ex-data
             :at - top stack element
    :trace - root cause stack elements"
  [o]
  (let [base (fn [t]
               (merge {:type (cond
                               (instance? ExceptionInfo t) 'ExceptionInfo
                               (instance? js/EvalError t) 'js/EvalError
                               (instance? js/RangeError t) 'js/RangeError
                               (instance? js/ReferenceError t) 'js/ReferenceError
                               (instance? js/SyntaxError t) 'js/SyntaxError
                               (instance? js/URIError t) 'js/URIError
                               (instance? js/Error t) 'js/Error
                               :else nil)}
                      (when-let [msg (ex-message t)]
                        {:message msg})
                      (when-let [ed (ex-data t)]
                        {:data ed})
                      #_(let [st (extract-canonical-stacktrace t)]
                          (when (pos? (count st))
                            {:at st}))))
        via (loop [via [], t o]
              (if t
                (recur (conj via t) (ex-cause t))
                via))
        root (peek via)]
    (merge {:via   (vec (map base via))
            :trace nil #_(extract-canonical-stacktrace (or root o))}
           (when-let [root-msg (ex-message root)]
             {:cause root-msg})
           (when-let [data (ex-data root)]
             {:data data})
           (when-let [phase (-> o ex-data :clojure.error/phase)]
             {:phase phase}))))

(defn ex-triage
  "Returns an analysis of the phase, error, cause, and location of an error that occurred
  based on Throwable data, as returned by Throwable->map. All attributes other than phase
  are optional:
    :clojure.error/phase - keyword phase indicator, one of:
      :read-source :compile-syntax-check :compilation :macro-syntax-check :macroexpansion
      :execution :read-eval-result :print-eval-result
    :clojure.error/source - file name (no path)
    :clojure.error/line - integer line number
    :clojure.error/column - integer column number
    :clojure.error/symbol - symbol being expanded/compiled/invoked
    :clojure.error/class - cause exception class symbol
    :clojure.error/cause - cause exception message
    :clojure.error/spec - explain-data for spec error"
  [datafied-throwable]
  (let [{:keys [via trace phase] :or {phase :execution}} datafied-throwable
        {:keys [type message data]} (last via)
        {::spec/keys [:problems :fn :cljs.spec.test.alpha/caller]} data
        {:keys [:clojure.error/source] :as top-data} (:data (first via))]
    (assoc
     (case phase
       :read-source
       (let [{:keys [:clojure.error/line :clojure.error/column]} data]
         (cond-> (merge (-> via second :data) top-data)
           source (assoc :clojure.error/source source)
           (#{"NO_SOURCE_FILE" "NO_SOURCE_PATH"} source) (dissoc :clojure.error/source)
           message (assoc :clojure.error/cause message)))

       (:compile-syntax-check :compilation :macro-syntax-check :macroexpansion)
       (cond-> top-data
         source (assoc :clojure.error/source source)
         (#{"NO_SOURCE_FILE" "NO_SOURCE_PATH"} source) (dissoc :clojure.error/source)
         type (assoc :clojure.error/class type)
         message (assoc :clojure.error/cause message)
         problems (assoc :clojure.error/spec data))

       (:read-eval-result :print-eval-result)
       (let [[source method file line] (-> trace first)]
         (cond-> top-data
           line (assoc :clojure.error/line line)
           file (assoc :clojure.error/source file)
           (and source method) (assoc :clojure.error/symbol (vector #_java-loc->source source method))
           type (assoc :clojure.error/class type)
           message (assoc :clojure.error/cause message)))

       :execution
       (let [[source method file line] (->> trace #_(drop-while #(core-class? (name (first %)))) first)
             file (first (remove #(or (nil? %) (#{"NO_SOURCE_FILE" "NO_SOURCE_PATH"} %)) [(:file caller) file]))
             err-line (or (:line caller) line)]
         (cond-> {:clojure.error/class type}
           err-line (assoc :clojure.error/line err-line)
           message (assoc :clojure.error/cause message)
           (or fn (and source method)) (assoc :clojure.error/symbol (or fn (vector #_java-loc->source source method)))
           file (assoc :clojure.error/source file)
           problems (assoc :clojure.error/spec data))))
     :clojure.error/phase phase)))

(defn ex-str
  "Returns a string from exception data, as produced by ex-triage.
  The first line summarizes the exception phase and location.
  The subsequent lines describe the cause."
  [{:clojure.error/keys [phase source line column symbol class cause spec] :as triage-data}]
  (let [loc          (str (or source "<cljs repl>") ":" (or line 1) (if column (str ":" column) ""))
        class-name   (name (or class ""))
        simple-class class-name
        cause-type   (if (contains? #{"Exception" "RuntimeException"} simple-class)
                       ""                                   ;; omit, not useful
                       (str " (" simple-class ")"))
        format       gstring/format]
    (case phase
      :read-source
      (format "Syntax error reading source at (%s).\n%s\n" loc cause)

      :macro-syntax-check
      (format "Syntax error macroexpanding %sat (%s).\n%s"
              (if symbol (str symbol " ") "")
              loc
              (if spec
                (with-out-str
                  (spec/explain-out
                   (if true #_(= s/*explain-out* s/explain-printer)
                       (update spec ::spec/problems
                               (fn [probs] (map #(dissoc % :in) probs)))
                       spec)))
                (format "%s\n" cause)))

      :macroexpansion
      (format "Unexpected error%s macroexpanding %sat (%s).\n%s\n"
              cause-type
              (if symbol (str symbol " ") "")
              loc
              cause)

      :compile-syntax-check
      (format "Syntax error%s compiling %sat (%s).\n%s\n"
              cause-type
              (if symbol (str symbol " ") "")
              loc
              cause)

      :compilation
      (format "Unexpected error%s compiling %sat (%s).\n%s\n"
              cause-type
              (if symbol (str symbol " ") "")
              loc
              cause)

      :read-eval-result
      (format "Error reading eval result%s at %s (%s).\n%s\n" cause-type symbol loc cause)

      :print-eval-result
      (format "Error printing return value%s at %s (%s).\n%s\n" cause-type symbol loc cause)

      :execution
      (if spec
        (format "Execution error - invalid arguments to %s at (%s).\n%s"
                symbol
                loc
                (with-out-str
                  (spec/explain-out
                   (if true #_(= s/*explain-out* s/explain-printer)
                       (update spec ::spec/problems
                               (fn [probs] (map #(dissoc % :in) probs)))
                       spec))))
        (format "Execution error%s at %s(%s).\n%s\n"
                cause-type
                (if symbol (str symbol " ") "")
                loc
                cause)))))

(defn error->str [error]
  (ex-str (ex-triage (Error->map error))))
