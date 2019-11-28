(defproject  org.pinkgorilla/kernel-cljs-shadow "0.0.1"
  :description "A cñljs kernel using shadow-cljs for PinkGorilla Notebook."
  :url "https://github.com/pink-gorilla/kernel-cljs-shadow"
  :license {:name "MIT"}
  ;:deploy-repositories [["releases" :clojars]]
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :username "pinkgorillawb"
                             :sign-releases false}]]
  :dependencies
  [[thheller/shadow-cljs "2.0.40"] ; needed for the deopendency loader in self hosted clojurescript
   ;[re-view "0.4.6"]
   ;[lark/cells "0.1.5"]
   ;[lark/tools "0.1.19"]
   ;[maria/shapes "0.1.0"]
   ;[thi.ng/geom "0.0.908"]
   ]
  :source-paths ["src"])