# Neovim Input Extensions

Edit ordinary textareas **in the page, in place**, using the Neovim WASM
Reagent component from [tolgrAVen](https://github.com/tolgraven/tolgrAVen).
ClojureScript, Reagent 2, React 19 and Shadow CLJS. No re-frame or native host.

The upstream component comes from the merged [tolgrAVen PR #173](https://github.com/tolgraven/tolgrAVen/pull/173),
pinned to merge commit `df3451086f912d9a97439fa2bc30f7c79fed2c4d`.
The extension retains its compact inline controls, measured textarea layout,
synchronization and shared-file persistence when refreshing that component.

Chrome is the first implementation. Safari is pending a compatible isolated
worker host and verification of the current Neovim WASM binary.

## Chrome

Build, open `chrome://extensions`, enable Developer mode, choose **Load unpacked**,
and select `dist/chrome` from this repository. Focus an ordinary textarea to start
its editor. The textarea stays usable during startup; Neovim then takes its place.
Hover the editor to reveal restart and regular-textarea icons on its right edge.
Their descriptions appear on hover; both controls are also keyboard accessible.
The regular-textarea icon restores that control for the remainder of the visit.

The inline editor frame isolates Neovim's keys from the site's keyboard handlers
and extensions such as Vimium. The original textarea remains the form control:
its measured width, height and position are retained as the page layout changes;
short fields grow to fit at least six editing lines, plus Neovim's status and
command lines. The extra title row and bottom control rows are absent. Switching
back restores the original minimum/maximum height and surrounding sizing row;
its name, native form ownership, validation and default value are retained. Buffer
edits emit native input events; leaving the editor emits change. Application
writes and resets update the buffer, and submit waits for a final synchronization.
Unicode and final newlines are preserved, with chunked writes beyond the native
transport's packet limit.

The toolbar action opens settings. Disable the extension or add hostnames to the
blacklist, one per line. An entry includes its subdomains. Settings changes
restore affected controls on already-open pages. Settings use local extension
storage. Page drafts are not saved automatically or sent to a service.

Use `:w notes.txt` to persist a virtual file locally, then `:e notes.txt` from any
other textarea editor to recall it. `:e <Tab>` uses ordinary filename completion.
Every editor uses `/workspace/files` as its working directory, with saved files
materialized in its virtual filesystem, so `:Meta!` searches the shared project.
The initial filename is derived from the site, URL path and textarea ID (falling
back to its name, sorted classes and occurrence among matching fields). A short
hash distinguishes full URL/query and field identities; URL fragments are ignored.
Plain `:w` saves that field's draft. Returning to the same URL and focusing the
same textarea restores the saved contents into both Neovim and the site's actual
control, even when the site supplied different default text. Edits made while
the editor is starting take priority. Restart keeps unsaved edits; revisiting
restores the last explicit save. `:w notes.txt` also remains available for shared
named files. Saved files survive editor/page/browser restarts, including files
explicitly saved in private windows. Existing editors receive filesystem updates;
use `:e!` to reread a file already open elsewhere. Concurrent writes to the same
name use the last saved value. Each file is limited to 4 MiB and Chrome's total
local extension-storage quota applies. These are virtual files, not host files.
Earlier saves under the generic name `textarea` remain available via `:e textarea`.

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
The fixture captures its own textarea focus events so an installed extension does
not also claim its controls. Click **Run integration checks**. The fixture uses only a local messaging
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
