(ns nvim-input.listener)

(defn listen! [_ bindings]
  (let [cleanups (mapv (fn [{:keys [target event handler passive?]}]
                         (let [target (case target "window" js/window "document" js/document target)
                               options #js {:passive (boolean passive?)}]
                           (.addEventListener target event handler options)
                           #(.removeEventListener target event handler options))) bindings)]
    #(doseq [cleanup! cleanups] (cleanup!))))
