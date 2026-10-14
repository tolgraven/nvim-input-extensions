(ns nvim-input.content
  (:require [nvim-input.config :as config]
            [nvim-input.textarea :as textarea]))

(defonce *config (atom config/defaults))
(defonce *editors (atom {}))
(defonce *bypassed (js/WeakSet.))
(defonce *resubmitting (js/WeakSet.))
(defonce *cleanup (atom nil))
(def native-styles {"clip-path" "inset(50%)" "opacity" "0" "pointer-events" "none"})

(defn style! [element property value]
  (let [style (.-style element)]
    (when (not= value (.getPropertyValue style property))
      (.setProperty style property value "important"))))

(defn size-editor! [element host]
  ;; Keep the native control in its site's layout. In particular, an absolute
  ;; textarea may already have a hidden sizing sibling in a flex container.
  (let [rect (.getBoundingClientRect element)
        parent (.-offsetParent host)
        initial? (or (nil? parent)
                     (and (identical? parent (.-body js/document))
                          (= "static" (.-position (.getComputedStyle js/window parent)))))
        bounds (when-not initial? (.getBoundingClientRect parent))
        scale-x (if (and bounds (pos? (.-offsetWidth parent))) (/ (.-width bounds) (.-offsetWidth parent)) 1)
        scale-y (if (and bounds (pos? (.-offsetHeight parent))) (/ (.-height bounds) (.-offsetHeight parent)) 1)
        left (if initial? (+ (.-left rect) js/window.scrollX)
                 (+ (/ (- (.-left rect) (.-left bounds)) scale-x) (- (.-scrollLeft parent) (.-clientLeft parent))))
        top (if initial? (+ (.-top rect) js/window.scrollY)
                (+ (/ (- (.-top rect) (.-top bounds)) scale-y) (- (.-scrollTop parent) (.-clientTop parent))))
        root-size (js/parseFloat (.-fontSize (.getComputedStyle js/window (.-documentElement js/document))))]
    (when (and (pos? scale-x) (pos? scale-y))
      (doseq [[property value] {"left" left "top" top
                              "width" (/ (.-width rect) scale-x) "height" (/ (.-height rect) scale-y)}]
        (style! host property (str (/ (js/Math.round (* 10000 (/ value root-size))) 10000) "rem"))))))

(defn hide-native! [element]
  (when-not (:native-styles (get @*editors element))
  (let [style (.-style element)
        saved (into {} (map (fn [[key _]]
                             [key [(.getPropertyValue style key) (.getPropertyPriority style key)]]) native-styles))]
    (swap! *editors update element assoc :native-styles saved
           :original-tabindex (.getAttribute element "tabindex"))
    (doseq [[key value] native-styles] (.setProperty style key value "important"))
    (.setAttribute element "tabindex" "-1"))))

(defn focus-editor! [host]
  (.focus host)
  (let [url (js/URL. (.-src host))]
    (.postMessage (.-contentWindow host)
      #js {:op "focus" :channel (subs (.-hash url) 1)}
      (str (.-protocol url) "//" (.-host url)))))

(defn restore! [element]
  (when-let [{:keys [host native-styles original-tabindex sync]} (get @*editors element)]
    (swap! *editors dissoc element)
    (when sync ((:stop! sync)))
    (.remove host)
    (when native-styles
      (doseq [[key [value priority]] native-styles]
        (if (empty? value) (.removeProperty (.-style element) key)
            (.setProperty (.-style element) key value priority)))
      (when (empty? (.getAttribute element "style")) (.removeAttribute element "style"))
      (if original-tabindex (.setAttribute element "tabindex" original-tabindex) (.removeAttribute element "tabindex")))))

(defn bypass! [element]
  (.add *bypassed element)
  (restore! element)
  (.focus element))

