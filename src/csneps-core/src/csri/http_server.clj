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
  "Very minimal pattern query. If given a simple functor like HighConfidenceLandmark(?l),
   attempt to call cs/find or analogous. Here, we stub by returning matches of a functor symbol."
  [pattern]
  (try
    (let [pstr (str/trim pattern)
          ;; crude parse: Functor(Arg) -> extract Functor
          functor (-> pstr (str/replace #"\(.*$" "") symbol)]
      ;; CSNePS does not expose a trivial 'find by functor' in this stub;
      ;; you can replace this with actual CSNePS pattern matching calls.
      ;; Returning a placeholder vector to demonstrate the API.
      {:results [(str functor "/example1")
                 (str functor "/example2")]}
      )
    (catch Exception e
      {:error (.getMessage e)})))

(defn why-json
  "Return justification for a term ID or a printed term."
  [node-id-or-term]
  (try
    ;; For demo, if node-id-or-term looks like a term, we could call (cs/why term)
    ;; Otherwise, if an ID scheme is adopted, map it to a term first.
    (let [proof (cs/why node-id-or-term)]
      {:node node-id-or-term
       :justification (str proof)})
    (catch Exception e
      {:error (.getMessage e)})))

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

(defn routes [req]
  (let [uri (:uri req)
        method (:request-method req)]
    (cond
      (and (= :post method) (= "/assert" uri)) (handle-assert req)
      (and (= :get method)  (= "/query" uri))  (handle-query req)
      (and (= :get method)  (= "/why" uri))    (handle-why req)
      (and (= :get method)  (= "/render" uri)) (handle-render req)
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
