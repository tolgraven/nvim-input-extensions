# Inline Neovim and native forms

The content script mounts the imported Reagent component inside an inline extension
frame immediately after the textarea. The native control is visually clipped only
after Neovim and synchronization are ready. It retains its name, form ownership,
constraints, default value and all existing listeners. The editor starts on first
focus, avoiding a WASM process for every dormant textarea. The regular control is
usable during startup. Use regular textarea restores it for the rest of the visit.

The content adapter and frame are ClojureScript, with Reagent 2 / React 19 for the component and
options UI. Local ratoms project the Neovim redraw state. No re-frame is needed.
The component's model, native keyboard/mouse renderer, profile and transport come
from tolgrAVen. `vendor/provenance.json` records source hashes and revision. The
standalone copy changes namespace/imports, replaces site macros with upstream
Reagent defc/hooks, and exposes lifecycle callbacks to the textarea adapter. Tab
traverses the surrounding form. Site repository browsing controls are hidden. The frame keeps site shortcuts and
other extensions, including Vimium, from consuming Neovim keyboard events.
Frame messages accept only text/lifecycle operations and validate origin, source
and a per-field channel; no native RPC or extension APIs are exposed to websites.

The pinned WASM port blocks in Atomics.wait and needs SharedArrayBuffer. Normal
web pages cannot host it directly, and an extension iframe inherits the embedding
page's lack of isolation. The visible component therefore stays in the page,
while an extension offscreen document owns each native worker and its MessagePack
transport. Runtime messages carry requests, responses and redraw events; shared
memory stays in that isolated document. The service worker routes messages and
terminates each native worker when its editor port disconnects. The extension
ships all executable code and runtime data. No HTTP service or native host runs.

The native buffer emits text notifications into a frame-local dummy textarea.
The frame forwards edits to the content adapter, which calls the native textarea
value setter followed by a bubbling input event, bypassing framework value trackers.
Change fires when leaving the editor. A 100 ms poll observes programmatic writes
and resets, including writes that do not dispatch input. Submit waits for queued
RPC and a final buffer read before re-dispatching through requestSubmit.

Plain textareas are considered on focus, including dynamically added controls and
permitted HTTP(S) frames. Hidden, disabled, readonly, contenteditable and known
CodeMirror, Monaco, Ace, TinyMCE, CKEditor, Quill, ProseMirror, Slate and Lexical
backing controls are skipped. Detection is heuristic: unknown rich editors should
be added to the site blacklist. Removal, disabling, rich-editor takeover or config
changes unmount the component and release its worker. Closed shadow roots and
browser/store pages are outside content-script reach.

## Safari

Safari packaging is deferred. Two separate requirements remain: verify the current
Neovim binary in Safari, and supply an isolated persistent worker host without
Chrome's offscreen API. The source and native-control adapter are portable, but
converting the manifest alone cannot provide either requirement. `npm run safari`
currently reports this explicitly. Do not claim Safari support from Chrome tests.

## Platform documentation

- https://developer.chrome.com/docs/extensions/develop/concepts/cross-origin-isolation
- https://developer.chrome.com/docs/extensions/reference/api/offscreen
- https://developer.chrome.com/docs/extensions/develop/concepts/messaging
- https://developer.apple.com/documentation/safariservices/safari-web-extensions
