import {mkdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
await mkdir('artifacts', {recursive: true});
const result = spawnSync('zip', ['-q', '-r', '../../artifacts/nvim-input-chrome.zip', '.'], {cwd: 'dist/chrome', stdio: 'inherit'});
process.exitCode = result.status;
