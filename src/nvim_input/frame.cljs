(ns nvim-input.frame
  (:require [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as dom]
            [nvim-input.textarea :as textarea]
            [nvim-input.neovim.views :as views]))

(defonce *sync (atom nil))
(defonce *dummy (atom nil))
(defonce *parent-origin (atom nil))
(defonce *revision (atom 0))
(defonce *initialized? (atom false))
(def channel (subs (.-hash js/location) 1))

(defn send! [message]
  (.postMessage js/parent (js/Object.assign #js {:channel channel} message) (or @*parent-origin "*")))

(defc <editor> [options]
  [:<>
   [views/<editor> options]
   [:button {:type "button" :on-click #(send! #js {:op "bypass"})} "Use regular textarea"]])

(defn initialize! [^js message]
  (when (and (string? (.-value message)) (compare-and-set! *initialized? false true))
    (let [dummy (.createElement js/document "textarea")
          label (if (string? (.-label message)) (.-label message) "Text")
          fail! #(send! #js {:op "failure" :value (.-message %)})
          ready! (fn [session]
                   (when-let [sync @*sync] ((:stop! sync)))
                   (-> (textarea/sync! session dummy
                         (fn []
                           (when-let [grid (.querySelector js/document ".neovim__grid")]
                             (.setAttribute grid "aria-label" (str label " — Neovim"))
                             (.focus grid))
                           (send! #js {:op "ready"})) fail!)
                       (.then #(reset! *sync %))
                       (.catch fail!)))]
      (reset! *revision (or (.-revision message) 0))
      (set! (.-value dummy) (.-value message))
      (when (and (number? (.-maxLength message)) (not (neg? (.-maxLength message)))) (set! (.-maxLength dummy) (.-maxLength message)))
      (reset! *dummy dummy)
      (.addEventListener dummy "input" #(send! #js {:op "input" :value (.-value dummy) :revision @*revision}))
      (dom/render (dom/create-root (.getElementById js/document "app"))
        [<editor> {:initial-text "" :filetype "text"
                   :rows (max 8 (min 30 (or (.-rows message) 10)))
                   :on-ready ready!
                   :on-status (fn [status message]
                                (when (#{:error :exited :unsupported} status)
                                  (fail! (js/Error. (or message (name status))))))}]))))

(defn init! []
  (.addEventListener js/window "message"
    (fn [event]
      (let [^js message (.-data event)]
        (when (and (some? message) (identical? (.-source event) js/parent) (= channel (.-channel message))
                   (or (nil? @*parent-origin) (= @*parent-origin (.-origin event))))
          (case (.-op message)
            "init" (do (reset! *parent-origin (.-origin event)) (initialize! message))
            "value" (when (and @*dummy (string? (.-value message)))
                      (reset! *revision (.-revision message))
                      (set! (.-value @*dummy) (.-value message))
                      (when-let [sync @*sync] ((:pull! sync))))
            "focus" (when-let [grid (.querySelector js/document ".neovim__grid")] (.focus grid))
            "flush" (when-let [sync @*sync]
                      (-> ((:flush! sync))
                          (.then #(do (send! #js {:op "input" :value (.-value @*dummy) :revision @*revision})
                                      (send! #js {:op "result" :id (.-id message) :revision @*revision})))
                          (.catch #(send! #js {:op "failure" :value (.-message %)}))))
            nil)))))
  (.addEventListener js/window "blur"
    #(when-let [sync @*sync]
       (-> ((:flush! sync)) (.then (fn [] (send! #js {:op "change"}))) (.catch (fn [_] nil)))))
  (let [observer (js/ResizeObserver.
                  #(send! #js {:op "height" :value (.-scrollHeight (.-body js/document))}))]
    (.observe observer (.-body js/document)))
  (send! #js {:op "loaded"}))
