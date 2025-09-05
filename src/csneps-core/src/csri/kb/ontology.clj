(ns csri.kb.ontology
  "Core ontology definitions for CSNePS robotics inference."
  (:require [clojure.tools.logging :as log]
            [clojure.set :as set]))

(def ^:dynamic *ontology* (atom {:entities #{}
                                 :relations #{}
                                 :frames {}
                                 :roles {}
                                 :hierarchies {}}))

(defn initialize-ontology!
  "Initialize the core ontology with entities and relations."
  [entities relations]
  (log/info "Initializing ontology with" (count entities) "entities and" (count relations) "relations")
  (swap! *ontology* assoc 
         :entities (set entities)
         :relations (set relations))
  (define-core-frames!)
  (define-core-hierarchies!))

(defn define-core-frames!
  "Define the standard frames for robotics inference."
  []
  (let [frames {:Entity {:roles [:id :type :properties]}
                :Place {:roles [:id :coordinates :uncertainty :reference-frame]}
                :Landmark {:roles [:id :type :confidence :location :features]}
                :Sensor {:roles [:id :type :calibration :noise-model]}
                :Observation {:roles [:sensor :timestamp :data :confidence :source]}
                :Hypothesis {:roles [:id :type :evidence :confidence :justification]}
                :Fault {:roles [:id :type :severity :timestamp :affected-systems]}
                :Mode {:roles [:id :type :constraints :transitions :active]}
                :Patient {:roles [:id :demographics :history :risk-factors]}
                :Finding {:roles [:id :type :location :size :confidence :modality]}
                :LoopClosure {:roles [:landmark-id :score :method :pose-estimate]}
                :AppearanceMatch {:roles [:landmark-id :consistency :score :features]}
                :HighConfidenceLandmark {:roles [:landmark-id :evidence :timestamp]}
                :GNCEvent {:roles [:event-type :timestamp :parameters :severity]}
                :VisionDetection {:roles [:object-class :confidence :bounding-box :features]}}]
    (swap! *ontology* assoc :frames frames)
    (log/debug "Defined" (count frames) "core frames")))

(defn define-core-hierarchies!
  "Define subsumption hierarchies for domain concepts."
  []
  (let [hierarchies {:spatial-entity [:place :landmark :coordinate]
                     :temporal-entity [:event :observation :measurement]
                     :epistemic-entity [:hypothesis :belief :knowledge]
                     :diagnostic-entity [:finding :symptom :diagnosis]
                     :system-entity [:sensor :mode :fault]
                     :landmark-types [:visual-landmark :geometric-landmark :semantic-landmark]
                     :observation-types [:visual-observation :inertial-observation :gnc-observation]
                     :confidence-levels [:high-confidence :medium-confidence :low-confidence]}]
    (swap! *ontology* assoc :hierarchies hierarchies)
    (log/debug "Defined" (count hierarchies) "hierarchies")))

(defn define-frame
  "Define a new frame with specified roles."
  [frame-name roles]
  (swap! *ontology* assoc-in [:frames frame-name] {:roles roles})
  (log/debug "Defined frame:" frame-name "with roles:" roles))

(defn get-frame
  "Retrieve frame definition by name."
  [frame-name]
  (get-in @*ontology* [:frames frame-name]))

(defn frame-exists?
  "Check if a frame is defined in the ontology."
  [frame-name]
  (contains? (:frames @*ontology*) frame-name))

(defn get-frame-roles
  "Get the roles defined for a specific frame."
  [frame-name]
  (:roles (get-frame frame-name)))

(defn entity-exists?
  "Check if an entity type is defined."
  [entity-type]
  (contains? (:entities @*ontology*) entity-type))

(defn relation-exists?
  "Check if a relation is defined."
  [relation]
  (contains? (:relations @*ontology*) relation))

(defn is-subtype?
  "Check if type-a is a subtype of type-b according to hierarchies."
  [type-a type-b]
  (let [hierarchies (:hierarchies @*ontology*)]
    (some (fn [[parent children]]
            (and (= parent type-b)
                 (some #{type-a} children)))
          hierarchies)))

(defn get-supertypes
  "Get all supertypes of a given type."
  [entity-type]
  (let [hierarchies (:hierarchies @*ontology*)]
    (set (for [[parent children] hierarchies
               :when (some #{entity-type} children)]
           parent))))

(defn get-subtypes
  "Get all subtypes of a given type."
  [entity-type]
  (get-in @*ontology* [:hierarchies entity-type] #{}))

(defn validate-frame-instance
  "Validate that a frame instance has all required roles."
  [frame-name instance]
  (let [required-roles (get-frame-roles frame-name)
        instance-roles (set (keys instance))]
    (when-not (set/subset? (set required-roles) instance-roles)
      (let [missing (set/difference (set required-roles) instance-roles)]
        (throw (ex-info "Frame instance missing required roles"
                        {:frame frame-name
                         :missing-roles missing
                         :instance instance}))))))

(defn ontology-stats
  "Get statistics about the current ontology."
  []
  (let [ont @*ontology*]
    {:entities (count (:entities ont))
     :relations (count (:relations ont))
     :frames (count (:frames ont))
     :hierarchies (count (:hierarchies ont))}))
