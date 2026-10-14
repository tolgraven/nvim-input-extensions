(ns nvim-input.virtual-files
  "Explicit :write files shared by extension editors, separate from page drafts."
  (:require [clojure.string :as string]
            [nvim-input.neovim.files :as files]
            [nvim-input.neovim.runtime :as runtime]))

(def root "/workspace/files/")
(def key-prefix "nvim-file:")

(defn relative-path [path]
  (when (and (string? path) (string/starts-with? path root))
    (let [relative (subs path (count root))]
      (when (and (<= (count relative) 1024) (files/safe-path? relative)) relative))))

(defn stored-file [key text]
  (when (and (string/starts-with? key key-prefix) (string? text))
    (when-let [path (relative-path (str root (subs key (count key-prefix))))]
      {:path (str "files/" path) :file (js/Blob. #js [text])})))

(defn start! [^js session filename fail!]
  (let [request! #(runtime/request! session %1 %2)
        filename (or (relative-path (str root filename)) "textarea")
        *active? (atom true)
        *queue (atom (js/Promise.resolve nil))
        read-event (str "nvim-file-read-" (random-uuid))
        write! (fn [descriptor]
                 (swap! *queue
                   (fn [pending]
                     (-> pending
                         (.then #(when @*active? (files/write! request! descriptor)))
                         (.then #(when @*active?
                                   (request! "nvim_exec_lua" ["vim.g.__meta_project_file_list_cache = nil" []])))
                         (.catch #(when @*active? (fail! %)))))))
        release-read! (.listen session read-event
                        (fn [payload]
                          (let [[path buffer] (js->clj payload)]
                            (when-let [relative (relative-path path)]
                              (let [key (str key-prefix relative)]
                                (-> (.get js/chrome.storage.local key)
                                    (.then (fn [stored]
                                             (when-let [descriptor (stored-file key (aget stored key))]
                                               (write! descriptor))))
                                    (.then #(when @*active?
                                              (request! "nvim_exec_lua"
                                                ["local buf = ...; if vim.api.nvim_buf_is_valid(buf) then
                                                    vim.api.nvim_buf_call(buf, function() vim.cmd('noautocmd edit!') end)
                                                    vim.b[buf].nvim_input_loading = false
                                                    vim.api.nvim_exec_autocmds('BufReadPost', {buffer = buf})
                                                  end" [buffer]])))
                                    (.catch #(when @*active? (fail! %)))))))))
        changed! (fn [changes area]
                   (when (and @*active? (= area "local"))
                     (doseq [key (array-seq (js/Object.keys changes))]
                       (when-let [descriptor (stored-file key (.-newValue (aget changes key)))]
                         (write! descriptor)))))
        writes (runtime/watch-writes! session
                   (fn [{:keys [path text]}]
                     (if-let [relative (relative-path path)]
                       (if (<= (.-length (.encode (js/TextEncoder.) text)) files/max-file-bytes)
                         (-> (.set js/chrome.storage.local (clj->js {(str key-prefix relative) text}))
                             (.catch fail!))
                         (fail! (js/Error. "Virtual files are limited to 4 MiB each.")))
                       (fail! (js/Error. "Save shared files under /workspace/files/ (the default directory).")))) fail!)
        stop! #(do (reset! *active? false)
                   (.removeListener js/chrome.storage.onChanged changed!)
                   (release-read!)
                   (-> (request! "nvim_exec_lua"
                         ["local group = ...; pcall(vim.api.nvim_del_augroup_by_name, group)" [read-event]])
                       (.catch (fn [_] nil)))
                   ((:stop! writes)))]
    (.addListener js/chrome.storage.onChanged changed!)
    (-> (request! "nvim_exec_lua"
          ["local filename = ...
            vim.fn.mkdir('/workspace/files', 'p')
            vim.cmd('cd /workspace/files')
            vim.api.nvim_buf_set_name(0, '/workspace/files/' .. filename)
            vim.o.autowrite = false
            vim.o.autowriteall = false
            vim.g['airline#extensions#tabline#enabled'] = 0
            vim.o.showtabline = 0" [filename]])
        (.then #(request! "nvim_get_api_info" []))
        (.then (fn [info]
                 (request! "nvim_exec_lua"
                   ["local channel, event = ...
                     vim.api.nvim_create_autocmd({'BufReadCmd', 'BufNewFile'}, {
                       pattern = '/workspace/files/*',
                       group = vim.api.nvim_create_augroup(event, {clear = true}),
                       callback = function(args)
                         vim.b[args.buf].nvim_input_loading = true
                         vim.rpcnotify(channel, event, vim.fn.fnamemodify(args.file, ':p'), args.buf)
                       end})" [(aget info 0) read-event]])))
        (.then #(-> (:ready writes) (.then (fn [_] (.get js/chrome.storage.local nil)))))
        (.then (fn [stored]
                 (doseq [key (array-seq (js/Object.keys stored))]
                   (when-let [descriptor (stored-file key (aget stored key))]
                     (write! descriptor)))
                 (let [saved-text (aget stored (str key-prefix filename))]
                   (-> @*queue
                       (.then #(when (string? saved-text)
                                 (request! "nvim_exec_lua"
                                   ["local path = ...; vim.cmd('silent noautocmd edit! ' .. vim.fn.fnameescape(path))"
                                    [(str root filename)]])))
                       (.then (fn [] {:stop! stop! :saved-text saved-text}))))))
        (.catch (fn [error] (stop!) (throw error))))))
