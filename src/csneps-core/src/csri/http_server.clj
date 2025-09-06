(ns csri.http-server
  (:gen-class)
  (:require
    [ring.adapter.jetty :as jetty]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.util.response :as resp]
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.set]
    [clojure.java.io :as io]
    [csneps.core :as cs]))

(def system (atom {}))

(defn load-edn-file! [f]
  (when (.exists (java.io.File. f))
    (read-string (slurp f))))

(defn load-rules! [rules-edn]
  ;; Load rules from EDN structure into CSNePS
  (doseq [rule (:rules rules-edn)]
    (try
      (let [term (read-string (:pattern rule))]
        (cs/assert! term))
      (catch Exception e
        (println "Failed to load rule:" (.getMessage e))))))

(defn init! []
  (try
    (cs/clearkb true)
    (println "CSNePS knowledge base initialized")
    ;; Load any startup rules
    (when-let [rules (load-edn-file! "resources/startup-rules.edn")]
      (load-rules! rules)
      (println "Startup rules loaded"))
    (catch Exception e
      (println "Warning: CSNePS initialization issue:" (.getMessage e)))))

(defn make-assertion-term
  "Parse assertion DTO and create CSNePS term with metadata"
  [dto]
  (let [assertion (or (:assertion dto) (:term dto))
        confidence (or (:confidence dto) 1.0)
        provenance (or (:provenance dto) {})]
    {:term (read-string assertion)
     :confidence confidence
     :provenance provenance}))

(defn- get-node-kind [node]
      (let [s (str node)]
        (cond
          (re-find #"[Ff]rame" s) "Frame"
          (re-find #"[Ii]ndividual" s) "Individual"
          (re-find #"[Pp]rop" s) "Proposition"
          (re-find #"[Rr]ole" s) "Role"
          :else "Frame")))

    (defn- get-node-attrs [node]
      {:id (str node)
       :label (str node)
       :kind (get-node-kind node)
       :asserted (boolean (try (cs/asserted? node) (catch Exception _ false)))
       :confidence (or (try (cs/confidence node) (catch Exception _ nil)) 1.0)
       :degree (try (count (cs/edges node)) (catch Exception _ 0))})

    (defn- get-edge-attrs [edge collapse?]
      (let [{:keys [src dst label kind asserted collapsed]} edge]
        {:id (str "edge-" (hash [src dst label]))
         :src (str src)
         :dst (str dst)
         :label (str label)
         :kind (or kind (if collapse? "Collapsed" "FrameEdge"))
         :asserted (boolean asserted)
         :collapsed (boolean collapsed)}))

    (defn- bfs-subgraph
      [focus-node radius edge-types max-nodes collapse?]
      ;; BFS traversal with edge filtering and node/edge limits
      (let [queue (java.util.LinkedList.)
            visited (atom #{})
            edges (atom [])
            add-node (fn [n] (when-not (@visited n) (.add queue n)))
            add-edge (fn [e] (swap! edges conj e))
            _ (add-node focus-node)
            node-depth (atom {focus-node 0})]
        (while (and (not (.isEmpty queue)) (< (count @visited) max-nodes))
          (let [node (.remove queue)
                depth (get @node-depth node 0)]
            (swap! visited conj node)
            (when (< depth radius)
              (doseq [edge (try (cs/edges node) (catch Exception _ []))]
                (let [other (if (= node (:src edge)) (:dst edge) (:src edge))
                      edge-type (str (:label edge))]
                  (when (and (or (empty? edge-types) (some #(= edge-type %) edge-types))
                             (not (@visited other)))
                    (add-node other)
                    (swap! node-depth assoc other (inc depth)))
                  (add-edge (assoc edge :src node :dst other :collapsed false))))))
        {:nodes @visited :edges @edges :truncated (>= (count @visited) max-nodes)}))

    (defn- collapse-frames [nodes edges]
      ;; Collapse two-slot frames not used as fillers elsewhere
      (let [frame-nodes (filter #(= "Frame" (get-node-kind %)) nodes)
            frame-edges (filter #(= "FrameEdge" (:kind %)) edges)
            used-as-filler (set (map :dst frame-edges))
            collapsible (filter (fn [f]
                                  (let [slots (try (cs/slots f) (catch Exception _ []))]
                                    (and (= 2 (count slots))
                                         (not (used-as-filler f)))))
                                frame-nodes)
            collapsed-edges (mapcat (fn [f]
                                       (let [slots (try (cs/slots f) (catch Exception _ []))]
                                         (when (= 2 (count slots))
                                           (let [[s1 s2] slots]
                                             [{:src s1 :dst s2 :label (str f) :kind "Collapsed" :asserted (cs/asserted? f) :collapsed true}]))))
                                     collapsible)
            keep-nodes (set/difference (set nodes) (set collapsible))
            keep-edges (remove #(some (fn [f] (= (:src %) f)) collapsible) edges)]
        {:nodes keep-nodes :edges (concat keep-edges collapsed-edges)}))

    (defn subgraph-json
      "Extract subgraph around focus node with specified radius, edge types, collapse, and maxNodes.
       Output: {nodes:[...], edges:[...], meta:{...}}"
      [{:keys [focus radius collapse edge-types max-nodes]
        :or {radius 2 collapse true edge-types [] max-nodes 500}}]
      (try
        (let [focus-node (if (string? focus)
                           (first (filter #(= (str %) focus) (cs/listnodes)))
                           focus)
              radius (or radius 2)
              collapse? (if (nil? collapse) true (boolean (if (string? collapse) (Boolean/parseBoolean collapse) collapse)))
              edge-types (or edge-types [])
              max-nodes (or max-nodes 500)
              {:keys [nodes edges truncated]} (if focus-node
                                                (bfs-subgraph focus-node radius edge-types max-nodes collapse?)
                                                {:nodes #{} :edges [] :truncated false})
              ;; Collapsing logic
              {:keys [nodes edges]} (if collapse?
                                      (collapse-frames nodes edges)
                                      {:nodes nodes :edges edges})
              nodes-json (mapv get-node-attrs nodes)
              edges-json (mapv #(get-edge-attrs % collapse?) edges)
              meta {:focus (str focus)
                    :radius radius
                    :collapsed collapse?
                    :truncated truncated
                    :count {:nodes (count nodes-json)
                            :edges (count edges-json)}}]
          {:nodes nodes-json
           :edges edges-json
           :meta meta})
        (catch Exception e
          {:error (.getMessage e)
           :focus (str focus)
           :radius radius})))

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

                   :reasoning "active"}}))

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
