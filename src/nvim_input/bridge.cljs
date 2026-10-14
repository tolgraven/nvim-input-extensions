(ns nvim-input.bridge
  "Content-world RPC proxy. Shared memory never crosses into the host page.")

(defn ^js start [_ on-redraw on-status on-exit on-failure]
  (let [port (.connect js/chrome.runtime #js {:name "nvim-editor"})
        *pending (atom {})
        *listeners (atom {})
        *next-id (atom 0)
        *stopped? (atom false)
        *ready (atom nil)
        ready (js/Promise. (fn [resolve reject] (reset! *ready [resolve reject])))
        send! #(.postMessage port %)
        stop! (fn []
                (when (compare-and-set! *stopped? false true)
                  ((second @*ready) (js/Error. "Editor disconnected"))
                  (doseq [[_ {:keys [reject timeout]}] @*pending]
                    (js/clearTimeout timeout)
                    (reject (js/Error. "Editor disconnected")))
                  (reset! *pending {})
                  (reset! *listeners {})
                  (.disconnect port)))
        request! (fn [method params]
                   (js/Promise.
                    (fn [resolve reject]
                      (if @*stopped?
                        (reject (js/Error. "Editor disconnected"))
                        (let [id (swap! *next-id inc)
                              timeout (js/setTimeout
                                       #(when-let [entry (get @*pending id)]
                                          (swap! *pending dissoc id)
                                          ((:reject entry) (js/Error. (str "RPC timeout: " method)))) 35000)]
                          (swap! *pending assoc id {:resolve resolve :reject reject :timeout timeout})
                          (send! #js {:op "request" :id id :method method :params params}))))))]
    (.addListener (.-onMessage port)
      (fn [^js message]
        (case (.-op message)
          "ready" ((first @*ready) true)
          "redraw" (on-redraw (.-value message))
          "status" (on-status (.-value message))
          "exit" (on-exit (.-value message))
          "failure" (do ((second @*ready) (js/Error. (.-value message))) (on-failure (.-value message)))
          "notification" (doseq [callback (get @*listeners (.-method message))] (callback (.-value message)))
          "result" (when-let [{:keys [resolve reject timeout]} (get @*pending (.-id message))]
                     (js/clearTimeout timeout)
                     (swap! *pending dissoc (.-id message))
                     (if-let [error (.-error message)] (reject (js/Error. error)) (resolve (.-value message))))
          nil)))
    (.addListener (.-onDisconnect port)
      (fn [] (when-not @*stopped? (on-failure "Extension connection lost; the original textarea is available.")) (stop!)))
    (send! #js {:op "start"})
    #js {:ready ready :request request! :stop stop!
         :registerTypes (fn [types] (send! #js {:op "types" :value types}))
         :listen (fn [method callback]
                   (swap! *listeners update method (fnil conj #{}) callback)
                   (send! #js {:op "listen" :method method})
                   #(swap! *listeners update method disj callback))}))
