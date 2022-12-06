;; #!/usr/bin/env bb
(ns hm.borp
  (:require ;;[clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.util Locale]
           [org.joda.time Chronology DateTime DateTimeZone Interval ReadableDateTime
            LocalDateTime Period PeriodType LocalDate LocalTime]
           [org.joda.time.format DateTimeFormat DateTimeFormatter
            DateTimePrinter DateTimeFormatterBuilder
            DateTimeParser ISODateTimeFormat])
  (:gen-class))

(def ^{:doc "DateTimeZone for UTC."}
  utc
  (DateTimeZone/UTC))

(def formatter
  (.withZone (DateTimeFormat/forPattern "YYYY-MM-dd'T'HH:mm:ss.SSSZ")
             utc))

(defn now
  "Returns a DateTime for the current instant in the UTC time zone."
  ^org.joda.time.DateTime
  []
  (DateTime. ^DateTimeZone utc))

(defn parse [s]
  (.parseDateTime formatter s))

(def reftime (atom (now)))

(defn interval
  "Returns an interval representing the span between the two given ReadableDateTimes.
   Note that intervals are closed on the left and open on the right."
  ^Interval [^ReadableDateTime dt-a ^ReadableDateTime dt-b]
  (Interval. dt-a dt-b))

(defn extract-match [rex text]
  (when-let [match(re-matches rex text)]
    (second match)))

(def extract-topic (partial extract-match #".*\"\"topic\"\":\"\"([^\"]*)\"\".*"))
(def extract-id    (partial extract-match #".*\"\"worker-name\"\":\"\"([^\"]*)\"\".*"))
(def extract-time  (partial extract-match #"^\"([^\"]*)\",.*"))
(def extract-host  (partial extract-match #"^[^,]*,,\"\"\"([^\"]*)\"\"\".*"))

(defn minutes-ago [time-string]
  (when time-string
    (-> time-string
        parse
        (interval @reftime)
        (.toPeriod (PeriodType/minutes))
        .getMinutes)))

(defn rename-matcha [^String m]
  (get {\1 "1111------"
        \2 "2---222---"
        \3 "3------333"}
       (last m)))

(defn extract-all [text]
  [(clojure.string/join
     " "
     [(extract-topic text)
      (rename-matcha(extract-host text))
      (extract-id    text)])
   (-> text extract-time minutes-ago)])

(defn extract-latest
  "assumes the most recent time stamps are first."
  [acc text]
  (let [[k v] (extract-all text)]
    (cond-> acc
      (not (contains? acc k)) (assoc k v))))

(defn to-str [pair]
  (clojure.string/join " " pair))

(defn find-suspect [{:keys [name minutes] :as prev} pair]
  (let [k (first pair)
        v (second pair)]
    (if v
      (if (< minutes v)
        {:name k :minutes v}
        prev)
      prev)))

(defn make-report [logs]
  (with-open [rdr (clojure.java.io/reader logs)]
    (let [lines (-> rdr line-seq rest)
          _ (->> lines first extract-time parse (reset! reftime))
          info (reduce extract-latest {} lines)]
      (->> info
           (map to-str)
           sort
           (map println)
           doall)
      (println "suspect:" (reduce find-suspect {:name "foo" :minutes -1} info)))))

(defn -main [& args]
  (make-report (first args)))

;; to run:
;; https://app.datadoghq.com/logs?query=service%3A%28matcha1%20OR%20matcha2%20OR%20matcha3%29%20%40env%3Aprod%20%40level%3Atrace%20%40worker-name%3A%2A%20%40topic%3A%2A&cols=host%2Cservice&index=&messageDisplay=inline&stream_sort=time%2Cdesc&viz=stream&from_ts=1669876350129&to_ts=1669962750129&live=true
;; borp clojure -M -m hm.borp <full-path-to-extract.csv>
