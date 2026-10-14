# Verification

## Build and focused checks

On 2026-10-14, release builds of content, frame, background, offscreen and options
completed with zero warnings through the Mac's required CPU launcher (1.5 Docker
CPUs, 4 GiB memory, 2 GiB heap). The browser fixture and Node test target also built.

`npm run check` passed: three ClojureScript tests / 15 assertions, the real
MessagePack transport regression checks, and extension entry-point/local-runtime
artifact checks. These cover hostname boundaries, rich-editor detection, native
textarea setters with a framework value tracker, input/change events, FormData,
form reset, fragmented RPC, input-queue bounds and session cleanup.

## Real WASM browser fixture

The compiled Reagent component ran against the real pinned Neovim binary in the
existing Chrome, with only the extension messaging boundary simulated locally.
The nine integration checks passed for initialization, native buffer edits,
application writes larger than one transport packet, Unicode across a chunk
boundary, trailing newlines, submission, reset, real Reagent controlled fields,
rich/readonly fields, dynamic controls, worker disposal and live blacklist cleanup.
The fixture transport copies messages into the receiving frame's realm and closes
ports on frame unload, matching extension messaging semantics.

The final follow-up adds explicit startup focus delivery into the inline frame,
change-event deduplication, restart-safe style restoration, and requestSubmit
without a submitter. Build/unit checks passed. The final installed private-window check verified startup
focus, change-event deduplication, restart with retained content, and complete
restoration of the native control. The expanded automated fixture rerun was
interrupted by another browser disconnection; the earlier nine-check run passed.

## Installed unpacked Chrome extension

The unpacked extension was loaded from `dist/chrome` into the existing installed
Chrome with user authorization for a temporary test. On `/native.html`:

- The host page reported `crossOriginIsolated=false` and had no fixture adapter.
- The extension-owned inline frame rendered the real native Neovim grid.
- Keyboard insertion changed the retained native textarea and emitted input.
- Escape reached Neovim normal mode inside the frame with Vimium installed.
- Submission delivered the exact edited native value through FormData.
- Programmatic writes, Unicode and trailing newlines reached the visible grid.
- Saving the host blacklist through the real settings UI removed the frame and
  restored the native styles/tabindex without changing the textarea value.
- The inspected native page had no warning/error console messages.

The initial installed test exposed a startup focus handoff issue; the final build
sends an explicit focus message into the editor frame. It was reloaded in Chrome.

A final check ran in an actual Chrome Incognito window, with only Neovim Input
shown among the enabled extension toolbar actions. Dark Reader, Vimium and the
other regular extensions were absent. The manifest uses split incognito contexts.
The host remained non-isolated and had no fixture adapter. Startup automatically
focused the grid; pressing i, X and Escape produced `XInitial native value`,
normal mode and one native input event. Submit delivered that exact value and
emitted change. Restart preserved the buffer. Use regular textarea removed the
frame, restored the original style/tabindex, and retained the edited value.
The private native page had no warning/error console messages.

The temporary test installation was removed through Chrome's extension manager;
the Neovim Input card and toolbar action disappeared. Chrome disconnected again
before the expanded automated fixture rerun finished. The installed extension
must be disabled or removed before running the HTTP fixture, otherwise the real
adapter and the fixture's adapter both try to claim the same fields. That setup
mistake produced a recorded startup-timeout warning on `/`; the installed native
page checks were clear.

GitHub Actions successfully built, checked and packaged implementation commit
`2a6c6d9`: https://github.com/tolgraven/nvim-input-extensions/actions/runs/38618027638

## Limits

No store submission, signing or Safari runtime test has been performed. Safari
needs an isolated worker-host implementation in addition to WASM compatibility.
The current pinned binary is experimental; arbitrary rich-editor heuristics,
screen-reader parity, native clipboard integration and IME behavior need further
site/device coverage. The original control remains available on startup failure.
