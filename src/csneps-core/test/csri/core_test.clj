(ns csri.core-test
  "Unit tests for CSNePS Robotics Inference Core v0.1.0"
  (:require [clojure.test :refer [deftest testing is are use-fixtures]]
            [clojure.data.json :as json]
            [csri.core :as core]))

;; Test fixtures

(defn reset-kb-fixture
  "Reset the knowledge base before each test"
  [f]
  (reset! @#'core/server-state {:kb nil :server nil :running false})
  (f))

(use-fixtures :each reset-kb-fixture)

;; Knowledge Base Tests

(deftest test-init-kb
  (testing "Knowledge base initialization"
    (is (true? (core/init-kb)) "KB should initialize successfully")

    (let [kb (:kb @@#'core/server-state)]
      (is (some? kb) "KB should be created")
      (is (set? (:facts kb)) "Facts should be a set")
      (is (map? (:rules kb)) "Rules should be a map")
      (is (contains? (:types kb) :Landmark) "Should contain Landmark type")
      (is (contains? (:types kb) :GNCEvent) "Should contain GNCEvent type")
      (is (contains? (:types kb) :MedicalFinding) "Should contain MedicalFinding type"))))

(deftest test-rule-loading
  (testing "Rule loading functions"
    (core/init-kb)

    (let [rules (:rules (:kb @@#'core/server-state))]
      (is (contains? rules :slam-rule-1) "Should contain SLAM rule 1")
      (is (contains? rules :slam-rule-2) "Should contain SLAM rule 2")
      (is (contains? rules :slam-rule-3) "Should contain SLAM rule 3")
      (is (contains? rules :gnc-rule-1) "Should contain GNC rule 1")
      (is (contains? rules :gnc-rule-2) "Should contain GNC rule 2")
      (is (contains? rules :gnc-rule-3) "Should contain GNC rule 3")
      (is (contains? rules :medical-rule-1) "Should contain Medical rule 1")
      (is (contains? rules :medical-rule-2) "Should contain Medical rule 2")
      (is (contains? rules :medical-rule-3) "Should contain Medical rule 3"))))

;; SLAM Domain Tests

(deftest test-process-loop-closure
  (testing "Loop closure observation processing"
    (core/init-kb)

    (let [observation {:landmark_id "L001"
                       :score 0.85
                       :method "visual"
                       :pose_estimate [1.0 2.0 3.0 0.0 0.0 0.0 1.0]
                       :timestamp "2024-01-01T12:00:00Z"}
          response (core/process-loop-closure observation)]

      (is (:success response) "Processing should succeed")
      (is (string? (:observation_id response)) "Should return observation ID")
      (is (vector? (:triggered_rules response)) "Should return triggered rules")
      (is (not-empty (:message response)) "Should return message")

      ;; Check that facts were added to KB
      (let [facts (:facts (:kb @@#'core/server-state))]
        (is (some #(= (first %) :LoopClosure) facts) "Should add LoopClosure fact")
        (is (some #(and (= (first %) :landmark-id)
                        (= (nth % 2) "L001")) facts) "Should add landmark-id fact")
        (is (some #(and (= (first %) :score)
                        (= (nth % 2) 0.85)) facts) "Should add score fact")))))

(deftest test-process-appearance-match
  (testing "Appearance match observation processing"
    (core/init-kb)

    (let [observation {:landmark_id "L001"
                       :consistency "consistent"
                       :score 0.78
                       :timestamp "2024-01-01T12:00:00Z"}
          response (core/process-appearance-match observation)]

      (is (:success response) "Processing should succeed")
      (is (string? (:observation_id response)) "Should return observation ID")
      (is (vector? (:triggered_rules response)) "Should return triggered rules")

      ;; Check that facts were added to KB
      (let [facts (:facts (:kb @@#'core/server-state))]
        (is (some #(= (first %) :AppearanceMatch) facts) "Should add AppearanceMatch fact")
        (is (some #(and (= (first %) :consistency)
                        (= (nth % 2) "consistent")) facts) "Should add consistency fact")))))

;; GNC Domain Tests

(deftest test-process-gnc-event
  (testing "GNC event observation processing"
    (core/init-kb)

    (let [observation {:event_type "thruster_anomaly"
                       :mode "burn"
                       :severity 0.65
                       :parameters {"thrust_deviation" 0.15}
                       :timestamp "2024-01-01T12:00:00Z"}
          response (core/process-gnc-event observation)]

      (is (:success response) "Processing should succeed")
      (is (string? (:observation_id response)) "Should return observation ID")
      (is (vector? (:triggered_rules response)) "Should return triggered rules")

      ;; Check that facts were added to KB
      (let [facts (:facts (:kb @@#'core/server-state))]
        (is (some #(= (first %) :GNCEvent) facts) "Should add GNCEvent fact")
        (is (some #(and (= (first %) :event-type)
                        (= (nth % 2) "thruster_anomaly")) facts) "Should add event-type fact")
        (is (some #(and (= (first %) :mode)
                        (= (nth % 2) "burn")) facts) "Should add mode fact")
        (is (some #(and (= (first %) :severity)
                        (= (nth % 2) 0.65)) facts) "Should add severity fact")))))

;; Medical Domain Tests

(deftest test-process-medical-finding
  (testing "Medical finding observation processing"
    (core/init-kb)

    (let [observation {:patient_id "P001"
                       :finding_type "lesion"
                       :confidence 0.89
                       :location "liver_segment_4"
                       :size_mm 12.5
                       :modality "CT"
                       :timestamp "2024-01-01T12:00:00Z"}
          response (core/process-medical-finding observation)]

      (is (:success response) "Processing should succeed")
      (is (string? (:observation_id response)) "Should return observation ID")
      (is (vector? (:triggered_rules response)) "Should return triggered rules")

      ;; Check that facts were added to KB
      (let [facts (:facts (:kb @@#'core/server-state))]
        (is (some #(= (first %) :MedicalFinding) facts) "Should add MedicalFinding fact")
        (is (some #(and (= (first %) :patient-id)
                        (= (nth % 2) "P001")) facts) "Should add patient-id fact")
        (is (some #(and (= (first %) :finding-type)
                        (= (nth % 2) "lesion")) facts) "Should add finding-type fact")
        (is (some #(and (= (first %) :confidence)
                        (= (nth % 2) 0.89)) facts) "Should add confidence fact")
        (is (some #(and (= (first %) :size-mm)
                        (= (nth % 2) 12.5)) facts) "Should add size-mm fact")))))

;; Query Tests

(deftest test-query-beliefs
  (testing "Belief query functionality"
    (core/init-kb)

    (testing "HighConfidenceLandmark query"
      (let [query {:concept "HighConfidenceLandmark"
                   :limit 5
                   :include_justification false}
            response (core/query-beliefs query)]

        (is (:success response) "Query should succeed")
        (is (vector? (:beliefs response)) "Should return beliefs vector")
        (is (string? (:message response)) "Should return message")))

    (testing "Hypothesis query"
      (let [query {:concept "Hypothesis"
                   :limit 5
                   :include_justification false}
            response (core/query-beliefs query)]

        (is (:success response) "Query should succeed")
        (is (vector? (:beliefs response)) "Should return beliefs vector")))

    (testing "Recommendation query"
      (let [query {:concept "Recommendation"
                   :limit 5
                   :include_justification false}
            response (core/query-beliefs query)]

        (is (:success response) "Query should succeed")
        (is (vector? (:beliefs response)) "Should return beliefs vector")))

    (testing "Unknown concept query"
      (let [query {:concept "UnknownConcept"
                   :limit 5
                   :include_justification false}
            response (core/query-beliefs query)]

        (is (:success response) "Query should succeed")
        (is (empty? (:beliefs response)) "Should return empty beliefs for unknown concept")))))

(deftest test-get-justification
  (testing "Justification query functionality"
    (core/init-kb)

    (let [query {:belief_id "test-belief-1"
                 :max_depth 5}
          response (core/get-justification query)]

      (is (:success response) "Justification query should succeed")
      (is (string? (:justification_tree response)) "Should return justification tree as string")
      (is (string? (:message response)) "Should return message")

      ;; Parse and validate justification JSON
      (let [justification (json/read-str (:justification_tree response) :key-fn keyword)]
        (is (= (:belief_id justification) "test-belief-1") "Should contain correct belief ID")
        (is (vector? (:rule_path justification)) "Should contain rule path")
        (is (vector? (:premises justification)) "Should contain premises")
        (is (number? (:confidence justification)) "Should contain confidence")))))

;; Inference Tests

(deftest test-run-inference
  (testing "Simplified inference engine"
    (core/init-kb)

    ;; Add some facts first
    (core/process-loop-closure {:landmark_id "L001" :score 0.85 :method "visual" :pose_estimate []})

    (let [triggered-rules (core/run-inference "test-obs-id")]
      (is (vector? triggered-rules) "Should return vector of triggered rules")
      (is (every? string? triggered-rules) "All triggered rules should be strings"))))

(deftest test-rule-matches
  (testing "Simplified rule matching"
    (core/init-kb)

    (let [rule-def {:type :implication
                    :condition [:and [:LoopClosure :?lc]]
                    :conclusion [:HighConfidenceLandmark :?landmark]}
          facts #{[:LoopClosure "obs1"] [:landmark-id "obs1" "L001"]}]

      ;; This is a very basic test since rule-matches? is simplified
      (is (boolean? (core/rule-matches? rule-def facts "obs1"))
          "Should return boolean result"))))

;; Server Management Tests

(deftest test-server-lifecycle
  (testing "gRPC server start/stop"
    (let [port 50052]  ; Use different port for testing

      (testing "Server start"
        (let [server (core/start-grpc-server port)]
          (is (some? server) "Server should start successfully")
          (is (:running @@#'core/server-state) "Server state should be running")

          (core/stop-grpc-server)))

      (testing "Server stop"
        (core/stop-grpc-server)
        (is (not (:running @@#'core/server-state)) "Server state should not be running")))))

(deftest test-system-status
  (testing "System status reporting"
    (core/init-kb)

    (let [status (core/get-system-status)]
      (is (map? status) "Status should be a map")
      (is (contains? status :running) "Should contain running status")
      (is (contains? status :kb_initialized) "Should contain KB status")
      (is (contains? status :rules_loaded) "Should contain rules count")
      (is (contains? status :facts_stored) "Should contain facts count")
      (is (= (:version status) "v0.1.0") "Should show correct version")

      (is (boolean? (:running status)) "Running should be boolean")
      (is (boolean? (:kb_initialized status)) "KB initialized should be boolean")
      (is (number? (:rules_loaded status)) "Rules loaded should be number")
      (is (number? (:facts_stored status)) "Facts stored should be number"))))

;; Error Handling Tests

(deftest test-error-handling
  (testing "Error handling in observation processing"
    ;; Don't initialize KB to trigger errors

    (testing "Loop closure with uninitialized KB"
      (let [observation {:landmark_id "L001" :score 0.85 :method "visual" :pose_estimate []}
            response (core/process-loop-closure observation)]

        ;; Should handle error gracefully
        (is (map? response) "Should return response map even on error")
        (is (contains? response :success) "Should contain success field")
        (is (contains? response :message) "Should contain message field")))

    (testing "Query with uninitialized KB"
      (let [query {:concept "HighConfidenceLandmark" :limit 5}
            response (core/query-beliefs query)]

        ;; Should handle error gracefully
        (is (map? response) "Should return response map even on error")
        (is (contains? response :success) "Should contain success field")
        (is (contains? response :beliefs) "Should contain beliefs field")))))

;; Integration Tests

(deftest test-canonical-rule-paths
  (testing "End-to-end canonical rule paths"
    (core/init-kb)

    (testing "SLAM path: LoopClosure → HighConfidenceLandmark"
      ;; Send loop closure
      (let [lc-response (core/process-loop-closure
                          {:landmark_id "L001" :score 0.85 :method "visual" :pose_estimate []})]
        (is (:success lc-response) "Loop closure should be processed"))

      ;; Send appearance match
      (let [am-response (core/process-appearance-match
                          {:landmark_id "L001" :consistency "consistent" :score 0.78})]
        (is (:success am-response) "Appearance match should be processed"))

      ;; Query landmarks
      (let [query-response (core/query-beliefs {:concept "HighConfidenceLandmark" :limit 5})]
        (is (:success query-response) "Landmark query should succeed")))

    (testing "GNC path: ThrusterAnomaly → Hypothesis"
      ;; Send GNC event
      (let [gnc-response (core/process-gnc-event
                           {:event_type "thruster_anomaly" :mode "burn" :severity 0.65 :parameters {}})]
        (is (:success gnc-response) "GNC event should be processed"))

      ;; Query hypotheses
      (let [query-response (core/query-beliefs {:concept "Hypothesis" :limit 5})]
        (is (:success query-response) "Hypothesis query should succeed")))

    (testing "Medical path: Finding → Recommendation"
      ;; Send medical finding
      (let [med-response (core/process-medical-finding
                           {:patient_id "P001" :finding_type "lesion" :confidence 0.89
                            :location "liver" :size_mm 12.5 :modality "CT"})]
        (is (:success med-response) "Medical finding should be processed"))

      ;; Query recommendations
      (let [query-response (core/query-beliefs {:concept "Recommendation" :limit 5})]
        (is (:success query-response) "Recommendation query should succeed")))))
