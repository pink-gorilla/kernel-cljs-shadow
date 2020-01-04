(ns pinkgorilla.kernel.macros
  (:require [cljs.analyzer]))


;;(defmacro dbg [x]
;;  (when *assert*
;;    `(let [x# ~x]
;;       (println (str '~x ": ") x#)
;;       x#)))


(defmacro dbg [x]
  `(let [x# ~x]
     (println (str '~x ": ") x#)
     x#))

(defmacro inferred-type
  "Returns the inferred type tag for the supplied form."
  [form]
  `'~(cljs.analyzer/infer-tag &env
                              (cljs.analyzer/analyze &env form)))

