# Inline Neovim and native forms

The content script mounts the imported Reagent component inside an inline extension
frame immediately after the textarea. The native control is visually clipped only
after Neovim and synchronization are ready. It retains its name, form ownership,
constraints, default value and all existing listeners. The editor starts on first
focus, avoiding a WASM process for every dormant textarea. The regular control is
usable during startup. Use regular textarea restores it for the rest of the visit.

The native textarea keeps its original layout and dimensions while clipped. The
frame is absolutely positioned over its border box, with ResizeObserver and
reconciliation following site layout changes. This also handles an absolute
textarea backed by a hidden sizing element in a flex row, as on Stack Overflow.
The extension's fit-height component option sizes native Neovim rows to the
remaining viewport height. Short fields get a measured minimum of eight native
rows: six editing lines, a status line and a command line. The retained textarea
and, for absolute fields, its containing sizing row reserve that space in page
layout. Original height constraints are restored on unmount. The extra Reagent
mode toolbar is hidden; restart and restore are accessible icons floating over
the right edge on hover, consuming no grid rows. These are extension adaptations
to retain during upstream refreshes.

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

Explicit native writes are stored as separate per-file keys in Chrome's local
extension storage. Each editor materializes them as ordinary files under
`/workspace/files` and uses that directory as its cwd. Storage changes update the
virtual filesystem of live editors and clear Meta's project-file cache; normal
filename completion and Meta project search use that filesystem. File-read hooks
refresh from storage before completing asynchronous reads, including when another
editor has just saved. Page buffers are not automatically written. Native
BufEnter, line and reload notifications switch synchronization to the active file;
special buffers used by Meta remain outside the textarea synchronization owner.
The redundant airline tabline is disabled. These are extension adapters to retain
when refreshing the upstream component.

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
