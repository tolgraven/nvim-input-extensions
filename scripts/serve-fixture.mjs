import http from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
const root = process.cwd();
const types = {'.js':'text/javascript','.html':'text/html','.css':'text/css','.json':'application/json','.wasm':'application/wasm','.woff2':'font/woff2'};
http.createServer(async (request,response) => {
  const url = new URL(request.url,'http://localhost');
  let file = url.pathname;
  if (file === '/') file='/test/fixture/index.html';
  else if (file === '/editor.html') file='/dist/chrome/editor.html';
  else if (file.startsWith('/js/')) file='/dist/chrome'+file;
  else if (file === '/editor.css') file='/dist/chrome/editor.css';
  else if (file === '/native.html') file='/test/fixture/native.html';
  else if (file === '/native.js') file='/test/fixture/native.js';
  else if (file === '/checks.js') file='/test/fixture/checks.js';
  else if (file === '/runtime.js') file='/test/fixture/runtime.js';
  else if (file.startsWith('/test-js/')) file=file.replace('/test-js/','/dist/test/js/');
  else if (file.startsWith('/vendor/')) file='/dist/chrome'+file;
  const target=path.resolve(root,'.'+file);
  if (!target.startsWith(root+path.sep)) {response.writeHead(403).end();return;}
  try {
    let bytes=await readFile(target);
    if (url.pathname === "/editor.html") bytes=Buffer.from(bytes.toString().replace('<script src="js/frame/main.js">','<script src="/runtime.js"></script><script src="js/frame/main.js">'));
    response.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream',...(url.pathname.startsWith('/native') ? {} : {'Cross-Origin-Opener-Policy':'same-origin','Cross-Origin-Embedder-Policy':'require-corp'}),'Cache-Control':'no-store'});
    response.end(bytes);
  } catch {response.writeHead(404).end();}
}).listen(4273,'127.0.0.1',()=>console.log('Fixture: http://127.0.0.1:4273'));
