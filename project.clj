(defproject metosin/testit "0.0.2-SNAPSHOT"
  :description "Midje style assertions for clojure.test"
  :license {:name "Eclipse Public License", :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tach "0.3.0"]
            [lein-doo "0.1.7"]
            [lein-cljsbuild "1.1.6"]]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [clj-http "3.4.1" :scope "test"]
                 [org.clojure/clojurescript "1.9.518"]
                 [net.cgrand/macrovich "0.2.0"]]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"] 
                                  [figwheel-sidecar "0.5.8"]]}}
  :test-paths ["test/cljc"]
  :aliases {"all" ["with-profile" "dev"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-phantom" ["doo" "phantom" "test"]
            "test-node" ["doo" "node" "node-test"]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main testit.doo-runner
                                   :optimizations :none}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main testit.doo-runner
                                   :optimizations :none
                                   :target :nodejs}}]})
 
