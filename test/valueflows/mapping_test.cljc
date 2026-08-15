(ns valueflows.mapping-test
  (:require [clojure.test :refer [deftest is testing]]
            [valueflows.mapping :as m]
            [valueflows.vocabulary :as vocab]))

(def ^:private statuses
  #{:vf-native :mapped :partial :adjacent :false-friend :absent :different-formalism})

(deftest every-entry-declares-a-known-status
  (doseq [[k v] (merge m/classes m/algorithms)]
    (is (contains? statuses (:status v)) (str k " has status " (:status v)))))

(deftest a-claim-about-another-repository-carries-its-evidence
  ;; This repository cannot read the repositories it cites — they are west
  ;; projects outside its tree. A citation with no evidence line is an
  ;; unfalsifiable claim, so require one wherever :where is asserted.
  (doseq [[k v] (merge m/classes m/algorithms)]
    (when (:where v)
      (is (string? (:evidence v)) (str k " cites " (:where v) " with no evidence")))))

(deftest a-partial-or-absent-entry-explains-itself
  (doseq [[k v] (merge m/classes m/algorithms)]
    (when (= :partial (:status v))
      (is (or (:gap v) (:note v)) (str k " is partial but says nothing about which part")))))

(deftest the-eight-network-based-algorithms-are-all-accounted-for
  (is (= #{:dependent-demand :critical-path :value-rollup :value-equation
           :track-and-trace :provenance :cash-flow :network-flows}
         (set (keys m/algorithms)))
      "the set from valueflo.ws/algorithms/overview, none quietly dropped"))

(deftest what-was-actually-missing-is-recorded-as-having-been-missing
  ;; critical-path is the one thing nothing here did. Keeping :was :absent
  ;; stops the history from being smoothed over once it works.
  (is (= :vf-native (:status (:critical-path m/algorithms))))
  (is (= :absent (:was (:critical-path m/algorithms)))))

(deftest adjacent-is-not-mapped
  (testing "the workspace traces artefacts, not resources through events"
    (is (= :adjacent (:status (:track-and-trace m/algorithms))))
    (is (= :adjacent (:status (:provenance m/algorithms)))))
  (testing "system dynamics is a different formalism, not a partial mapping"
    (is (= :different-formalism (:status (:network-flows m/algorithms))))))

(deftest false-friends-are-named-so-nobody-joins-on-the-word
  (is (contains? m/false-friends :cloud-itonami-commitment-ledger))
  (is (contains? m/false-friends :engi-economic-event))
  (doseq [[k v] m/false-friends]
    (is (:their-meaning v) (str k))
    (is (:vf-meaning v) (str k))
    (is (:do-not v) (str k " does not say what not to do"))))

(deftest commitment-and-intent-exist-now-and-say-they-did-not-before
  ;; They were :absent until 2026-08-15. Keeping :was means the history is not
  ;; smoothed over once the gap is closed -- the same device :critical-path uses.
  (is (= :vf-native (:status (:Commitment m/classes))))
  (is (= :absent (:was (:Commitment m/classes))))
  (is (= :vf-native (:status (:Intent m/classes))))
  (is (= :absent (:was (:Intent m/classes))))
  (is (string? (:note-on-absence (:Commitment m/classes)))
      "what was missing is recorded, not just that something now exists"))

(deftest the-ledger-is-a-sharper-false-friend-now-that-a-real-commitment-exists
  (let [ff (:cloud-itonami-commitment-ledger m/false-friends)]
    (is (string? (:sharper-since ff))
        "before, the confusion was verbal; now the two can actually be wired together")))

(deftest gaps-are-queryable
  (let [g (m/gaps)]
    (is (contains? g :cash-flow))
    (is (contains? g :Proposal) "still absent")
    (is (not (contains? g :Commitment)) "closed 2026-08-15, so no longer a gap")
    (is (not (contains? g :Intent)))
    (is (not (contains? g :dependent-demand)) "mapped, so not a gap")
    (is (not (contains? g :critical-path)) "vf-native, so not a gap")))

(deftest coverage-is-stated-rather-than-implied
  (let [s (m/summary)]
    (is (= (count (vocab/classes)) (:vocabulary-classes s)))
    (is (< (:classes-considered s) (:vocabulary-classes s))
        "fewer classes examined than exist — and the summary says so")
    (is (string? (:coverage-note s)))))