(defn attach! [element]
  (when (and (config/allowed? @*config (.-hostname js/location))
             (not (contains? @*editors element))
             (not (.has *bypassed element))
             (textarea/eligible? element))
    (let [host (.createElement js/document "iframe")
          channel (str (random-uuid))
          frame-url (.getURL js/chrome.runtime "editor.html")
          url (js/URL. frame-url)
          origin (str (.-protocol url) "//" (.-host url))
          *ready? (atom false)
          *revision (atom 0)
          *dirty? (atom false)
          *committed (atom (textarea/text element))
          *last (atom (textarea/text element))
          *pending (atom {})
          size! #(size-editor! element host)
          observer (js/ResizeObserver. size!)
          label (or (.getAttribute element "aria-label")
                    (some-> (.-labels element) (aget 0) .-textContent)
                    (not-empty (.-placeholder element)) (not-empty (.-name element)) "Text")
          send! (fn [message]
                  (.postMessage (.-contentWindow host)
                    (js/Object.assign #js {:channel channel} message) origin))
          pull! (fn []
                  (when (not= @*last (textarea/text element))
                    (reset! *last (textarea/text element))
                    (when-not @*dirty? (reset! *committed @*last))
                    (swap! *revision inc)
                    (send! #js {:op "value" :value @*last :revision @*revision})))
          fail! (fn [message]
                  (js/console.warn "Neovim Input restored the textarea:" message)
                  (bypass! element))
          timeout (js/setTimeout #(fail! "Editor startup timed out") 35000)
          receive! (fn [event]
                     (let [^js message (.-data event)]
                       (when (and (some? message) (identical? (.-source event) (.-contentWindow host))
                                  (= origin (.-origin event)) (= channel (.-channel message)))
                         (case (.-op message)
                           "loaded" (send! #js {:op "init" :value (textarea/text element) :revision @*revision
                                                 :label label :rows (max 8 (min 30 (.-rows element)))
                                                 :maxLength (.-maxLength element)})
                           "ready" (do (reset! *ready? true)
                                       (js/clearTimeout timeout)
                                       (pull!)
                                       (hide-native! element)
                                       (size!)
                                       (style! host "opacity" "1")
                                       (style! host "pointer-events" "auto")
                                       (focus-editor! host))
                           "input" (when (string? (.-value message))
                                     ;; A pending application write takes priority over an older editor event.
                                     (when (and (= @*revision (.-revision message)) (= @*last (textarea/text element)))
                                       (when (not= (.-value message) (textarea/text element)) (reset! *dirty? true))
                                       (reset! *last (.-value message))
                                       (textarea/input! element @*last)))
                           "change" (do (when (and @*dirty? (not= @*committed (textarea/text element)))
                                          (textarea/change! element))
                                        (reset! *dirty? false)
                                        (reset! *committed (textarea/text element)))
                           "result" (when-let [{:keys [resolve timeout]} (get @*pending (.-id message))]
                                      (pull!)
                                      (if (= @*revision (.-revision message))
                                        (do (js/clearTimeout timeout)
                                            (swap! *pending dissoc (.-id message))
                                            (resolve true))
                                        (send! #js {:op "flush" :id (.-id message)})))
                           "failure" (fail! (.-value message))
                           "bypass" (bypass! element)
                           nil))))
          timer (js/setInterval pull! 100)
          stop! (fn []
                  (js/clearTimeout timeout)
                  (js/clearInterval timer)
                  (.disconnect observer)
                  (.removeEventListener js/window "message" receive!)
                  (doseq [[_ {:keys [reject timeout]}] @*pending]
                    (js/clearTimeout timeout)
                    (reject (js/Error. "Editor removed")))
                  (reset! *pending {}))
          flush! (fn []
                   (pull!)
                   (if-not @*ready?
                     (js/Promise.resolve nil)
                     (js/Promise.
                      (fn [resolve reject]
                        (let [id (str (random-uuid))
                              timeout (js/setTimeout #(do (swap! *pending dissoc id)
                                                         (reject (js/Error. "Editor flush timed out"))) 35000)]
                          (swap! *pending assoc id {:resolve resolve :reject reject :timeout timeout})
                          (send! #js {:op "flush" :id id}))))))]
      (.setAttribute host "data-nvim-input-host" "")
      (.setAttribute host "title" (str label " — Neovim"))
      (.setAttribute host "src" (str frame-url "#" channel))
      (set! (.. host -style -cssText)
            "all:initial!important;display:block!important;position:absolute!important;box-sizing:border-box!important;border:0!important;margin:0!important;padding:0!important;min-width:0!important;max-width:none!important;min-height:0!important;max-height:none!important;opacity:0!important;pointer-events:none!important;")
      (.addEventListener js/window "message" receive!)
      (swap! *editors assoc element {:host host :sync {:stop! stop! :pull! pull! :flush! flush!}})
      (.insertAdjacentElement element "afterend" host)
      (size!)
      (.observe observer element))))

(defn active-eligible? [element]
  (and (.-isConnected element)
       (not (or (.-disabled element) (.-readOnly element) (.-hidden element) (textarea/fancy? element)))
       ;; Our visually clipped native control remains present for form validation.
       (not= "none" (.-display (.getComputedStyle js/window element)))))

(defn reconcile! []
  (doseq [[element {:keys [host]}] @*editors]
    (if (and (.-isConnected host) (config/allowed? @*config (.-hostname js/location)) (active-eligible? element))
      (size-editor! element host)
      (restore! element))))

(defn submit! [event]
  (let [form (.-target event)
        editors (keep (fn [[element entry]] (when (identical? form (.-form element)) (:sync entry))) @*editors)]
    (when (and (seq editors) (not (.has *resubmitting form)))
      (.preventDefault event)
      (.stopImmediatePropagation event)
      (let [submitter (.-submitter event)]
        (-> (js/Promise.all (into-array (map #((:flush! %)) editors)))
            (.then (fn []
                     (.add *resubmitting form)
                     (try (if submitter (.requestSubmit form submitter) (.requestSubmit form))
                          (finally (.delete *resubmitting form)))))
            (.catch #(js/console.error "Neovim Input could not synchronize submission" %)))))))

(defn start! []
  (let [focus! (fn [event]
                 (let [element (first (filter #(= "TEXTAREA" (.-tagName %)) (array-seq (.composedPath event))))]
                   (when (and element (= "TEXTAREA" (.-tagName element)))
                     (if-let [host (:host (get @*editors element))]
                       (when (:sync (get @*editors element)) (focus-editor! host))
                       (attach! element)))))
        invalid! (fn [event]
                   (when-let [host (:host (get @*editors (.-target event)))]
                     (.preventDefault event)
                     (focus-editor! host)))
        reset! (fn [_] (js/setTimeout #(doseq [[_ {:keys [sync]}] @*editors :when sync] ((:pull! sync))) 0))
        observer (js/MutationObserver. reconcile!)
        timer (js/setInterval reconcile! 500)]
    (.addEventListener js/document "focusin" focus! true)
    (.addEventListener js/document "submit" submit! true)
    (.addEventListener js/document "invalid" invalid! true)
    (.addEventListener js/document "reset" reset! true)
    (.addEventListener js/window "resize" reconcile!)
    (.addEventListener js/document "scroll" reconcile! true)
    (.observe observer (.-documentElement js/document)
      #js {:subtree true :childList true :attributes true :attributeFilter #js ["disabled" "readonly" "hidden" "class" "style" "contenteditable"]})
    (when (= "TEXTAREA" (.-tagName (.-activeElement js/document))) (attach! (.-activeElement js/document)))
    (fn []
      (.disconnect observer)
      (js/clearInterval timer)
      (.removeEventListener js/document "focusin" focus! true)
      (.removeEventListener js/document "submit" submit! true)
      (.removeEventListener js/document "invalid" invalid! true)
      (.removeEventListener js/document "reset" reset! true)
      (.removeEventListener js/window "resize" reconcile!)
      (.removeEventListener js/document "scroll" reconcile! true)
      (doseq [[element _] @*editors] (restore! element)))))

(defn init! []
  (-> (.get js/chrome.storage.local (clj->js config/defaults))
      (.then (fn [settings]
               (reset! *config (js->clj settings :keywordize-keys true))
               (when-let [cleanup! @*cleanup] (cleanup!))
               (reset! *cleanup (start!))))
      (.catch #(js/console.warn "Neovim Input settings unavailable" %)))
  (.addListener js/chrome.storage.onChanged
    (fn [changes area]
      (when (= area "local")
        (doseq [key [:enabled :blacklist] :let [change (aget changes (name key))] :when change]
          (swap! *config assoc key (js->clj (.-newValue change) :keywordize-keys true)))
        (reconcile!)))))
