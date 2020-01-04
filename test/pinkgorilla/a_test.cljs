(ns pinkgorilla.a-test
  (:require-macros

   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.test :refer [deftest is are async use-fixtures]]
   [cljs.core.async :refer [<!]]
   [clojure.string :as string]))

(defn add [x y] (+ x y))

(deftest add-x-to-y-a-few-times
  (is (= 5 (add 2 3)))
  (is (= 5 (add 1 4)))
  (is (= 5 (add 3 2))))