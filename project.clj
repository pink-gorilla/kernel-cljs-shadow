(defproject  org.pinkgorilla/kernel-cljs-shadow "0.0.23"
  :description "A cljs kernel using shadow-cljs for PinkGorilla Notebook."
  :url "https://github.com/pink-gorilla/kernel-cljs-shadow"
  :license {:name "MIT"}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/release_username
                                     :password :env/release_password
                                     :sign-releases false}]]  
  :dependencies  [; dependencies of cljs-kernel-shadow. The gorilla-notebook will automatically fetch this transient dependencies
                  [thheller/shadow-cljs "2.8.80"] ; needed for the dependency loader in self hosted clojurescript
                  [cljs-await "1.0.2"]  ; "promisify callbacks"
                  [reagent "0.8.1"
                   :exclusions [org.clojure/tools.reader]] ; add custom renderers to pinkie.
                  ]
  :profiles {:dev {:dependencies [[clj-kondo "2019.11.23"]]
                   :plugins      [[lein-cljfmt "0.6.6"]
                                  [lein-cloverage "1.1.2"]]
                   :aliases      {"clj-kondo" ["run" "-m" "clj-kondo.main"]}
                   :cloverage    {:codecov? true
                                  ;; In case we want to exclude stuff
                                  ;; :ns-exclude-regex [#".*util.instrument"]
                                  ;; :test-ns-regex [#"^((?!debug-integration-test).)*$$"]
                                  }
                   ;; TODO : Make cljfmt really nice : https://devhub.io/repos/bbatsov-cljfmt
                   :cljfmt       {:indents {as->                [[:inner 0]]
                                            with-debug-bindings [[:inner 0]]
                                            merge-meta          [[:inner 0]]
                                            try-if-let          [[:block 1]]}}}}
    :aliases {;; "build-shadow-ci" ["run" "-m" "shadow.cljs.devtools.cli" "compile" ":ci"]
              "bump-version" ["change" "version" "leiningen.release/bump-version"]}

:release-tasks [["vcs" "assert-committed"]
                ["bump-version" "release"]
                ["vcs" "commit" "Release %s"]
                ["vcs" "tag" "v" "--no-sign"]
                ["deploy"]
                ["bump-version"]
                ["vcs" "commit" "Begin %s"]
                ["vcs" "push"]])