(ns kaocha.monkey-patch
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [kaocha.testable :as testable]
            [kaocha.core-ext :refer :all]
            [kaocha.report :as report]
            [kaocha.hierarchy :as hierarchy]))

(defn- test-file-and-line [stacktrace test-fn]
  (let [test-class-name (.getName (class test-fn))
        trace-matches?  #(str/starts-with? (.getClassName ^StackTraceElement %) test-class-name)]
    (when-let [^StackTraceElement s (first (filter trace-matches? stacktrace))]
      (.getClassName s)
      (when (.getFileName s)
        {:file (.getFileName s) :line (.getLineNumber s)}))))

(defn- stacktrace-file-and-line [stacktrace]
  (if (seq stacktrace)
    (let [^StackTraceElement s (first stacktrace)]
      {:file (.getFileName s) :line (.getLineNumber s)})
    {:file nil :line nil}))

;; This is an unfortunate hack. clojure.test/is wraps all assertions in a
;; try/catch, but we actually want to use an exception to signal a failure when
;; using --fail-fast, so we can skip the rest of the assertions in the var. This
;; detects our own fail-fast exception, and rethrows it, rather than reporting
;; it as an error.
;; See also the fail-fast reporter
(alter-var-root #'t/do-report
                (fn [_]
                  (fn [m]
                    (let [m          (merge {:kaocha/testable  testable/*current-testable*
                                             :kaocha/test-plan testable/*test-plan*} m)
                          test-fn    (:kaocha.var/test (:kaocha/testable m))
                          stacktrace (.getStackTrace (if (exception? (:actual m))
                                                       (:actual m)
                                                       (Thread/currentThread)))
                          file-and-line
                          (or testable/*test-location*
                              (stacktrace-file-and-line (drop-while
                                                         #(let [cl-name (.getClassName ^StackTraceElement %)]
                                                            (or (str/starts-with? cl-name "java.")
                                                                (str/starts-with? cl-name "jdk.internal.reflect.")
                                                                (str/starts-with? cl-name "sun.reflect.")

                                                                (str/starts-with? cl-name "clojure.core")
                                                                (str/starts-with? cl-name "clojure.test$")
                                                                (str/starts-with? cl-name "clojure.lang.")
                                                                (str/starts-with? cl-name "clojure.main$")

                                                                (str/starts-with? cl-name "orchestra.")

                                                                (str/starts-with? cl-name "kaocha.plugin.capture_output")
                                                                (str/starts-with? cl-name "kaocha.monkey_patch$")
                                                                (str/starts-with? cl-name "kaocha.runner")
                                                                (str/starts-with? cl-name "kaocha.watch")
                                                                (str/starts-with? cl-name "kaocha.api")
                                                                (str/starts-with? cl-name "kaocha.testable")
                                                                (str/starts-with? cl-name "kaocha.type.")))
                                                         stacktrace))
                              (and test-fn (test-file-and-line stacktrace test-fn)))]
                      (t/report
                       (if (= :error (:type m))
                         (if (-> m :actual ex-data :kaocha/fail-fast)
                           (throw (:actual m))
                           (merge file-and-line m))

                         (merge file-and-line m)))))))
