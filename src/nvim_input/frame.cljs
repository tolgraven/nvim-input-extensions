(ns nvim-input.frame
  (:require [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as dom]
            [nvim-input.textarea :as textarea]
            [nvim-input.virtual-files :as virtual-files]
            [nvim-input.neovim.runtime :as runtime]
            [nvim-input.neovim.views :as views]))

(defonce *sync (atom nil))
(defonce *dummy (atom nil))
(defonce *parent-origin (atom nil))
(defonce *revision (atom 0))
(defonce *initialized? (atom false))
(defonce *files-cleanup (atom nil))
(def channel (subs (.-hash js/location) 1))

(defn send! [message]
  (.postMessage js/parent (js/Object.assign #js {:channel channel} message) (or @*parent-origin "*")))

(defc <editor> [options]
  [:div.inline-editor
   [views/<editor> options]
   [:button.neovim__action.neovim__restore
    {:type "button" :aria-label "Use regular textarea" :title "Use regular textarea"
     :on-click #(send! #js {:op "bypass"})}
    [:svg {:view-box "0 0 24 24" :aria-hidden true}
     [:rect {:x 3 :y 4 :width 18 :height 16 :rx 2}]
     [:path {:d "M7 8h10M7 12h10M7 16h6"}]]]])

(defn minimum-height []
  (let [probe (.querySelector js/document ".neovim__measure")
        editor (.querySelector js/document ".neovim")
        style (.getComputedStyle js/window editor)]
    (js/Math.ceil (+ (* runtime/min-fit-rows (.-height (.getBoundingClientRect probe)))
                     (js/parseFloat (.-borderTopWidth style))
                     (js/parseFloat (.-borderBottomWidth style))))))

(defn initialize! [^js message]
  (when (and (string? (.-value message)) (compare-and-set! *initialized? false true))
    (let [dummy (.createElement js/document "textarea")
          label (if (string? (.-label message)) (.-label message) "Text")
          fail! #(send! #js {:op "failure" :value (.-message %)})
          ready! (fn [session]
                   (when-let [sync @*sync] ((:stop! sync)))
                   (when-let [stop! @*files-cleanup] (stop!))
                   (reset! *files-cleanup nil)
                   (-> (virtual-files/start! session
                         (fn [error]
                           (js/console.warn "Neovim Input could not persist a virtual file:" (.-message error))
                           (-> (runtime/request! session "nvim_echo"
                                 [[[(str "Virtual file: " (.-message error)) "ErrorMsg"]] true {}])
                               (.catch (fn [_] nil)))))
                       (.then (fn [stop!]
                                (reset! *files-cleanup stop!)
                                (textarea/sync! session dummy
                         (fn []
                           (when-let [grid (.querySelector js/document ".neovim__grid")]
                             (.setAttribute grid "aria-label" (str label " — Neovim"))
                             (.focus grid))
                           (send! #js {:op "ready" :minimumHeight (minimum-height)})) fail!)))
                       (.then #(reset! *sync %))
                       (.catch fail!)))]
      (reset! *revision (or (.-revision message) 0))
      (set! (.-value dummy) (.-value message))
      (when (and (number? (.-maxLength message)) (not (neg? (.-maxLength message)))) (set! (.-maxLength dummy) (.-maxLength message)))
      (reset! *dummy dummy)
      (.addEventListener dummy "input" #(send! #js {:op "input" :value (.-value dummy) :revision @*revision}))
      (dom/render (dom/create-root (.getElementById js/document "app"))
        [<editor> {:initial-text "" :filetype "text" :fit-height? true :hide-toolbar? true
                   :rows (max 8 (min 30 (or (.-rows message) 10)))
                   :on-ready ready!
                   :on-status (fn [status message]
                                (when (#{:error :exited :unsupported} status)
                                  (fail! (js/Error. (or message (name status))))))}]))))

(defn init! []
  (.addEventListener js/window "pagehide"
    (fn [_]
      (when-let [sync @*sync] ((:stop! sync)))
      (when-let [stop! @*files-cleanup] (stop!))))
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
  (send! #js {:op "loaded"}))
