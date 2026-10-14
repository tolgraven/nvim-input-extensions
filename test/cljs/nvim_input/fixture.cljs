(ns nvim-input.fixture
  (:require [reagent.core :as r :refer [defc]]
            [reagent.dom.client :as dom]
            [nvim-input.background :as background]
            [nvim-input.offscreen :as offscreen]
            [nvim-input.neovim.runtime :as runtime]
            [nvim-input.content :as content]))

(defonce *value (r/atom "Controlled initial text"))
(defonce *write-checks (r/atom "Not run"))

(defn check-written-text! [text]
  (let [{:keys [session ready stop!]} (runtime/start! {:cols 40 :rows 12 :initial-text text :filetype "text"}
                                                   (fn [_]) (fn [_ _]))
        *release (atom nil)
        *result (atom nil)
        *failure (atom nil)]
    (-> ready
        (.then (fn [value]
                 (assert value "WASM startup failed")
                 (let [watch (runtime/watch-writes! session #(reset! *result %) #(reset! *failure %))]
                   (reset! *release (:stop! watch))
                   (:ready watch))))
        (.then #(runtime/request! session "nvim_command" ["write"]))
        (.then (fn [_]
                 (assert (nil? @*failure))
                 (assert (= text (:text @*result)) "Native writes must preserve exact input bytes")
                 (runtime/request! session "nvim_command" ["write! alternate.txt"])))
        (.then (fn [_]
                 (assert (= text (:text @*result)) "Named writes must preserve exact input bytes")
                 (assert (.endsWith (:path @*result) "/alternate.txt") "Named writes must use the written target, not the buffer name")
                 (@*release)
                 (let [watch (runtime/watch-writes! session
                               (fn [_] (throw (js/Error. "Consumer rejected this write")))
                               #(reset! *failure %))]
                   (reset! *release (:stop! watch))
                   (:ready watch))))
        (.then #(runtime/request! session "nvim_command" ["write"]))
        (.then (fn [_]
                 (assert (= "Consumer rejected this write" (some-> @*failure .-message)))
                 (runtime/request! session "nvim_buf_get_lines" [0 0 -1 true])))
        (.then (fn [lines] (assert (pos? (.-length lines)) "Consumer failure must keep RPC usable")))
        (.finally (fn [] (when-let [release! @*release] (release!)) (stop!))))))

(defn write-checks! []
  (reset! *write-checks "Running…")
  (-> (reduce (fn [pending text] (.then pending #(check-written-text! text)))
              (js/Promise.resolve nil) ["hello\n" "" "hello" "λ\n" "hello\n\n" "hello\r\n"])
      (.then #(reset! *write-checks "PASS: exact native writes for empty, Unicode, LF, CRLF and missing EOF; named targets preserved; consumer failures preserve RPC"))
      (.catch #(reset! *write-checks (str "FAIL: " (.-message %))))))

(defc <controlled> []
  [:div
   [:label {:for "controlled"} "Reagent controlled textarea"]
   [:textarea {:id "controlled" :value @*value :rows 10
               :on-change #(reset! *value (.. % -target -value))}]
   [:output {:id "controlled-value"} @*value]
   [:button {:id "controlled-update" :type "button" :on-click #(reset! *value "Updated by the application\nλ🙂\n")} "Update from application"]
   [:button {:type "button" :on-click write-checks!} "Run upstream write checks"]
   [:output {:id "write-results"} @*write-checks]])

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
