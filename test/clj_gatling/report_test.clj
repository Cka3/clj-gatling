(ns clj-gatling.report-test
  (:require [clojure.test :refer :all]
            [clj-time.core :refer [local-date-time]]
            [clj-gatling.report :as report]
            [clj-containment-matchers.clojure-test :refer :all]
            [clojure.core.async :as a :refer [onto-chan chan]]
            [clj-containment-matchers.clojure-test :refer :all]))

(def scenario-results
  [{:name "Test scenario" :id 1 :start 1391936496814 :end 1391936496814
    :requests [{:id 1 :name "Request1" :start 1391936496853 :end 1391936497299 :result true}
               {:id 1 :name "Request2" :start 1391936497299 :end 1391936497996 :result true}]}
   {:name "Test scenario" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}
               {:id 0 :name "Request2" :start 1391936498430 :end 1391936498450 :result false}]}
   {:name "Test scenario2" :id 0 :start 1391936496808 :end 1391936496808
    :requests [{:id 0 :name "Request1" :start 1391936497998 :end 1391936498426 :result true}]}])

(def expected-lines-1
  [["clj-gatling" "mySimulation" "RUN" "20140209110136" "\u0020" "2.0"]
   ["Test scenario" "1" "USER" "START" "1391936496814" "1"]
   ["Test scenario" "1" "REQUEST" "" "Request1" "1391936496853" "1391936496853" "1391936497299" "1391936497299" "OK" "\u0020"]
   ["Test scenario" "1" "REQUEST" "" "Request2" "1391936497299" "1391936497299" "1391936497996" "1391936497996" "OK" "\u0020"]
   ["Test scenario" "1" "USER" "END" "1391936496814" "1391936496814"]
   ["Test scenario" "0" "USER" "START" "1391936496808" "0"]
   ["Test scenario" "0" "REQUEST" "" "Request1" "1391936497998" "1391936497998" "1391936498426" "1391936498426" "OK" "\u0020"]
   ["Test scenario" "0" "REQUEST" "" "Request2" "1391936498430" "1391936498430" "1391936498450" "1391936498450" "KO" "\u0020"]
   ["Test scenario" "0" "USER" "END" "1391936496808" "1391936496808"]])

(def expected-lines-2
  [["clj-gatling" "mySimulation" "RUN" "20140209110136" "\u0020" "2.0"]
   ["Test scenario2" "0" "USER" "START" "1391936496808" "0"]
   ["Test scenario2" "0" "REQUEST" "" "Request1" "1391936497998" "1391936497998" "1391936498426" "1391936498426" "OK" "\u0020"]
   ["Test scenario2" "0" "USER" "END" "1391936496808" "1391936496808"]])

(defn- from [coll]
  (let [c (chan)]
    (onto-chan c coll)
    c))

;TODO Remove dependency to gatling-csv-writer and test it separately
(deftest maps-scenario-results-to-log-lines
  (let [result-lines [(promise) (promise)]
        start-time (local-date-time 2014 2 9 11 1 36)
        output-writer (fn [simulation idx result]
                        (deliver (nth result-lines idx) (report/gatling-csv-lines start-time
                                                                                  simulation
                                                                                  idx
                                                                                  result)))
        summary (report/create-result-lines {:name "mySimulation"}
                                            2
                                            (from scenario-results)
                                            output-writer)]
    (is (equal? summary {:ok 4 :ko 1}))
    (is (equal? @(first result-lines) expected-lines-1))
    (is (equal? @(second result-lines) expected-lines-2))))

(deftest waits-results-to-be-written-before-returning
  (let [result-lines [(atom nil) (atom nil)]
        start-time (local-date-time 2014 2 9 11 1 36)
        slow-writer (fn [simulation idx result]
                      (Thread/sleep 100)
                      (reset! (nth result-lines idx) (report/gatling-csv-lines start-time
                                                                               simulation
                                                                               idx
                                                                               result)))]
    (report/create-result-lines {:name "mySimulation"}
                                2
                                (from scenario-results)
                                slow-writer)
    (is (equal? @(first result-lines) expected-lines-1))
    (is (equal? @(second result-lines) expected-lines-2))))
