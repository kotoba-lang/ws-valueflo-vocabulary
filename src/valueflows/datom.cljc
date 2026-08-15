(ns valueflows.datom
  "Project Valueflows records onto this workspace's datom plane.

   The plane is the one `manifest/edn-query.cljs` reads (ADR-2607171600 /
   ADR-2607252000): entity maps tagged with `:source/dataset`, queried with
   DataScript, joined across datasets by shared keys. Valueflows records enter
   it as `:source/dataset \"valueflows\"`.

   Two join keys are asserted, both because they already exist on the plane:

     :company/lei  an Agent that is a legal entity -> market-intel financials,
                   cloud-itonami-lei blueprints, cloud-itonami-lei-tos
     :repo/path    the repository whose actor recorded the flow ->
                   repo-taxonomy, repo-maturity

   No other join is claimed. An agent with no LEI simply has no LEI here; it
   is not given a synthesised one (a fabricated key joins to the wrong row,
   which is worse than not joining).

   ONE REF, ON PURPOSE: kotobase reaches exactly one ref for a Datalog join
   (ADR-260726-kotobase-query-plane-is-one-ref), so events, resources,
   processes and agents are projected into ONE dataset. Splitting them by
   volume would make 'who produced the resource this event consumed'
   unanswerable — the exact question the plane exists for."
  (:require [valueflows.vocabulary :as vocab]))

(def dataset "valueflows")

(defn- base [i] {:db/id (- (inc i)) :source/dataset dataset})

(defn- measure->attrs [prefix m]
  (when (map? m)
    (cond-> {}
      (some? (:has-numerical-value m))
      (assoc (keyword (str prefix "-value")) (:has-numerical-value m))
      (some? (:has-unit m))
      (assoc (keyword (str prefix "-unit")) (name (:has-unit m))))))

(defn- agent-attrs [prefix a]
  (cond
    (nil? a) {}
    (map? a) (cond-> {(keyword (str prefix "-id")) (str (or (:id a) (:name a)))}
               (:company/lei a) (assoc :company/lei (:company/lei a))
               (:name a) (assoc (keyword (str prefix "-name")) (:name a)))
    :else {(keyword (str prefix "-id")) (str a)}))

(defn event->entity
  "One EconomicEvent -> one entity map. The behaviour cells are denormalised
   onto the event so a query can ask 'which events incremented an inventory'
   without also loading the vocabulary."
  [i event]
  (let [a (:action event)]
    (merge
     (base i)
     {:vf.event/action (some-> a name)}
     (when (vocab/action? a)
       {:vf.event/input-output (some-> (vocab/input-output a) name)
        :vf.event/accounting-effect (some-> (vocab/effect a :accounting-effect) name)
        :vf.event/onhand-effect (some-> (vocab/effect a :onhand-effect) name)
        :vf.event/effort-based? (vocab/effort-based? a)})
     (agent-attrs "vf.event/provider" (:provider event))
     (agent-attrs "vf.event/receiver" (:receiver event))
     (measure->attrs "vf.event/resource-quantity" (:resource-quantity event))
     (measure->attrs "vf.event/effort-quantity" (:effort-quantity event))
     (cond-> {}
       (:resource-inventoried-as event)
       (assoc :vf.event/resource-inventoried-as (str (:resource-inventoried-as event)))
       (:to-resource-inventoried-as event)
       (assoc :vf.event/to-resource-inventoried-as (str (:to-resource-inventoried-as event)))
       (:input-of event) (assoc :vf.event/input-of (str (:input-of event)))
       (:output-of event) (assoc :vf.event/output-of (str (:output-of event)))
       (:has-point-in-time event) (assoc :vf.event/has-point-in-time (str (:has-point-in-time event)))
       (:note event) (assoc :vf.event/note (:note event))
       (:repo/path event) (assoc :repo/path (:repo/path event))))))

(defn resource->entity [i [res-id r]]
  (merge
   (base i)
   {:vf.resource/id (str res-id)}
   (measure->attrs "vf.resource/accounting-quantity" (:accounting-quantity r))
   (measure->attrs "vf.resource/onhand-quantity" (:onhand-quantity r))
   (agent-attrs "vf.resource/primary-accountable" (:primary-accountable r))
   (cond-> {}
     (:current-location r) (assoc :vf.resource/current-location (str (:current-location r)))
     (:contained-in r) (assoc :vf.resource/contained-in (str (:contained-in r)))
     (:stage r) (assoc :vf.resource/stage (str (:stage r)))
     (:state r) (assoc :vf.resource/state (str (:state r)))
     (:conforms-to r) (assoc :vf.resource/conforms-to (str (:conforms-to r))))))

(defn coverage->entity
  "What was NOT projected. Declared as an entity so a count query cannot read
   as complete coverage — the same device `:concept/coverage` uses."
  [i {:keys [events-in events-out resources-in resources-out skipped-reasons]}]
  (merge (base i)
         {:vf.coverage/events-offered events-in
          :vf.coverage/events-projected events-out
          :vf.coverage/resources-offered resources-in
          :vf.coverage/resources-projected resources-out
          :vf.coverage/complete? (and (= events-in events-out)
                                      (= resources-in resources-out))}
         (when (seq skipped-reasons)
           {:vf.coverage/skipped-reasons (pr-str skipped-reasons)})))

(defn project
  "events + inventory -> tx-data ready for `d/transact`, ending with one
   coverage entity. Events that are not maps are skipped and counted, never
   silently dropped."
  [events inventory]
  (let [evs (vec (filter map? events))
        res (vec (filter (fn [[_ v]] (map? v)) inventory))
        ents (concat (map-indexed event->entity evs)
                     (map-indexed (fn [i r] (resource->entity (+ i (count evs)) r)) res))
        skipped (cond-> []
                  (< (count evs) (count events)) (conj :non-map-event)
                  (< (count res) (count inventory)) (conj :non-map-resource))]
    (conj (vec ents)
          (coverage->entity (+ (count evs) (count res))
                            {:events-in (count events) :events-out (count evs)
                             :resources-in (count inventory) :resources-out (count res)
                             :skipped-reasons skipped}))))
