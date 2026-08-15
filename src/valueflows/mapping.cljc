(ns valueflows.mapping
  "Where each Valueflows term already lives in this workspace, and where it
   does not.

   Why this is data and not prose: the workspace reached most of REA
   independently and under other names — MRP explosion, mutual credit,
   content-addressed receipts. Written as a paragraph, that correspondence
   rots the first time a repository moves. Written as data it can be queried,
   counted, and shown to be incomplete.

   Status vocabulary, and the distinctions worth keeping:

     :vf-native    implemented here, in Valueflows terms
     :mapped       another repository provides it; the mapping is exact
     :partial      provides part of it; :gap says which part
     :adjacent     structurally similar, DIFFERENT OBJECT. Not a mapping.
     :false-friend shares a word with Valueflows, means something else.
                   Recorded so nobody wires them together by name.
     :absent       nothing here does this

   THIS NAMESPACE CANNOT VERIFY ITSELF. The repositories it cites are west
   projects outside this repository's tree, so a check that read them would be
   a gate whose input is absent — which reports a pass by not looking
   (ADR-2608136000). Each entry therefore carries `:evidence`: the file that
   was read and the date it was read. Evidence is a citation, not a
   measurement, and is labelled as one."
  (:require [valueflows.vocabulary :as vocab]))

(def measured-at "2026-08-15")

(def classes
  "Valueflows class -> where its content already lives."
  {:Agent
   {:status :mapped
    :where ["orgs/cloud-itonami/cloud-itonami-lei-*" "orgs/com-junkawasaki/org-gleif-projections"]
    :join :company/lei
    :note "Legal entities carry an LEI, which is already the plane's join key."
    :evidence "CLAUDE.md ADR-2607252000 dataset list; :company/lei joins market-intel"}

   :EconomicResource
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "PLM items with perpetual on-hand. VF splits accounting from onhand quantity; plm keeps one (erp.inventory/qty-on-hand)."
    :gap :no-separate-accounting-quantity
    :evidence "src/kotoba/plm/mrp.cljc on-hand, read 2026-08-15"}

   :ResourceSpecification
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "Item master; make/buy/phantom classification."
    :evidence "src/kotoba/plm/item.cljc make-buy, read 2026-08-15"}

   :Process
   {:status :partial
    :where ["orgs/kotoba-lang/plm"]
    :note "Routing gives work centres and operations, i.e. process specifications with cost."
    :gap :no-process-network-scheduling
    :evidence "src/kotoba/plm/routing.cljc work-center/operation/process-cost, read 2026-08-15"}

   :Recipe
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "The effective MBOM is a recipe: parent -> children with quantities, effective-dated."
    :evidence "src/kotoba/plm/item.cljc mbom-children, read 2026-08-15"}

   :Plan
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "MPS is time-phased demand, which is what a Plan carries."
    :evidence "src/kotoba/plm/mps.cljc mps-for-item/approved-demand-for-item, read 2026-08-15"}

   :EconomicEvent
   {:status :partial
    :where ["orgs/cloud-itonami/credits" "orgs/kotoba-lang/engi"]
    :note "ENGI transfers are two-party signed events with equal debit and credit — vf:transfer with accountingEffect decrementIncrement, in a ledger that also proves net supply is zero."
    :gap :only-the-transfer-action
    :evidence "orgs/cloud-itonami/credits/README.md constitutional boundary, read 2026-08-15"}

   :Commitment
   {:status :absent
    :note "Nothing here records a promised future economic event with a due date and a fulfilment link."
    :false-friends [:cloud-itonami-commitment-ledger]}

   :Intent
   {:status :absent}

   :Proposal
   {:status :absent
    :note "Offers and requests exist as business surfaces, not as vf:Proposal records."}

   :Agreement
   {:status :partial
    :where ["orgs/cloud-itonami/cloud-itonami-app"]
    :note "Contracts and tenant connections bind parties, but are not modelled as reciprocal commitment bundles."
    :gap :not-reciprocal-commitments
    :evidence "orgs/cloud-itonami/cloud-itonami-app/docs/adr/0008-business-is-the-join-of-five-planes.md (Contracts view) and docs/adr/0014-agent-loops-use-tenant-bound-connections.md, read 2026-08-15"}

   :Unit
   {:status :partial
    :note "Units appear per-repository; there is no shared om-2 unit registry."
    :gap :no-shared-unit-registry}})

(def false-friends
  "Same word, different thing. The point of naming these is that a future
   integration reads the list instead of joining on the word."
  {:cloud-itonami-commitment-ledger
   {:their-word "commitment"
    :their-meaning "a LOAN commitment: a lender's governed offer of loan-based support to a self-registered business, V1 lending only, equity language is a hard governor violation"
    :vf-meaning "vf:Commitment — a planned economic flow that a future EconomicEvent fulfils"
    :do-not "treat the ledger's records as vf:Commitment instances"
    :evidence "orgs/cloud-itonami/cloud-itonami-commitment-ledger/README.md, read 2026-08-15"}

   :engi-economic-event
   {:their-word "economic event"
    :their-meaning "a signed mutual-credit transfer in the ENGI journal"
    :vf-meaning "vf:EconomicEvent — any action on a resource: produce, consume, use, work, transfer, ..."
    :do-not "read the ENGI journal as a complete vf:EconomicEvent log; it carries one action out of nineteen"
    :evidence "orgs/cloud-itonami/credits/test/credits/engi_r3_test.clj:120, read 2026-08-15"}

   :system-dynamics-flow
   {:their-word "flow"
    :their-meaning "a continuous RATE between stocks, integrated over time (Forrester/Meadows), in kotoba-lang/dynamics and org-oasis-open-xmile"
    :vf-meaning "a discrete typed event between agents and resources"
    :do-not "equate a vf network flow with an XMILE flow; both are directed graphs and neither converts to the other"
    :evidence "orgs/kotoba-lang/dynamics/README.md, read 2026-08-15"}})

