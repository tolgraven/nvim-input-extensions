// Chrome's offscreen API has no Safari equivalent. Do not package a broken build.
console.error('Safari runtime support is pending. The inline Reagent UI and textarea adapter are shared; an isolated worker host for Safari is still required. See docs/architecture.md.');
process.exitCode = 1;
