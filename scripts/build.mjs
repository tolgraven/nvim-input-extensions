import {cp, mkdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
await mkdir('dist/chrome', {recursive: true});
await cp('resources', 'dist/chrome', {recursive: true});
await cp('vendor/neovim', 'dist/chrome/vendor/neovim', {recursive: true});
const result = spawnSync('npx', ['--no-install', 'shadow-cljs', 'release', 'content', 'frame', 'background', 'offscreen', 'options'], {stdio: 'inherit'});
if (result.status) process.exit(result.status);
console.log('Load dist/chrome as an unpacked extension.');
