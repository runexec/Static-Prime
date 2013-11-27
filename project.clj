(defproject static-prime "1-alpha"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2080"]
                 [domina "1.0.2"]
                 [markdown-clj "0.9.35"]
                 [crate "0.2.4"]
                 [org.clojure/core.async "0.1.256.0-1bf8cf-alpha"]
                 [lib-noir "0.7.6"]
                 [hiccup "1.0.4"]]

  :plugins [[lein-cljsbuild "1.0.1-SNAPSHOT"]
            [lein-ring "0.8.8"]]

  :source-paths ["src"]

  :cljsbuild { 
              :builds [{:id "static-prime"
                        :source-paths ["src-cljs"]
                        :compiler {
                                   :output-to "resources/public/js/static_prime.js"
                                   :output-dir "resources/public/js/"
                                   :optimizations :whitespace}}]}

  :ring {:handler static-prime.handler/app}

  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]]}})
