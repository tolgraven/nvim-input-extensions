# Neovim Input Extensions

Use ClojureScript, Reagent 2 / React 19 and Shadow CLJS. Keep state local; no
re-frame layer. Prefer upstream Reagent defc, ratoms and reagent.hooks. JavaScript
belongs at the native Worker/MessagePack boundary and build/test tooling.

The visible editor mounts inline in the extension frame at the textarea's position. The original
textarea remains the authoritative native form control. Do not move editing to a
popup, tab or side panel. Chrome's offscreen document hosts only the native engine
because SharedArrayBuffer cannot run inside normal website contexts.

Read docs/architecture.md before changing routing or synchronization. Retain
vendor provenance/licenses and the component's renderer/profile. Vendor updates
must record source hashes and explicit adapter changes. Do not modify the source
Neovim worktree from this repository.

Run Java/Shadow builds through `/Users/tol/.local/bin/tolgraven-cpu run -- <command>`
on this Mac. It builds a snapshot under `.local-wip/cpu-builds/<id>`; copy the
verified dist output back for local browser checks. Keep Shadow targets sequential.
Run focused unit/transport/package tests and the real-WASM browser fixture.
Test the installed extension separately before claiming actual extension support.
Safari needs its own isolated worker host and native-WASM validation.
