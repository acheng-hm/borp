# hm/borp

Box workers thread report - borp for short.

This utility helps you discover any dead box worker threads in Matcha by printing the topic + matcha instance + thread id + number of minutes since last log event.

There are 3 threads per worker type per instance of Matcha. This number (3) is configured in Matcha's `common.edn` at `[:services :file-upload :number-of-workers]` and `[:services :docusign-document-download :number-of-workers]`

## Installation

Prerequisites: [brew](https://brew.sh/), [Java](https://clojure.org/guides/install_clojure#java)

`brew install clojure/tools/clojure`

`git clone git@github.com:acheng-hm/borp.git`

## Usage

1. Go to this DataDog query https://app.datadoghq.com/logs?query=service%3A%28matcha1%20OR%20matcha2%20OR%20matcha3%29%20%40env%3Aprod%20%40level%3Atrace%20%40worker-name%3A%2A%20%40topic%3A%2A&cols=host%2Cservice&index=&messageDisplay=inline&stream_sort=time%2Cdesc&viz=stream&from_ts=1669876350129&to_ts=1669962750129&live=true If the link does not work for you, search the past 1 day with this query `service:(matcha1 OR matcha2 OR matcha3) @env:prod @level:trace @worker-name:* @topic:*`
1. Make sure you have 4 columns in your output: date, host, service, content.
1. Export the results as CSV.
1. `cd` to the root directory of this git repo.
1. `clojure -M -m hm.borp <full path to your extract.csv file from step 4>`

Box workers are healthy if

* you see 3 threads (guids) per topic per Matcha instance
* AND you _eventually_ see a new log entry in DataDog from the worst offender (the one with the longest time since the last log event)
