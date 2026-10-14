(ns nvim-input.editor
  "Native presentation and initial mode for the inline textarea editor."
  (:require [nvim-input.neovim.runtime :as runtime]))

(defn initial-mode [text] (if (empty? text) :insert :normal))

(defn configure! [session]
  (-> (js/fetch (.getURL js/chrome.runtime "editor.lua"))
      (.then (fn [response]
               (if (.-ok response) (.text response)
                   (throw (js/Error. "Could not load inline Neovim settings.")))))
      (.then #(runtime/request! session "nvim_exec_lua" [% []]))))

(defn start-mode! [session text]
  (runtime/request! session "nvim_input" [(if (= :insert (initial-mode text)) "<Esc>i" "<Esc>")]))
