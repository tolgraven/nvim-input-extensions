(ns nvim-input.config
  (:require [clojure.string :as string]))

(def defaults {:enabled true :blacklist []})

(defn normalize-host [text]
  (-> text string/trim string/lower-case (string/replace #"\.$" "")))

(defn valid-rule? [rule]
  (boolean (re-matches #"(?:\*\.)?(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)*[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" rule)))

(defn parse-rules [text]
  (->> (string/split-lines text) (map normalize-host) (remove string/blank?) distinct vec))

(defn blocked? [rules hostname]
  (let [host (normalize-host hostname)]
    (boolean (some (fn [rule]
                     (let [suffix (string/replace rule #"^\*\." "")]
                       (or (= host suffix) (string/ends-with? host (str "." suffix))))) rules))))

(defn allowed? [config hostname]
  (and (:enabled config) (not (blocked? (:blacklist config) hostname))))
