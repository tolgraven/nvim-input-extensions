(ns nvim-input.background)

(defonce *editors (atom {}))
(defonce *creating (atom nil))

(defn engine! [id ^js message]
  (.sendMessage js/chrome.runtime
    (js/Object.assign #js {} message #js {:target "engine" :session id})))

(defn ensure-host! []
  (or @*creating
      (let [pending (-> (.getContexts js/chrome.runtime
                          #js {:contextTypes #js ["OFFSCREEN_DOCUMENT"]
                               :documentUrls #js [(.getURL js/chrome.runtime "offscreen.html")]})
                        (.then (fn [contexts]
                                 (when (zero? (.-length contexts))
                                   (.createDocument js/chrome.offscreen
                                     #js {:url "offscreen.html" :reasons #js ["WORKERS"]
                                          :justification "Run the Neovim WASM worker with isolated shared memory for inline textarea editors."})))))]
        (reset! *creating pending)
        (.finally pending #(reset! *creating nil)))))

(defn post! [^js port ^js message]
  (try (.postMessage port message) (catch :default _ nil)))

(defn connect! [^js port]
  (when (= "nvim-editor" (.-name port))
    (let [id (str (random-uuid))
          *started? (atom false)
          *queue (atom (js/Promise.resolve nil))]
      (swap! *editors assoc id port)
      (.addListener (.-onMessage port)
        (fn [^js message]
          ;; Preserve start/types/listen/request order across asynchronous document creation.
          (swap! *queue
            (fn [pending]
              (-> pending
                  (.then (fn []
                           (when (get @*editors id)
                             (when (= "start" (.-op message)) (reset! *started? true))
                             (-> (ensure-host!)
                                 (.then #(when (get @*editors id) (engine! id message)))
                                 (.then (fn [response]
                                          (when (and response (= "request" (.-op message)))
                                            (post! port (js/Object.assign #js {:op "result" :id (.-id message)} response)))))))))
                  (.catch #(post! port #js {:op "failure" :value (.-message %)})))))))
      (.addListener (.-onDisconnect port)
        (fn []
          (swap! *editors dissoc id)
          (when @*started?
            (-> @*queue (.then #(engine! id #js {:op "stop"})) (.catch (fn [_] nil)))))))))

(defn init! []
  (.addListener js/chrome.runtime.onConnect connect!)
  (.addListener js/chrome.runtime.onMessage
    (fn [^js message ^js sender _]
      (when (and (= "editor" (.-target message))
                 (= (.-url sender) (.getURL js/chrome.runtime "offscreen.html")))
        (when-let [port (get @*editors (.-session message))] (post! port message)))
      false))
  (.addListener js/chrome.action.onClicked #(.openOptionsPage js/chrome.runtime)))
