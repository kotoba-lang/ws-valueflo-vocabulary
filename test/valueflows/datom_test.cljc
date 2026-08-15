(ns valueflows.datom-test
  (:require [clojure.test :refer [deftest is testing]]
            [valueflows.datom :as d]))

(def events
  [{:action :work :provider {:id "aki" :name "Aki"}
    :effort-quantity {:has-numerical-value 3 :has-unit :hour}
    :input-of "baking" :repo/path "orgs/cloud-itonami/cloud-itonami-isic-1071"}
   {:action :produce :receiver {:id "bakery" :company/lei "ZSN2LWNPYW6ISMRUC664"}
    :resource-inventoried-as "loaf"
    :resource-quantity {:has-numerical-value 12 :has-unit :each}
    :output-of "baking"}])

(def inventory
  {"loaf" {:onhand-quantity {:has-numerical-value 12 :has-unit :each}
           :accounting-quantity {:has-numerical-value 12 :has-unit :each}
           :primary-accountable {:id "bakery"}}})

(deftest every-entity-is-tagged-with-its-dataset
  (let [tx (d/project events inventory)]
    (is (every? #(= "valueflows" (:source/dataset %)) tx))
    (is (every? #(neg? (:db/id %)) tx) "tempids, ready for transact")
    (is (apply distinct? (map :db/id tx)) "no two entities share a tempid")))

(deftest the-behaviour-cells-travel-with-the-event
  (let [tx (d/project events inventory)
        work (first (filter #(= "work" (:vf.event/action %)) tx))
        produce (first (filter #(= "produce" (:vf.event/action %)) tx))]
    (is (= "input" (:vf.event/input-output work)))
    (is (true? (:vf.event/effort-based? work)))
    (is (= 3 (:vf.event/effort-quantity-value work)))
    (is (= "hour" (:vf.event/effort-quantity-unit work)))
    (is (= "increment" (:vf.event/onhand-effect produce)))
    (is (false? (:vf.event/effort-based? produce)))))

(deftest join-keys-are-carried-only-when-present
  (let [tx (d/project events inventory)
        lei (keep :company/lei tx)
        repo (keep :repo/path tx)]
    (is (= ["ZSN2LWNPYW6ISMRUC664"] (vec lei))
        "the agent that has an LEI joins market-intel; the one without gets none invented")
    (is (= ["orgs/cloud-itonami/cloud-itonami-isic-1071"] (vec repo)))))

(deftest one-dataset-so-the-join-stays-reachable
  ;; kotobase reaches one ref for a Datalog join. Events and resources must
  ;; therefore land in the same dataset, or "who produced this resource" stops
  ;; being answerable.
  (let [tx (d/project events inventory)]
    (is (= 1 (count (distinct (map :source/dataset tx)))))
    (is (seq (filter :vf.event/action tx)))
    (is (seq (filter :vf.resource/id tx)))))

(deftest coverage-is-declared-so-a-count-cannot-read-as-complete
  (testing "a clean projection says so"
    (let [cov (last (d/project events inventory))]
      (is (true? (:vf.coverage/complete? cov)))
      (is (= 2 (:vf.coverage/events-offered cov)))
      (is (= 2 (:vf.coverage/events-projected cov)))))
  (testing "a skipped input is counted, not dropped"
    (let [tx (d/project (conj events "not-an-event") inventory)
          cov (last tx)]
      (is (= 3 (:vf.coverage/events-offered cov)))
      (is (= 2 (:vf.coverage/events-projected cov)))
      (is (false? (:vf.coverage/complete? cov)))
      (is (re-find #"non-map-event" (:vf.coverage/skipped-reasons cov)))))
  (testing "an empty projection still emits coverage"
    (let [tx (d/project [] {})]
      (is (= 1 (count tx)))
      (is (= 0 (:vf.coverage/events-offered (first tx)))))))
