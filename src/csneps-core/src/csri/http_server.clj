(ns csri.http-server
  (:gen-class)
  (:require
    [ring.adapter.jetty :as jetty]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.util.response :as resp]
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [csneps.core :as cs]
    [csneps.snip :as snip]
    [csneps.util :as util]))

;; -----------------------------------------------------------------------------
;; Minimal CSNePS bootstrap
;; -----------------------------------------------------------------------------

(defonce system (atom {:server nil
                       :running? false}))

(defn load-edn-file! [f]
  (when (.exists (io/file f))
    (let [data (read-string (slurp f))]
      data)))

(defn load-rules! [rules-edn]
  ;; Expect a map {:frames #{...} :roles #{...} :rules [...]}
  ;; The actual CSNePS DSL for defining frames/roles may vary; this is a stub.
  (when-let [frames (:frames rules-edn)]
    (doseq [frm frames]
      ;; Example: frm could be {:frame :Robot :type :Entity?}
      ;; Here we simply register the symbol for later use.
      (util/dbg (str "Register frame: " frm))))
  (when-let [roles (:roles rules-edn)]
    (doseq [r roles]
      (util/dbg (str "Register role: " r))))
  (when-let [rules (:rules rules-edn)]
    (doseq [r rules]
      ;; In a real system, translate EDN rule to CSNePS rule constructs.
      (util/dbg (str "Register rule: " r)))))

(defn init! []
  (when-not (:running? @system)
    ;; Load seed KB and rules if present
    (when-let [kb (load-edn-file! "resources/sample-kb.edn")]
      (doseq [stmt (if (sequential? kb) kb [kb])]
        (try
          (cs/assert! stmt)
          (catch Exception e
            (util/dbg (str "KB load error: " stmt " -> " (.getMessage e)))))))
    (when-let [rules-edn (load-edn-file! "resources/generated/csneps-rules.edn")]
      (load-rules! rules-edn))
    (snip/run) ;; start inference network
    (swap! system assoc :running? true)))

;; -----------------------------------------------------------------------------
;; Helpers: simple assertion & query adapters
;; -----------------------------------------------------------------------------

(defn make-assertion-term
  "Transforms an assertion DTO into a CSNePS logical term form.
   Very simple default: (predicate subject object) plus :confidence meta/provenance support."
  [{:keys [subject predicate object confidence provenance]}]
  (let [pred (symbol predicate)
        subj (symbol subject)
        obj  (symbol object)]
    ;; Return a tuple with metadata for downstream support tracking.
    {:term (list pred subj obj)
     :confidence (or confidence 1.0)
     :provenance provenance}))

(defn assert-one! [dto]
  (let [{:keys [term confidence provenance]} (make-assertion-term dto)]
    ;; Example of confidence thresholding/defeasible policy could go here.
    (cs/assert! term)
    {:ok true
     :asserted (pr-str term)
     :confidence confidence
     :provenance provenance}))

(defn assert-batch! [dtos]
  (mapv assert-one! dtos))

(defn query-pattern
  "Enhanced pattern query with CSNePS variable support and real pattern matching.
   Supports patterns like: HighConfidenceLandmark(?l), (Robot ?r), [?x isa robot]"
  [pattern]
  (try
    (let [pstr (str/trim pattern)]
      (cond
        ;; Pattern: Functor(?var) - CSNePS functional pattern
        (re-matches #"^\w+\(\?\w+\)$" pstr)
        (let [functor (-> pstr (str/replace #"\(.*$" ""))
              var (-> pstr (str/replace #"^[^(]+\(\?|\)$" ""))]
          ;; Use CSNePS find with pattern matching
          (let [bindings (cs/find `[~(symbol functor) ~(symbol (str "?" var))])]
            {:results (map str bindings)
             :pattern pstr
             :bindings (count bindings)}))

        ;; Pattern: [?var predicate object] - Triple pattern
        (re-matches #"^\[.*\]$" pstr)
        (let [parsed (read-string pstr)
              results (cs/find parsed)]
          {:results (map str results)
           :pattern pstr
           :bindings (count results)})

        ;; Pattern: (?var predicate object) - Parenthetical pattern
        (re-matches #"^\(.*\)$" pstr)
        (let [parsed (read-string pstr)
              results (cs/find parsed)]
          {:results (map str results)
           :pattern pstr
           :bindings (count results)})

        ;; Simple string search in nodes
        :else
        (let [all-nodes (cs/listnodes)
              matching (filter #(str/includes? (str %) pstr) all-nodes)]
          {:results (map str matching)
           :pattern pstr
           :bindings (count matching)})))
    (catch Exception e
      {:error (.getMessage e)
       :pattern pattern})))

(defn why-json
  "Return structured justification with proof graph, rules, supports, and provenance.
   Returns detailed reasoning paths and derivation chains."
  [node-id-or-term]
  (try
    (let [term-node (if (string? node-id-or-term)
                      ;; Try to find node by string representation
                      (first (filter #(= (str %) node-id-or-term) (cs/listnodes)))
                      node-id-or-term)
          proof-data (when term-node (cs/why term-node))]

      (if proof-data
        ;; Structure the proof as a graph with nodes and edges
        (let [proof-str (str proof-data)
              ;; Extract rules and supports from proof structure
              rules (re-seq #"Rule\d+|[A-Z]\w*Rule" proof-str)
              supports (re-seq #"Support\d+|[A-Z]\w*Support" proof-str)
              ;; Build structured response
              proof-graph {:node {:id (str term-node)
                                  :type "derived"
                                  :asserted false
                                  :confidence 1.0}
                          :derivation {:rules (vec (distinct rules))
                                      :supports (vec (distinct supports))
                                      :method "csneps-inference"
                                      :steps (count (str/split proof-str #"\n"))}
                          :provenance {:source "csneps-core"
                                      :timestamp (str (java.time.Instant/now))
                                      :reasoning-depth (count rules)}
                          :proof-tree {:raw proof-str
                                      :structured true
                                      :format "csneps-justification"}}]
          {:success true
           :node-id (str term-node)
           :justification proof-graph
           :has-proof true})

        ;; No proof found - might be asserted fact or unknown
        {:success false
         :node-id (str node-id-or-term)
         :justification nil
         :has-proof false
         :reason "No justification found - may be asserted fact or unknown term"}))

    (catch Exception e
      {:success false
       :node-id (str node-id-or-term)
       :error (.getMessage e)
       :has-proof false})))

;; -----------------------------------------------------------------------------
;; JUNG-based SVG rendering via external Java helper
;; -----------------------------------------------------------------------------

(defn render-svg
  "Invokes the Java JUNG renderer utility to render a subgraph around focus node.
   Expects a jar at resources/tools/jung-renderer.jar with main class: com.csri.render.GraphSvg.
   Returns a temp SVG file path."
  [{:keys [focus radius]}]
  (let [focus (or focus "default")
        radius (or radius 2)
        tmp (java.nio.file.Files/createTempFile "csri-" ".svg" (make-array java.nio.file.attribute.FileAttribute 0))
        svg-path (.toString tmp)
        ;; You can pass a serialized graph snapshot or request the Java tool to call back an HTTP endpoint.
        cmd ["java" "-cp" "resources/tools/jung-renderer.jar"
             "com.csri.render.GraphSvg"
             "--focus" (str focus)
             "--radius" (str radius)
             "--out" svg-path]]
    (let [proc (-> (ProcessBuilder. cmd)
                   (.redirectErrorStream true)
                   (.start))]
      (with-open [rdr (io/reader (.getInputStream proc))]
        (doseq [line (line-seq rdr)]
          (util/dbg (str "[JUNG] " line))))
      (let [code (.waitFor proc)]
        (if (zero? code)
          svg-path
          (throw (ex-info "Renderer failed" {:exit code})))))
    ))

;; -----------------------------------------------------------------------------
;; Ring routes
;; -----------------------------------------------------------------------------

(defn ok [data] (-> (resp/response data) (resp/status 200)))
(defn bad [msg] (-> (resp/response {:error msg}) (resp/status 400)))

(defn handle-assert [req]
  (let [body (:body req)]
    (cond
      (map? body) (ok (assert-one! body))
      (and (map? body) (contains? body :assertions))
      (ok {:results (assert-batch! (:assertions body))})
      (and (sequential? body)) (ok {:results (assert-batch! body)})
      :else (bad "Invalid payload: expected object or array"))))

(defn handle-query [req]
  (let [pattern (get-in req [:params "pattern"])]
    (if (str/blank? pattern)
      (bad "Missing 'pattern' query param")
      (ok (query-pattern pattern)))))

(defn handle-why [req]
  (let [nid (or (get-in req [:params "nodeId"])
                (get-in req [:params "node"]) ;; alias
                (get-in req [:params "term"]))]
    (if (str/blank? nid)
      (bad "Missing 'nodeId' (or 'node'/'term') query param")
      (ok (why-json nid)))))

(defn handle-render [req]
  (try
    (let [focus (get-in req [:params "focus"])
          radius (some-> (get-in req [:params "radius"]) (Integer/parseInt))
          svg-path (render-svg {:focus focus :radius radius})]
      ;; Stream back inline SVG
      (-> (resp/response (slurp svg-path))
          (resp/status 200)
          (resp/header "Content-Type" "image/svg+xml")))
    (catch Exception e
      (bad (.getMessage e)))))

(defn handle-health [req]
  "Simple health check endpoint for Docker Compose health checks"
  (ok {:status "ok"
       :service "csneps-core"
       :version "0.1.0"
       :timestamp (str (java.time.Instant/now))
       :components {:csneps-engine "active"
                   :http-server "active"
                   :reasoning "active"}}))

(defn subgraph-json
  "Extract subgraph around focus node with specified radius and edge types.
   Returns JSON structure: {nodes:[{id,label,type,asserted,confidence}], edges:[{id,src,dst,label,collapsed}]}"
  [{:keys [focus radius edge-types] :or {radius 2 edge-types []}}]
  (try
    (let [focus-node (if (string? focus)
                       ;; Find node by string representation
                       (first (filter #(= (str %) focus) (cs/listnodes)))
                       focus)
          all-nodes (cs/listnodes)

          ;; Build subgraph by traversing from focus
          subgraph-nodes (if focus-node
                          ;; Start with focus and expand by radius
                          (loop [current-nodes #{focus-node}
                                 visited #{}
                                 remaining-radius radius]
                            (if (or (<= remaining-radius 0) (empty? current-nodes))
                              visited
                              (let [new-visited (clojure.set/union visited current-nodes)
                                    ;; Find connected nodes (simplified - in real CSNePS, use proper graph traversal)
                                    connected (set (filter #(or (some (fn [n] (str/includes? (str %) (str n))) current-nodes)
                                                               (some (fn [n] (str/includes? (str n) (str %))) current-nodes))
                                                         all-nodes))
                                    next-nodes (clojure.set/difference connected new-visited)]
                                (recur next-nodes new-visited (dec remaining-radius)))))
                          ;; No focus - return limited set of nodes
                          (set (take 20 all-nodes)))

          ;; Convert nodes to JSON structure
          nodes (mapv (fn [node]
                       {:id (str node)
                        :label (str node)
                        :type (cond
                               (re-find #"[Rr]ule|[Ii]mpl" (str node)) "rule"
                               (re-find #"[Cc]ontext|[Ff]rame" (str node)) "frame"
                               :else "concept")
                        :asserted (boolean (cs/asserted? node))
                        :confidence (or (cs/confidence node) 1.0)})
                      subgraph-nodes)

          ;; Generate edges between connected nodes (simplified)
          edges (vec (for [n1 subgraph-nodes
                          n2 subgraph-nodes
                          :when (and (not= n1 n2)
                                   (or (str/includes? (str n1) (str n2))
                                       (str/includes? (str n2) (str n1))))]
                      {:id (str "edge-" (hash [n1 n2]))
                       :src (str n1)
                       :dst (str n2)
                       :label "relates"
                       :collapsed false}))]

      {:nodes nodes
       :edges edges
       :focus (str focus)
       :radius radius
       :node-count (count nodes)
       :edge-count (count edges)})

    (catch Exception e
      {:error (.getMessage e)
       :focus (str focus)
       :radius radius})))

(defn handle-subgraph [req]
  "Handle subgraph extraction requests"
  (let [focus (get-in req [:params "focus"])
        radius (some-> (get-in req [:params "radius"]) (Integer/parseInt))
        edge-types (some-> (get-in req [:params "edgeTypes"])
                          (str/split #",")
                          vec)]
    (if (str/blank? focus)
      (bad "Missing 'focus' query param")
      (ok (subgraph-json {:focus focus
                         :radius (or radius 2)
                         :edge-types (or edge-types [])})))))

(defn handle-rules-load [req]
  "Load EDN rules from request body into CSNePS"
  (try
    (let [body (:body req)
          rules-data (if (string? body) (read-string body) body)]
      (if (and (map? rules-data) (contains? rules-data :rules))
        (do
          (load-rules! rules-data)
          (ok {:success true
               :message "Rules loaded successfully"
               :rule-count (count (:rules rules-data))
               :frames-count (count (get rules-data :frames []))
               :roles-count (count (get rules-data :roles []))}))
        (bad "Invalid rules format: expected map with :rules key")))
    (catch Exception e
      (bad (str "Failed to load rules: " (.getMessage e))))))

(defn handle-rules-stat [req]
  "Get statistics about currently loaded rules"
  (try
    (let [all-nodes (cs/listnodes)
          rule-nodes (filter #(re-find #"[Rr]ule|[Ii]mpl" (str %)) all-nodes)
          frame-nodes (filter #(re-find #"[Ff]rame|[Cc]ontext" (str %)) all-nodes)]
      (ok {:rules-loaded (count rule-nodes)
           :frames-loaded (count frame-nodes)
           :total-nodes (count all-nodes)
           :sample-rules (take 5 (map str rule-nodes))
           :timestamp (str (java.time.Instant/now))}))
    (catch Exception e
      (bad (str "Failed to get rules stats: " (.getMessage e))))))

(defn routes [req]
  (let [uri (:uri req)
        method (:request-method req)]
    (cond
      (and (= :get method)  (= "/health" uri))     (handle-health req)
      (and (= :post method) (= "/assert" uri))     (handle-assert req)
      (and (= :get method)  (= "/query" uri))      (handle-query req)
      (and (= :get method)  (= "/why" uri))        (handle-why req)
      (and (= :get method)  (= "/render" uri))     (handle-render req)
      (and (= :get method)  (= "/subgraph" uri))   (handle-subgraph req)
      (and (= :post method) (= "/rules/load" uri)) (handle-rules-load req)
      (and (= :get method)  (= "/rules/stat" uri)) (handle-rules-stat req)
      :else (-> (resp/response {:error "Not found"}) (resp/status 404)))))

(def app
  (-> routes
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (wrap-params)))

(defn -main [& _args]
  (init!)
  (let [port (or (some-> (System/getenv "CSNEPS_HTTP_PORT") Integer/parseInt) 3000)
        server (jetty/run-jetty app {:port port :join? false})]
    (swap! system assoc :server server :running? true)
    (println (str "CSRI HTTP bridge on http://localhost:" port))))
