# Verification

## Version 0.1.5: first-line and caret border clearance

The reported Stack Overflow screenshot showed the insert caret intersecting the
blue focus outline at the top of the grid. The private fixture reproduced a
one-pixel first-row inset, zero viewport scroll offset and only 1.8125 pixels
below the final row. The old focus outline was drawn inward over that grid.

The editor now has a quarter-rem inset, and focus colours its outer border
instead. The measured minimum height includes both vertical padding and borders,
preserving six editing lines plus the native status and command rows.

The installed private test exposed a second issue on empty lines: insert mode
inherited NonText's dim foreground for its caret. The rendered colours were
rgb(45,48,48) against rgb(42,41,43), only 1.09:1 contrast. The extension now gives
insert mode the profile's native Cursor highlight. The browser regression also
requires a visible caret width and at least 3:1 contrast against its cell.

All release targets, unit tests and the browser fixture compiled without warnings
through the required 1.5-CPU Docker launcher. Six CLJS tests / 34 assertions,
transport checks and artifact checks passed.

In private Chrome at 100% zoom, the expanded layout checks passed for ordinary,
empty and absolute flex fields. Each check verified first/last-row clearance,
normal and insert caret containment, absence of a grid outline, eight fitted
rows, matching frame/native bounds, hidden headers, floating controls and exact
restoration of the original size constraints. The final run also passed the
insert-caret contrast check on the initially empty field.

Chrome was reloaded with the final 0.1.5 assets, enabled from this checkout's
`dist/chrome` with Allow in Incognito retained. On the installed private native
page `/native.html?empty&short&test=015`, the focused empty field showed a bright
vertical insert bar entirely below the outer focus border, with six editing
lines, hidden extra headers and floating controls. The extension was left loaded
for user testing.

## Version 0.1.4: startup mode, airline and mouse scrolling

All release targets, unit tests and the browser fixture compiled with zero
warnings through the required 1.5-CPU Docker launcher. Six CLJS tests / 34
assertions, transport checks, artifact checks and Chrome packaging passed.

Private Chrome's real-WASM editor checks passed for insert/normal startup modes,
middle-truncated filenames at 100, 60 and 40 columns, Unicode display-width
handling, and the hidden airline filetype section with the actual filetype still
set to text. Native wheel input reached EOF without overscrolling, retained
insert/normal mode, scrolled upward again, and kept short buffers at the top.

All thirteen textarea integration checks passed, including an empty textarea
accepting its first key directly in insert mode, nonempty normal-mode startup,
controlled fields, exact file persistence, completion/Meta, layout, disposal,
restart and saved-draft recovery. Both compact-layout checks passed: eight fitted
native rows, matching bounds, no extra title/tabline, floating labelled controls
and restoration of the original height constraints.

The installed extension was reloaded to 0.1.4, enabled from this checkout's
`dist/chrome`, retaining Allow in Incognito. On the private native fixture
`/native.html?empty&short&test=014`, the empty field displayed INSERT and the
middle-truncated URL/field filename without the filetype label. Typing X without
an insert command updated the actual textarea, and the real form submitted X.
Downward wheel input left that short buffer visible at the top.
The second, nonempty field opened in NORMAL. After plain `:w` saved X, reloading
the page supplied an empty server field again; focusing it restored X and opened
in NORMAL, confirming that saved contents take precedence in mode selection.
The updated extension was left installed and enabled for user testing.

## Version 0.1.3: merged upstream component

The imported component now tracks PR #173's merge commit,
`df3451086f912d9a97439fa2bc30f7c79fed2c4d`. Source hashes were refreshed against
that immutable commit. The WASM binary, data, worker and plugin profile match the
merged source. The extension bridge, toolbar hiding, floating controls, minimum
rows, shared filesystem and textarea synchronization remain explicit adapters.

All release targets and the browser fixture compiled with zero warnings through
the required 1.5-CPU Docker launcher. Five CLJS tests / 31 assertions, native
transport checks (including stale cleanup against a replacement listener),
artifact checks and Chrome packaging passed.

In private Chrome, the real-WASM upstream write checks passed for empty text,
Unicode, LF, CRLF, multiple trailing newlines and missing final newlines. The
checks also replaced the write watcher, threw from its consumer and verified that
the error was reported while subsequent native RPC remained usable.

The first full integration run exposed a named-write incompatibility in the
merged watcher: `:w shared-fixture.txt` wrote that file, while the buffer name
remained `textarea`. Reading the latter raised a native BufWritePost error and
prevented persistence. The extension retains its previous absolute write-target
adapter with upstream's new byte reader. The focused write regression now covers
an alternate filename as well as the initial buffer name.

