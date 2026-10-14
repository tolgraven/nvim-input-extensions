(ns nvim-input.textarea
  "The native textarea remains the form control. Neovim is its disposable editor."
  (:require [clojure.string :as string]
            [nvim-input.neovim.runtime :as runtime]))

(def rich-selector
  (string/join "," ["[contenteditable]:not([contenteditable='false'])" "[role='combobox']"
                     ".CodeMirror" ".cm-editor" ".monaco-editor" ".ace_editor"
                     ".tox" ".tox-tinymce" ".mce-tinymce" ".ck-editor" ".cke"
                     ".ql-container" ".ProseMirror" ".tiptap" "[data-lexical-editor]"
                     "[data-slate-editor]" "[data-nvim-input='off']"]))

(defn fancy? [element]
  (or (.closest element rich-selector)
      (.matches element ".cm-textarea,.ace_text-input,[aria-hidden='true'],[role='combobox']")
      ;; Several WYSIWYG libraries hide a source textarea next to their visible editor.
      (when-let [id (not-empty (.-id element))]
        (some? (.getElementById (.-ownerDocument element) (str id "_ifr"))))))

(defn eligible? [element]
  (and (= "TEXTAREA" (.-tagName element))
       (.-isConnected element)
       (not (or (.-disabled element) (.-readOnly element) (.-hidden element) (fancy? element)))
       (let [style (.getComputedStyle js/window element)]
         (and (not= "none" (.-display style)) (not= "hidden" (.-visibility style))))
       (pos? (.-length (.getClientRects element)))))

(defn text [element] (.-value element))

(defn set-native! [element value]
  ;; Calling the native setter avoids React's own value tracker swallowing the input.
  (let [prototype (.. element -ownerDocument -defaultView -HTMLTextAreaElement -prototype)
        setter (.-set (js/Object.getOwnPropertyDescriptor prototype "value"))]
    (.call setter element value)))

(defn input! [element value]
  (when (not= value (text element))
    (set-native! element value)
    (.dispatchEvent element (js/InputEvent. "input" #js {:bubbles true :composed true
                                                        :inputType "insertReplacementText" :data nil}))))

(defn change! [element]
  (.dispatchEvent element (js/Event. "change" #js {:bubbles true})))

(def read-lua
  "local b = ...; return table.concat(vim.api.nvim_buf_get_lines(b, 0, -1, true), '\\n')")

(def watch-lua
  "local channel, event = ...
   local b = vim.api.nvim_get_current_buf()
   vim.bo[b].fixendofline = false
   vim.api.nvim_buf_attach(b, false, {
     on_lines = function(_, buf)
       local text = table.concat(vim.api.nvim_buf_get_lines(buf, 0, -1, true), '\\n')
       vim.rpcnotify(channel, event, text)
     end
   })
   return b")

(defn replace-buffer! [session buffer value]
  ;; Stage bounded UTF-8 packets, then update the buffer atomically. Using Unicode
  ;; codepoints keeps surrogate pairs intact across the transport's 64 KiB ring.
  (let [token (str (random-uuid))
        chunks (partition-all 2048 (array-seq (js/Array.from value)))]
    (-> (reduce
         (fn [pending chunk]
           (.then pending
             #(runtime/request! session "nvim_exec_lua"
                ["local key, part = ...; _G.nvim_input_pending = _G.nvim_input_pending or {}; local parts = _G.nvim_input_pending[key] or {}; table.insert(parts, part); _G.nvim_input_pending[key] = parts"
                 [token (apply str chunk)]])))
         (js/Promise.resolve nil) chunks)
        (.then #(runtime/request! session "nvim_exec_lua"
                  ["local b, key = ...; local parts = (_G.nvim_input_pending or {})[key] or {}; local text = table.concat(parts); if _G.nvim_input_pending then _G.nvim_input_pending[key] = nil end; vim.api.nvim_buf_set_lines(b, 0, -1, true, vim.split(text, '\\n', {plain=true}))"
                   [buffer token]])))))

(defn sync!
  "Acquire continuous native buffer notifications and observe site-driven values.
   Returns a promise of {:stop! :flush!}; all callbacks belong to this acquisition."
  [^js session element on-ready! fail!]
  (let [event (str "nvim-input-" (random-uuid))
        *active? (atom true)
        *applying? (atom false)
        *last (atom (text element))
        *queue (atom (js/Promise.resolve nil))
        *timer (atom nil)
        *buffer (atom nil)
        release! (.listen session event
                   (fn [payload]
                     (when (and @*active? (not @*applying?) (= @*last (text element)))
                       (let [value (aget payload 0)
                             limit (.-maxLength element)
                             native-value (if (neg? limit) value (.slice value 0 limit))]
                         (reset! *last value)
                         (input! element native-value)))))
        pull! (fn []
                (when (and @*active? @*buffer (not= @*last (text element)))
                  (let [value (text element)]
                    (reset! *last value)
                    (swap! *queue
                      (fn [pending]
                        (-> pending
                            (.then (fn []
                                     (when @*active?
                                       (reset! *applying? true)
                                       (replace-buffer! session @*buffer value))))
                            (.finally #(reset! *applying? false))
                            (.catch #(when @*active? (fail! %)))))))))
        stop! (fn [] (reset! *active? false) (js/clearInterval @*timer) (release!))]
    (-> (runtime/request! session "nvim_get_api_info" [])
        (.then #(runtime/request! session "nvim_exec_lua" [watch-lua [(aget % 0) event]]))
        (.then (fn [buffer]
                 (when @*active?
                   (reset! *buffer buffer)
                   (reset! *applying? true)
                   (let [initial (text element)]
                   (-> (replace-buffer! session buffer initial)
                       (.then (fn []
                                (reset! *applying? false)
                                (reset! *last initial)
                                (when @*active?
                                  (reset! *timer (js/setInterval pull! 100))
                                  (on-ready!)))))))))
        (.then (fn [] {:stop! stop! :pull! pull!
                       :flush! (fn flush! []
                                 (pull!)
                                 (-> @*queue
                                     (.then #(runtime/request! session "nvim_exec_lua" [read-lua [@*buffer]]))
                                     (.then (fn [value]
                                              (when @*active?
                                                (if (not= @*last (text element))
                                                  (flush!)
                                                  (do (reset! *last value) (input! element value))))))))}))
        (.catch (fn [error] (stop!) (throw error))))))
