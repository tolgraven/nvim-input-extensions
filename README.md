# Neovim Input Extensions

Edit ordinary textareas **in the page, in place**, using the Neovim WASM
Reagent component from [tolgrAVen](https://github.com/tolgraven/tolgrAVen).
ClojureScript, Reagent 2, React 19 and Shadow CLJS. No re-frame or native host.

Chrome is the first implementation. Safari is pending a compatible isolated
worker host and verification of the current Neovim WASM binary.

## Chrome

Build, open `chrome://extensions`, enable Developer mode, choose **Load unpacked**,
and select `dist/chrome` from this repository. Focus an ordinary textarea to start
its editor. The textarea stays usable during startup; Neovim then takes its place.
Use **Use regular textarea** to restore that control for the remainder of the visit.

The inline editor frame isolates Neovim's keys from the site's keyboard handlers
and extensions such as Vimium. The original textarea remains the form control:
its name, native form ownership, validation and default value are retained. Buffer
edits emit native input events; leaving the editor emits change. Application
writes and resets update the buffer, and submit waits for a final synchronization.
Unicode and final newlines are preserved, with chunked writes beyond the native
transport's packet limit.

The toolbar action opens settings. Disable the extension or add hostnames to the
blacklist, one per line. An entry includes its subdomains. Settings changes
restore affected controls on already-open pages. Settings use local extension
storage; textarea contents are never stored or sent to a service.

Hidden, disabled, readonly and known rich-editor backing fields are skipped.
Detection covers CodeMirror, Monaco, Ace, TinyMCE, CKEditor, Quill, ProseMirror,
Slate and Lexical markers. Unknown custom editors may need a blacklist entry.
Browser internal pages and extension-store pages cannot be replaced. A site that
blocks the embedded frame keeps its original textarea. The experimental Neovim
port and browser APIs do not provide every native textarea accessibility or OS
clipboard behavior; verify the sites you use before relying on it for long drafts.

## Build and test

Use Node 22.12+ and Java 21+. Install dependencies with `npm ci`, then:

```sh
npm run check
npm run package
```

`npm run check` releases the Chrome targets sequentially, compiles/runs the focused
ClojureScript tests, tests the actual MessagePack transport and checks the packaged
entry points. `npm test` reruns the already compiled unit/transport/package checks.
`artifacts/nvim-input-chrome.zip` contains the unpacked extension.

On this Mac, run **all Java/Shadow work through the required budgeted launcher**:

```sh
/Users/tol/.local/bin/tolgraven-cpu run -- npm run check
/Users/tol/.local/bin/tolgraven-cpu run -- npm run fixture:build
```

The launcher prints its isolated output path under `.local-wip/cpu-builds/`.
Copy that build's `dist/` into this checkout's `dist/` before loading Chrome or
serving the fixture. It deliberately leaves other sessions' workers/output alone.

For real-WASM browser regression checks, build `npm run fixture:build`, run
`npm run fixture`, then open `http://127.0.0.1:4273/` in the existing Chrome.
Run `runIntegrationChecks()` in that page. The fixture uses only a local messaging
adapter; it does **not** establish installed-extension support. The separate
`http://127.0.0.1:4273/native.html` page has no isolation headers and no adapter,
and is for testing the installed unpacked extension.

The GitHub workflow builds and packages Chrome. Browser/installed-extension
verification is documented separately in [docs/verification.md](docs/verification.md).

## Component and runtime

[docs/architecture.md](docs/architecture.md) describes the inline frame, retained
control, shared-memory worker isolation, messaging and Safari boundary.
[Vendor provenance](vendor/provenance.json) records the source revision/hashes.
The Neovim runtime, plugins, font and MessagePack codec retain their bundled
license notices. No tolgrAVen site source bundle is included.
