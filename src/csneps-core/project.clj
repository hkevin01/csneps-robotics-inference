(defproject csri "0.1.0-SNAPSHOT"
  :description "CSNePS-based concurrent inference for robotics/CV/GNC/medical applications"
  :url "https://github.com/username/csneps-robotics-inference"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/data.json "2.4.0"]
                 [cheshire "5.12.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.11"]
                 ;; CSNePS integration - using local checkout initially
                 ;; [csneps "3.0.0"] ;; Uncomment when CSNePS is available in maven
                 ;; gRPC dependencies
                 [io.grpc/grpc-netty-shaded "1.58.0"]
                 [io.grpc/grpc-protobuf "1.58.0"]
                 [io.grpc/grpc-stub "1.58.0"]
                 [io.grpc/grpc-services "1.58.0"]
                 [com.google.protobuf/protobuf-java "3.24.4"]
                 ;; Web server and HTTP bridge
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-params "0.3.0"]
                 [compojure "1.7.0"]
                 [hiccup "1.0.5"]
                 ;; JSON Schema validation (optional)
                 [com.github.fge/json-schema-validator "2.2.6"]]

  :main ^:skip-aot csri.core
  :target-path "target/%s"

  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]
                                  [criterium "0.4.6"]]
                   :plugins [[lein-ancient "1.0.0-RC3"]
                             [lein-kibit "0.1.8"]
                             [lein-bikeshed "0.5.2"]]
                   :source-paths ["dev"]}

             :test {:resource-paths ["test-resources"]}

             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]

  :jvm-opts ["-Xmx2g" "-XX:+UseG1GC"]

  :aliases {"test-all" ["do" ["clean"] ["test"]]
            "lint" ["do" ["kibit"] ["bikeshed"]]
            "dev" ["repl" ":start-server" ":port" "7888"]
            "http-server" ["run" "-m" "csri.http-server"]}

  :repositories [["central" "https://repo1.maven.org/maven2/"]
                 ["clojars" "https://clojars.org/repo/"]])
