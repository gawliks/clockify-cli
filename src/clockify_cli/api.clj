(ns clockify-cli.api
  (:require [clojure.data.json :as json]
            [clojure.string :as strings]
            [clj-http.client :as client]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clj-time.local :as local-time]
            [clockify-cli.config :as config])
  (:import (java.lang Integer))
  (:gen-class))

(def base-url "https://api.clockify.me/api/v1")

(defn default-headers []
  (let [config (config/load-config)]
    {"X-Api-Key" (:clockify-api-key config)}))

(defn request-params
  ([] (request-params {}))
  ([params] (merge {:headers (merge (default-headers) (:headers params))
                    :content-type :json
                    :accept :json
                    :throw-entire-message? true} (dissoc params :headers))))
(defn trace [x]
  (println x)
  x)

(defn handle-response [response]
  (let [body (:body response)
        status (:status response)]
    (if (>= status 200)
      (json/read-str body)
      (throw (Exception. (str "Error: " status " " body))))))

(defn make-request
  ([method url]
   (make-request method url {}))
  ([method url params]
   (handle-response (method (str base-url url) (request-params params)))))

(defn do-get [url]
  (make-request client/get url))

(defn do-post [url params]
  (make-request client/post url params))

(defn get-workspaces []
  (do-get "/workspaces"))

(defn get-workspace-id [workspace-name]
  (let [workspace (first (filter #(= workspace-name (get % "name")) (get-workspaces)))]
    (get workspace "id")))

(defn get-default-workspace-id []
  (get-workspace-id (:workspace (config/load-config))))

(defn get-projects [workspace-id]
  (do-get (str "/workspaces/" workspace-id "/projects")))

(defn get-project-id [workspace-id project-name]
  (let [project (first (filter #(= project-name (get % "name")) (get-projects workspace-id)))]
    (get project "id")))

(defn get-default-project-id []
  (let [config (config/load-config)
        workspace-id (get-default-workspace-id)]
    (get-project-id workspace-id (:project config))))

(defn- get-date-from-time [hour-min, date-time]
  (let [year (time/year date-time)
        month (time/month date-time)
        [hour minute] (map #(Integer/parseInt %) (strings/split hour-min #":"))]
    (time/date-time year month (time/day date-time) hour minute 0 0)))

(defn date-by-day [hour-min day]
  (case day
    "today" (get-date-from-time hour-min (local-time/local-now))
    "yesterday" (get-date-from-time hour-min (time/minus (local-time/local-now) (time/days 1)))
    (get-date-from-time hour-min (time-format/parse (time-format/formatter "yyyy-MM-dd") day))))

(defn format-api-date [date]
  (local-time/format-local-time date :date-time))

(defn add-time-entry [time-entry]
  (let [workspace-id (get-workspace-id (:workspace time-entry))
        project-id (get-project-id workspace-id (:project time-entry))
        params {:body (json/write-str {:description (:description time-entry)
                                       :projectId project-id
                                       :start (format-api-date (date-by-day (:start time-entry) (:day time-entry)))
                                       :end (format-api-date (date-by-day (:end time-entry) (:day time-entry)))})}]
    (try (do-post (str "/workspaces/" workspace-id "/time-entries")
                  params)
         (catch Exception e (println "Error: " (ex-message e))))))