(defproject  org.pinkgorilla/kernel-cljs-shadow "0.0.9"
  :description "A cñljs kernel using shadow-cljs for PinkGorilla Notebook."
  :url "https://github.com/pink-gorilla/kernel-cljs-shadow"
  :license {:name "MIT"}
  ;:deploy-repositories [["releases" :clojars]]
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :username "pinkgorillawb"
                             :sign-releases false}]]
  :dependencies
  [ ; dependencies of cljs-kernel-shadow. The gorilla-notebook will automatically fetch this transient dependencies
   [thheller/shadow-cljs "2.8.76"] ; needed for the deopendency loader in self hosted clojurescript
   [cljs-await "1.0.2"]  ; "promisify cllbacks"
   ]
  :source-paths ["src"])