(def algorithms
  "The eight network-based algorithms from https://www.valueflo.ws/algorithms/overview/
   and their standing here."
  {:dependent-demand
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :vf-native "valueflows.algorithms.dependent-demand"
    :note "Multi-level MBOM explosion, netted against on-hand, raising purchase orders for the shortfall. Reached from the MRP-II lineage, not from Valueflows; upstream describes dependent demand as exactly this."
    :evidence "src/kotoba/plm/mrp.cljc gross-requirements/plan/mrp-run!, read 2026-08-15"}

   :critical-path
   {:status :vf-native
    :was :absent
    :vf-native "valueflows.algorithms.critical-path"
    :note "Nothing here scheduled a process network. plm has routing master data but no forward pass; murakumo's task/plan is greedy least-filled placement over hosts, which is a different problem."
    :evidence "grep -in 'critical.?path' over plm, murakumo, cloud-itonami on 2026-08-15 returned only latency prose and test-coverage prose"}

   :value-rollup
   {:status :partial
    :where ["orgs/kotoba-lang/plm"]
    :vf-native "valueflows.algorithms.value-rollup"
    :note "plm rolls cost through the BOM. A vf rollup implodes any value attribute over the recipe tree, including effort."
    :gap :cost-only
    :evidence "src/kotoba/plm/cost.cljc rolled-cost, read 2026-08-15"}

   :value-equation
   {:status :adjacent
    :where ["orgs/cloud-itonami/credits"]
    :vf-native "valueflows.algorithms.value-equation"
    :note "ENGI recognises contribution — including care work — through bounded multi-role Commons issuance and relational credit lines. That is a different mechanism from distributing an income over contributions, and the closest thing here in intent."
    :evidence "orgs/cloud-itonami/credits/README.md runnable social kernel, read 2026-08-15"}

   :track-and-trace
   {:status :adjacent
    :vf-native "valueflows.algorithms.track-trace"
    :where ["manifest/projection-verify.cljs" "orgs/kotoba-lang/kotobase"]
    :note "The workspace traces ARTEFACTS: content-addressed lineage, receipts binding input hash to output hash. Strong machinery, different object — it does not follow a resource through the events that consumed and produced it."
    :evidence "CLAUDE.md projection-verify contract; ADR-2608039700"}

   :provenance
   {:status :adjacent
    :vf-native "valueflows.algorithms.track-trace"
    :where ["orgs/kotoba-lang/ontology"]
    :note "kotoba-ontology stamps facts with :ontology/source, an ingestion provenance registry. Resource provenance in the vf sense is the constituent-input closure of a resource."
    :evidence "orgs/kotoba-lang/ontology/README.md, read 2026-08-15"}

   :cash-flow
   {:status :partial
    :where ["orgs/cloud-itonami/credits" "70-tools/bmc"]
    :vf-native "valueflows.algorithms.cash-flow"
    :note "Settlement and business metrics exist. Neither presents inflows and outflows on one timeline, historical and forecast, over resources other than money."
    :gap :money-only-and-not-time-phased
    :evidence "orgs/cloud-itonami/credits/src/credits/engi/netting.clj; CLAUDE.md BMC section"}

   :network-flows
   {:status :different-formalism
    :where ["orgs/kotoba-lang/dynamics" "orgs/kotoba-lang/org-oasis-open-xmile"]
    :note "System dynamics: stocks, continuous rates, Meadows leverage points. Both are directed graphs with flows; the semantics do not convert. Keep both."
    :evidence "orgs/kotoba-lang/dynamics/README.md, read 2026-08-15"}})

;; ── queries over the mapping ──────────────────────────────────────────────

(defn by-status [m status] (into (sorted-set) (keep (fn [[k v]] (when (= status (:status v)) k)) m)))

(defn gaps
  "Everything not yet expressed in Valueflows terms, with what is missing.
   A caller wanting 'what is left to do' reads this, not the prose."
  []
  (into (sorted-map)
        (keep (fn [[k v]]
                (when (contains? #{:absent :partial :adjacent :different-formalism} (:status v))
                  [k (select-keys v [:status :gap :note :where])])))
        (merge classes algorithms)))

(defn summary []
  {:measured-at measured-at
   :classes {:total (count classes)
             :by-status (into (sorted-map)
                              (map (fn [[s ks]] [s (count ks)]))
                              (group-by #(:status (val %)) classes))}
   :algorithms {:total (count algorithms)
                :by-status (into (sorted-map)
                                 (map (fn [[s ks]] [s (count ks)]))
                                 (group-by #(:status (val %)) algorithms))}
   :false-friends (into (sorted-set) (keys false-friends))
   ;; stated, not implied: the vocabulary has more classes than are mapped
   :vocabulary-classes (count (vocab/classes))
   :classes-considered (count classes)
   :coverage-note "classes-considered < vocabulary-classes: the rest are unexamined, which is not the same as absent"})