Site-draft filenames use a readable URL/field prefix plus a stable identity hash.
Unit checks cover URL/query separation, fragment stability, IDs surviving class
changes, sorted classes, repeated controls and filename length bounds.

In the installed extension in private Chrome, `/native.html?short&recovery=173`
generated a URL/field filename for `#plain`. Plain `:w` saved a two-line Unicode
draft without a final newline. Reloading the page restored its original server
default first; focusing the field then recovered the exact draft into both the
visible grid and retained textarea. The real form submitted that restored text.

All twelve real-WASM integration checks passed in private Chrome: startup/focus,
edits and native events, large Unicode text and trailing newlines, form submission
and reset, controlled Reagent fields, exact LF/CRLF storage writes, shared-file
recall, filename completion, Meta scratch-buffer exclusion, responsive ordinary
and absolute layouts, rich/readonly detection, dynamic worker disposal, restart
and live blacklist cleanup. The final check saved with plain `:w`, made an
unsaved edit, verified restart retained it, then created a fresh editor with a
different server default and recovered the last saved site draft instead.

The final build also passed both short-field layout checks: eight fitted native
rows, first-row content without a tabline, no external mode title, labelled
floating controls, matching bounds and exact original-height restoration.

Chrome's actual extension reload retained the saved site draft. Revisiting the
native fixture afterward and focusing `#plain` recovered the same exact text
through a new extension worker. The manager showed 0.1.3 enabled from this
checkout's `dist/chrome`, with Allow in Incognito on; it was left installed.

## Version 0.1.2 follow-up

All five release targets, the unit target and the real-WASM fixture compiled
without warnings through the required 1.5-CPU Docker launcher. Four CLJS tests /
23 assertions, transport checks, artifact checks and Chrome packaging passed.

The installed 0.1.2 extension was checked in an actual Chrome Incognito window,
with only Neovim Input in the extension toolbar, at `/native.html?short` on the
IPv6 fixture host. Both fields initially had a three-rem height cap:

- The ordinary field grew to matching native/frame bounds of 966 × 154; the
  absolute flex field grew to matching 768 × 154 bounds. Both displayed six
  editing lines plus the native status and command lines. The absolute field's
  sizing row reserved the extra height, keeping following controls clear.
- The external mode title and bottom button rows were absent. Restart and
  regular-textarea controls appeared as icons over the right edge and disappeared
  when the pointer left the editor. Both have accessible names and hover titles.
- Editing updated the actual textarea. Restart through its icon retained that
  text. Restoring each native control through its icon passed exact style checks
  for the original min/max-height constraints and the containing sizing row.

The standalone real-WASM short-field check passed in private Chrome for both
ordinary and absolute flex fields: at least eight fitted native rows, matching
bounds, no extra title, labelled absolutely positioned controls and exact height
restoration. An earlier normal-window run passed the ordinary field but its
second editor timed out, consistent with the startup timeouts recorded below.

Chrome's extension manager showed 0.1.2 enabled, loaded from this checkout's
`dist/chrome`, with Allow in Incognito on. It was left installed for user testing.

## Version 0.1.1 follow-up

The release targets and fixture compiled without warnings through the required
1.5-CPU Docker launcher. Four CLJS tests / 23 assertions, the native transport
checks and package checks passed.

In the installed extension in an actual Chrome Incognito window, with only
Neovim Input in the extension toolbar:

- Native textarea and inline frame bounds matched at 966 × 166 and 768 × 192.
  Changing the flex layout produced matching 528 × 256 bounds. The native
  textarea retained its original layout, including the absolute textarea plus
  hidden sizing sibling used by Stack Overflow.
- The redundant native tabline was absent. `:w installed-shared.txt` saved the
  first field's text. A second textarea's `:e installed-sh<Tab>` completed the
  native filename, and opening it updated its actual textarea value.
- After reloading the page and creating a fresh editor, the same file reopened
  with its exact contents, demonstrating persistence beyond the old workers.
- From an empty `:enew` buffer in a larger textarea, `:Meta! Initial` found the
  persisted file and displayed its contents and filename in project results.
  The retained native textarea stayed empty while the Meta scratch UI was open.

The installed test used `http://[::1]:4273/native.html`, because the existing
user blacklist includes localhost and 127.0.0.1. Those settings were preserved.

The expanded standalone fixture covers controlled fields, file completion,
shared-file recall and Meta scratch-buffer exclusion. Its repeated full runs
were interrupted by intermittent editor-startup timeouts while several other
browser sessions were active; no full-pass claim is made for that expanded
suite. File-read tracing exposed and fixed premature empty-buffer notifications
and missing reload notifications. The installed private-window flows above
verified the resulting behavior directly. Broad concurrent-editor startup still
needs further coverage with the evolving upstream WASM component.

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
