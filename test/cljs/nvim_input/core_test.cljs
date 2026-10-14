(ns nvim-input.core-test
  (:require [cljs.test :refer [deftest is testing async]]
            [nvim-input.config :as config]
            [nvim-input.textarea :as textarea]
            [nvim-input.virtual-files :as virtual-files]
            ["jsdom" :refer [JSDOM]]))

(defn document! []
  (let [dom (JSDOM. "<!doctype html><form id='form'><textarea id='text' name='body'>original</textarea></form>"
                   #js {:url "https://example.com/"})
        window (.-window dom)]
    (set! js/global.window window)
    (set! js/global.document (.-document window))
    (set! js/global.InputEvent (.-InputEvent window))
    (set! js/global.Event (.-Event window))
    (.-document window)))

(deftest hostname-blacklist
  (is (config/blocked? ["example.com"] "sub.example.com"))
  (is (config/blocked? ["*.example.com"] "EXAMPLE.com."))
  (is (not (config/blocked? ["example.com"] "notexample.com")))
  (is (= ["example.com" "localhost"] (config/parse-rules " Example.com\n\nlocalhost\nexample.com")))
  (is (config/valid-rule? "*.example.com"))
  (is (not (config/valid-rule? "https://example.com/path")))
  (is (not (config/allowed? {:enabled false :blacklist []} "example.com"))))

(deftest fancy-detection
  (let [doc (document!) field (.getElementById doc "text")]
    (is (not (textarea/fancy? field)))
    (.setAttribute (.-parentElement field) "class" "CodeMirror")
    (is (textarea/fancy? field))
    (.removeAttribute (.-parentElement field) "class")
    (.setAttribute field "aria-hidden" "true")
    (is (textarea/fancy? field))
    (.removeAttribute field "aria-hidden")
    (let [iframe (.createElement doc "iframe")]
      (set! (.-id iframe) "text_ifr")
      (.append (.-body doc) iframe)
      (is (textarea/fancy? field)))))

(deftest native-form-integration
  (let [doc (document!) field (.getElementById doc "text")
        window (.-defaultView doc) events (atom [])]
    (.addEventListener field "input" #(swap! events conj (.-type %)))
    (.addEventListener field "change" #(swap! events conj (.-type %)))
    ;; A controlled framework's tracker overrides the instance setter.
    (js/Object.defineProperty field "value"
      #js {:configurable true
           :get (fn [] (.call (.-get (js/Object.getOwnPropertyDescriptor (.-prototype (.-HTMLTextAreaElement window)) "value")) field))
           :set (fn [_] (throw (js/Error. "Framework tracker must be bypassed")))})
    (textarea/input! field "λ\nlast\n")
    (is (= "λ\nlast\n" (.get (js/Reflect.construct (.-FormData window) #js [(.getElementById doc "form")]) "body")))
    (textarea/input! field "λ\nlast\n")
    (is (= ["input"] @events))
    (textarea/change! field)
    (is (= ["input" "change"] @events))
    (.reset (.getElementById doc "form"))
    (is (= "original" (textarea/text field)))))

(deftest virtual-file-paths
  (is (= "notes.txt" (virtual-files/relative-path "/workspace/files/notes.txt")))
  (is (= "drafts/λ.txt" (virtual-files/relative-path "/workspace/files/drafts/λ.txt")))
  (doseq [path ["/workspace/files-other/note" "/tmp/note" "/workspace/files/../note"
                "/workspace/files/" "/workspace/files/a\\b" "/workspace/files/a/./b"]]
    (is (nil? (virtual-files/relative-path path)))))
