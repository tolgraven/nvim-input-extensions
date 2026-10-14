import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, stat} from 'node:fs/promises';

test('unpacked extension contains its declared entry points and local WASM', async () => {
  const root = 'dist/chrome/';
  const manifest = JSON.parse(await readFile(root + 'manifest.json'));
  assert.equal(manifest.manifest_version, 3);
  assert.deepEqual(manifest.permissions, ['storage', 'offscreen']);
  for (const file of [manifest.background.service_worker, ...manifest.content_scripts.flatMap(s => s.js),
    manifest.options_ui.page, 'editor.html', 'editor.lua', 'js/frame/main.js', 'offscreen.html', 'js/offscreen/main.js', 'js/options/main.js',
    'vendor/neovim/nvim.wasm', 'vendor/neovim/nvim.data', 'vendor/neovim/profile.json', 'vendor/neovim/workspace.json']) {
    assert.ok((await stat(root + file)).size > 0, file);
  }
  assert.equal(manifest.cross_origin_embedder_policy.value, 'require-corp');
  assert.ok(manifest.content_security_policy.extension_pages.includes("'wasm-unsafe-eval'"));
  assert.ok(!manifest.web_accessible_resources.flatMap(r => r.resources).includes('offscreen.html'));
});
