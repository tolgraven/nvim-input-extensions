(ns nvim-input.fixture
  (:require [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as dom]
            [nvim-input.background :as background]
            [nvim-input.offscreen :as offscreen]
            [nvim-input.content :as content]))

(defonce *value (r/atom "Controlled initial text"))
(defc <controlled> []
  [:div
   [:label {:for "controlled"} "Reagent controlled textarea"]
   [:textarea {:id "controlled" :value @*value :rows 10
               :on-change #(reset! *value (.. % -target -value))}]
   [:output {:id "controlled-value"} @*value]
   [:button {:id "controlled-update" :type "button" :on-click #(reset! *value "Updated by the application\nλ🙂\n")} "Update from application"]])

(defn init! []
  ;; This synthetic page owns its adapter. Stop focus at the window boundary so
  ;; an installed copy cannot also acquire the same fields; it stays enabled
  ;; and unaffected on every other page.
  (.addEventListener js/window "focusin"
    (fn [event]
      (let [element (.-target event)]
        (when (= "TEXTAREA" (.-tagName element))
          (.stopImmediatePropagation event)
          (if-let [host (:host (get @content/*editors element))]
            (content/focus-editor! host)
            (content/attach! element))))) true)
  (background/init!)
  (offscreen/init!)
  (content/init!)
  (dom/render (dom/create-root (.getElementById js/document "controlled-root")) [<controlled>])
  (.addEventListener (.getElementById js/document "plain-form") "submit"
    (fn [event]
      (.preventDefault event)
      (set! (.-textContent (.getElementById js/document "submitted"))
            (.get (js/FormData. (.-target event)) "body"))))
  (doseq [type ["input" "change"]]
    (.addEventListener (.getElementById js/document "plain") type
      #(set! (.-textContent (.getElementById js/document "events"))
             (str (.-textContent (.getElementById js/document "events")) " " (.-type %)))))
  (.addEventListener (.getElementById js/document "dynamic") "click"
    (fn []
      (let [field (.createElement js/document "textarea")]
        (set! (.-id field) "added")
        (set! (.-value field) "Dynamic initial")
        (.append (.getElementById js/document "dynamic-root") field))))
  (set! (.-fixture js/window)
    #js {:editors #(count @content/*editors)
         :workers #(count @offscreen/*sessions)
         :blacklist (fn [hosts] (.set js/chrome.storage.local #js {:blacklist hosts}))}))
