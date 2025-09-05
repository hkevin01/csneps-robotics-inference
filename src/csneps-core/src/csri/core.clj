(ns csri.core
  "Main entry point for CSNePS Robotics Inference system."
  (:require [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [clojure.core.async :as async]
            [csri.kb.ontology :as ontology]
            [csri.kb.rules :as rules]
            [csri.kb.assertions :as assertions]
            [csri.kb.queries :as queries]
            [csri.adapters.grpc-server :as grpc]
            [csri.web.server :as web])
  (:gen-class))

(def ^:dynamic *system-state* (atom {:running false
                                     :knowledge-base nil
                                     :inference-engine nil
                                     :grpc-server nil
                                     :web-server nil}))

(defn load-seed-knowledge!
  "Load initial knowledge base from EDN configuration file."
  [kb-file]
  (try
    (let [kb-data (-> kb-file slurp edn/read-string)]
      (log/info "Loading seed knowledge from" kb-file)
      (ontology/initialize-ontology! (:entities kb-data) (:relations kb-data))
      (rules/load-rules! (:rules kb-data))
      (assertions/load-facts! (:facts kb-data))
      (log/info "Successfully loaded seed knowledge"))
    (catch Exception e
      (log/error e "Failed to load seed knowledge from" kb-file)
      (throw e))))

(defn start-inference-engine!
  "Initialize and start the concurrent inference engine."
  []
  (log/info "Starting CSNePS inference engine...")
  (let [engine-chan (async/chan 1000)]
    (async/go-loop []
      (when-let [observation (async/<! engine-chan)]
        (try
          (log/debug "Processing observation:" observation)
          (assertions/process-observation! observation)
          (rules/trigger-inference! observation)
        (catch Exception e
          (log/error e "Error processing observation:" observation)))
        (recur)))
    (swap! *system-state* assoc :inference-engine engine-chan)
    (log/info "Inference engine started")))

(defn start-grpc-server!
  "Start the gRPC server for external communication."
  [port]
  (log/info "Starting gRPC server on port" port)
  (let [server (grpc/start-server port)]
    (swap! *system-state* assoc :grpc-server server)
    (log/info "gRPC server started on port" port)))

(defn start-web-server!
  "Start the web interface server."
  [port]
  (log/info "Starting web server on port" port)
  (let [server (web/start-server port)]
    (swap! *system-state* assoc :web-server server)
    (log/info "Web server started on port" port)))

(defn start-system!
  "Start the complete CSNePS Robotics Inference system."
  [& {:keys [kb-file grpc-port web-port]
      :or {kb-file "resources/sample-kb.edn"
           grpc-port 50051
           web-port 8080}}]
  (try
    (log/info "Starting CSNePS Robotics Inference system...")
    
    ;; Load initial knowledge
    (load-seed-knowledge! kb-file)
    
    ;; Start core components
    (start-inference-engine!)
    (start-grpc-server! grpc-port)
    (start-web-server! web-port)
    
    (swap! *system-state* assoc :running true)
    (log/info "System started successfully")
    (log/info "gRPC endpoint: localhost:" grpc-port)
    (log/info "Web interface: http://localhost:" web-port)
    
    (catch Exception e
      (log/error e "Failed to start system")
      (stop-system!)
      (throw e))))

(defn stop-system!
  "Gracefully shutdown the system."
  []
  (log/info "Stopping CSNePS Robotics Inference system...")
  (let [state @*system-state*]
    (when (:grpc-server state)
      (grpc/stop-server (:grpc-server state)))
    (when (:web-server state)
      (web/stop-server (:web-server state)))
    (when (:inference-engine state)
      (async/close! (:inference-engine state))))
  
  (swap! *system-state* assoc :running false)
  (log/info "System stopped"))

(defn system-status
  "Get current system status and statistics."
  []
  (let [state @*system-state*]
    {:running (:running state)
     :components {:knowledge-base (boolean (:knowledge-base state))
                  :inference-engine (boolean (:inference-engine state))
                  :grpc-server (boolean (:grpc-server state))
                  :web-server (boolean (:web-server state))}
     :statistics {:total-facts (assertions/fact-count)
                  :active-rules (rules/rule-count)
                  :recent-inferences (rules/recent-inference-count)}}))

(defn -main
  "Main entry point. Supports both CLI and GUI modes."
  [& args]
  (let [cli-mode? (some #{"-c" "--cli"} args)
        kb-file (or (some->> args
                             (partition 2 1)
                             (filter #(= (first %) "--kb"))
                             first
                             second)
                    "resources/sample-kb.edn")]
    
    ;; Setup shutdown hook
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(stop-system!)))
    
    (if cli-mode?
      (do
        (log/info "Starting in CLI mode")
        (start-system! :kb-file kb-file)
        ;; Keep running until interrupted
        (loop []
          (Thread/sleep 1000)
          (when (:running @*system-state*)
            (recur))))
      (do
        (log/info "Starting in GUI mode")
        (start-system! :kb-file kb-file)
        ;; TODO: Launch CSNePS GUI
        (log/warn "GUI mode not yet implemented, running in CLI mode")
        (loop []
          (Thread/sleep 1000)
          (when (:running @*system-state*)
            (recur)))))))
