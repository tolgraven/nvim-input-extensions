(ns nvim-input.options
  (:require [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as dom]
            [nvim-input.config :as config]
            [clojure.string :as string]))

(defonce *form (r/atom {:enabled true :blacklist "" :message "Loading…"}))

(defn save! []
  (let [{:keys [enabled blacklist]} @*form
        rules (config/parse-rules blacklist)]
    (if-let [invalid (first (remove config/valid-rule? rules))]
      (swap! *form assoc :message (str "Enter hostnames, one per line. Invalid rule: " invalid))
      (-> (.set js/chrome.storage.local (clj->js {:enabled enabled :blacklist rules}))
          (.then #(swap! *form assoc :message "Saved. Open pages update automatically."))
          (.catch #(swap! *form assoc :message (.-message %)))))))

(defc <settings> []
  [:<>
   [:h1 "Neovim Input"]
   [:p "Edit ordinary textareas directly in the page. The original form control stays synchronized. Rich text editors are skipped automatically."]
   [:form {:on-submit #(do (.preventDefault %) (save!))}
    [:label [:input {:type "checkbox" :checked (:enabled @*form)
                    :on-change #(swap! *form assoc :enabled (.. % -target -checked))}]
     " Enable inline Neovim"]
    [:label {:for "blacklist"} "Site blacklist"
     [:textarea {:id "blacklist" :value (:blacklist @*form)
                 :placeholder "example.com\n*.example.org"
                 :on-change #(swap! *form assoc :blacklist (.. % -target -value))}]]
    [:p "One hostname per line. Each entry includes its subdomains. No schemes or paths. Settings stay in this browser."]
    [:button {:type "submit"} "Save settings"]
    [:p {:role "status"} (:message @*form)]]])

(defn init! []
  (dom/render (dom/create-root (.getElementById js/document "app")) [<settings>])
  (-> (.get js/chrome.storage.local (clj->js config/defaults))
      (.then #(let [settings (js->clj % :keywordize-keys true)]
                (swap! *form assoc :enabled (:enabled settings)
                       :blacklist (string/join "\n" (:blacklist settings)) :message "")))
      (.catch #(swap! *form assoc :message (.-message %)))))
