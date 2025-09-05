(ns csri.kb.rules
  "Inference rules and rule management for CSNePS robotics inference."
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [csri.kb.ontology :as ontology]
            [csri.kb.assertions :as assertions]))

(def ^:dynamic *rules* (atom {:rules {}
                              :rule-order []
                              :active-rules #{}
                              :inference-stats {}}))

(defn load-rules!
  "Load inference rules from configuration."
  [rules-config]
  (log/info "Loading" (count rules-config) "inference rules")
  (doseq [rule rules-config]
    (add-rule! rule))
  (log/info "Successfully loaded rules"))

(defn add-rule!
  "Add a new inference rule to the system."
  [rule-spec]
  (let [rule-name (:name rule-spec)
        rule-data (assoc rule-spec :added-at (System/currentTimeMillis))]
    (swap! *rules* update :rules assoc rule-name rule-data)
    (swap! *rules* update :rule-order conj rule-name)
    (swap! *rules* update :active-rules conj rule-name)
    (log/debug "Added rule:" rule-name)))

(defn remove-rule!
  "Remove a rule from the system."
  [rule-name]
  (swap! *rules* update :rules dissoc rule-name)
  (swap! *rules* update :rule-order #(remove #{rule-name} %))
  (swap! *rules* update :active-rules disj rule-name)
  (log/debug "Removed rule:" rule-name))

(defn get-rule
  "Retrieve a rule by name."
  [rule-name]
  (get-in @*rules* [:rules rule-name]))

(defn activate-rule!
  "Activate a rule for inference."
  [rule-name]
  (when (get-rule rule-name)
    (swap! *rules* update :active-rules conj rule-name)
    (log/debug "Activated rule:" rule-name)))

(defn deactivate-rule!
  "Deactivate a rule from inference."
  [rule-name]
  (swap! *rules* update :active-rules disj rule-name)
  (log/debug "Deactivated rule:" rule-name))

(defn rule-active?
  "Check if a rule is currently active."
  [rule-name]
  (contains? (:active-rules @*rules*) rule-name))

;; Core inference rules for robotics applications

(def landmark-promotion-rule
  {:name :promote-landmark
   :description "Promote landmark to high confidence based on multiple evidence"
   :domain :slam
   :priority 10
   :if [:and
        [:LoopClosure ?landmark ?score1 ?method]
        [:AppearanceMatch ?landmark :consistent ?score2]
        [:> ?score1 0.7]
        [:> ?score2 0.7]]
   :then [:HighConfidenceLandmark ?landmark]
   :justification "Strong loop closure and appearance consistency"})

(def map-inconsistency-rule
  {:name :detect-map-inconsistency
   :description "Detect potential map inconsistency from conflicting observations"
   :domain :slam
   :priority 8
   :if [:and
        [:Landmark ?l1 ?pos1 ?conf1]
        [:Landmark ?l2 ?pos2 ?conf2]
        [:spatial-conflict ?pos1 ?pos2]
        [:> ?conf1 0.5]
        [:> ?conf2 0.5]]
   :then [:Hypothesis :map-inconsistency [:landmarks ?l1 ?l2]]
   :justification "Conflicting landmark positions suggest map error"})

(def gnc-fault-detection-rule
  {:name :detect-thruster-fault
   :description "Detect potential thruster fault from anomalous behavior"
   :domain :gnc
   :priority 9
   :if [:and
        [:GNCEvent :thruster-anomaly ?timestamp ?params]
        [:Mode :burn ?active]
        [:= ?active true]]
   :then [:Hypothesis :propellant-cavitation ?params]
   :justification "Thruster anomaly during burn suggests cavitation"})

(def medical-recommendation-rule
  {:name :contrast-mri-recommendation
   :description "Recommend contrast MRI for high-risk lesions"
   :domain :medical
   :priority 7
   :if [:and
        [:Finding :lesion ?location ?size ?confidence]
        [:Patient ?patient :high-risk ?history]
        [:> ?size 10.0]
        [:> ?confidence 0.8]]
   :then [:Recommendation :contrast-mri [:patient ?patient] [:timeframe "3 months"]]
   :justification "Large lesion in high-risk patient requires follow-up"})

(def cv-object-validation-rule
  {:name :validate-object-detection
   :description "Validate object detection with contextual evidence"
   :domain :computer-vision
   :priority 6
   :if [:and
        [:VisionDetection :person ?confidence ?bbox]
        [:Context :indoor ?scene-type]
        [:> ?confidence 0.85]]
   :then [:ValidatedDetection :person ?bbox]
   :justification "High-confidence person detection in appropriate context"})

(defn evaluate-condition
  "Evaluate a rule condition against current knowledge base."
  [condition bindings]
  (cond
    (vector? condition)
    (let [[op & args] condition]
      (case op
        :and (every? #(evaluate-condition % bindings) args)
        :or (some #(evaluate-condition % bindings) args)
        :not (not (evaluate-condition (first args) bindings))
        :> (let [[a b] args
                 val-a (get bindings a a)
                 val-b (get bindings b b)]
             (and (number? val-a) (number? val-b) (> val-a val-b)))
        :< (let [[a b] args
                 val-a (get bindings a a)
                 val-b (get bindings b b)]
             (and (number? val-a) (number? val-b) (< val-a val-b)))
        := (let [[a b] args
                 val-a (get bindings a a)
                 val-b (get bindings b b)]
             (= val-a val-b))
        ;; Default: try to match against knowledge base
        (assertions/query-fact condition bindings)))
    
    :else
    ;; Simple fact query
    (assertions/query-fact condition bindings)))

(defn find-bindings
  "Find variable bindings that satisfy rule conditions."
  [conditions]
  (let [variables (extract-variables conditions)]
    ;; Simplified binding search - in real implementation would use
    ;; more sophisticated pattern matching and unification
    (assertions/find-matching-bindings conditions variables)))

(defn extract-variables
  "Extract variable symbols from rule conditions."
  [conditions]
  (letfn [(extract [form]
            (cond
              (symbol? form) (when (.startsWith (name form) "?") #{form})
              (vector? form) (apply clojure.set/union (map extract form))
              (seq? form) (apply clojure.set/union (map extract form))
              :else #{}))]
    (extract conditions)))

(defn apply-rule
  "Apply a rule and generate new assertions."
  [rule bindings]
  (let [conclusion (:then rule)
        instantiated-conclusion (substitute-bindings conclusion bindings)
        justification {:rule (:name rule)
                       :bindings bindings
                       :timestamp (System/currentTimeMillis)
                       :reason (:justification rule)}]
    (log/debug "Applying rule" (:name rule) "with bindings" bindings)
    (assertions/assert-fact! instantiated-conclusion justification)
    (record-inference! (:name rule) bindings instantiated-conclusion)))

(defn substitute-bindings
  "Substitute variable bindings in a conclusion."
  [conclusion bindings]
  (clojure.walk/postwalk
    (fn [form]
      (if (and (symbol? form) (.startsWith (name form) "?"))
        (get bindings form form)
        form))
    conclusion))

(defn trigger-inference!
  "Trigger inference rules based on new observation."
  [observation]
  (let [active-rules (get-active-rules)]
    (doseq [rule active-rules]
      (try
        (let [conditions (:if rule)
              bindings-set (find-bindings conditions)]
          (doseq [bindings bindings-set]
            (when (evaluate-condition conditions bindings)
              (apply-rule rule bindings))))
        (catch Exception e
          (log/error e "Error applying rule" (:name rule)))))))

(defn get-active-rules
  "Get all currently active rules."
  []
  (let [rules-state @*rules*
        active-names (:active-rules rules-state)]
    (vals (select-keys (:rules rules-state) active-names))))

(defn record-inference!
  "Record an inference for statistics and explanation."
  [rule-name bindings conclusion]
  (let [inference-record {:rule rule-name
                          :bindings bindings
                          :conclusion conclusion
                          :timestamp (System/currentTimeMillis)}]
    (swap! *rules* update-in [:inference-stats :recent] 
           #(take 100 (cons inference-record %)))
    (swap! *rules* update-in [:inference-stats :total-count] (fnil inc 0))))

(defn rule-count
  "Get the total number of active rules."
  []
  (count (:active-rules @*rules*)))

(defn recent-inference-count
  "Get the count of recent inferences."
  []
  (count (get-in @*rules* [:inference-stats :recent] [])))

(defn get-rule-statistics
  "Get statistics about rule execution."
  []
  (let [stats (:inference-stats @*rules*)]
    {:total-inferences (:total-count stats 0)
     :recent-inferences (count (:recent stats []))
     :active-rules (count (:active-rules @*rules*))
     :total-rules (count (:rules @*rules*))}))

(defn explain-inference
  "Provide explanation for how a conclusion was reached."
  [conclusion]
  (let [recent-inferences (get-in @*rules* [:inference-stats :recent] [])]
    (filter #(= (:conclusion %) conclusion) recent-inferences)))

;; Initialize with core rules
(defn initialize-core-rules!
  "Initialize the system with core inference rules."
  []
  (doseq [rule [landmark-promotion-rule
                map-inconsistency-rule
                gnc-fault-detection-rule
                medical-recommendation-rule
                cv-object-validation-rule]]
    (add-rule! rule))
  (log/info "Initialized with core inference rules"))
