(ns nvim-input.offscreen
  (:require [nvim-input.neovim.native :as transport]))

(defonce *sessions (atom {}))

(defn emit! [id op value]
  (-> (.sendMessage js/chrome.runtime #js {:target "editor" :session id :op op :value value})
      (.catch (fn [_] nil))))

(defn stop! [id]
  (when-let [^js session (get @*sessions id)]
    (.stop session)
    (swap! *sessions dissoc id)))

(defn handle! [^js message]
  (let [id (.-session message)]
    (case (.-op message)
      "start" (do
                (stop! id)
                (when-not (and js/crossOriginIsolated (exists? js/SharedArrayBuffer))
                  (throw (js/Error. "The extension worker host has no isolated shared memory.")))
                (let [session (transport/start (.getURL js/chrome.runtime "vendor/neovim/nvim-profile-worker.js")
                                #(emit! id "redraw" %)
                                #(emit! id "status" %)
                                #(emit! id "exit" %)
                                #(emit! id "failure" %))]
                  (swap! *sessions assoc id session)
                  (-> (.-ready session)
                      (.then #(emit! id "ready" true))
                      (.catch #(emit! id "failure" (.-message %)))))
                (js/Promise.resolve #js {}))
      "stop" (do (stop! id) (js/Promise.resolve #js {}))
      (if-let [^js session (get @*sessions id)]
        (case (.-op message)
          "types" (do (.registerTypes session (.-value message)) (js/Promise.resolve #js {}))
          "listen" (do (.listen session (.-method message)
                         (fn [value]
                           (-> (.sendMessage js/chrome.runtime
                                 #js {:target "editor" :session id :op "notification"
                                      :method (.-method message) :value value})
                               (.catch (fn [_] nil)))))
                       (js/Promise.resolve #js {}))
          "request" (-> (.request session (.-method message) (.-params message))
                        (.then (fn [value] #js {:value value})))
          (js/Promise.reject (js/Error. "Unknown editor operation")))
        (js/Promise.reject (js/Error. "Editor session is closed"))))))

(defn init! []
  (.addListener js/chrome.runtime.onMessage
    (fn [^js message ^js sender respond!]
      (if (and (= "engine" (.-target message))
               (= (.-id sender) js/chrome.runtime.id)
               (nil? (.-tab sender)))
        (do
          (-> (js/Promise.resolve nil)
              (.then #(handle! message))
              (.then respond!)
              (.catch #(respond! #js {:error (.-message %)})))
          true)
        false))))
