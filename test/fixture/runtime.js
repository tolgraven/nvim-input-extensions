// Only for the HTTP fixture: simulate extension messaging, not Neovim or DOM behavior.
(() => {
  if (window.parent !== window && window.parent.chrome?.runtime?.id === "fixture") {
    const parentChrome = window.parent.chrome;
    window.chrome = {...parentChrome, runtime: {...parentChrome.runtime,
      connect: options => {
        const port = parentChrome.runtime.connect(options);
        window.addEventListener("pagehide", () => port.disconnect(), {once:true});
        return {...port, onMessage:{addListener: callback => port.onMessage.addListener(message => callback(JSON.parse(JSON.stringify(message))))}};
      }
    }};
    return;
  }
  const event = () => { const listeners = new Set(); return {addListener: f => listeners.add(f), removeListener: f => listeners.delete(f), fire: (...args) => [...listeners].map(f => f(...args))}; };
  const onMessage = event(), onConnect = event(), onChanged = event();
  const settings = {enabled: true, blacklist: []};
  const runtime = {
    id: 'fixture', onMessage, onConnect,
    getURL: path => new URL('/' + path, location.href).href,
    getContexts: () => Promise.resolve([{}]),
    sendMessage: message => new Promise((resolve, reject) => {
      let replied = false;
      const respond = value => { replied = true; resolve(value); };
      const sender = message.target === 'editor' ? {id:'fixture', url:runtime.getURL('offscreen.html')} : {id:'fixture'};
      const results = onMessage.fire(message, sender, respond);
      if (!replied && !results.includes(true)) resolve(null);
    }),
    connect: () => {
      const a = {name:'nvim-editor', onMessage:event(), onDisconnect:event()}, b = {name:'nvim-editor', onMessage:event(), onDisconnect:event()};
      let closed = false;
      a.postMessage = m => queueMicrotask(() => !closed && b.onMessage.fire(m));
      b.postMessage = m => queueMicrotask(() => !closed && a.onMessage.fire(m));
      a.disconnect = b.disconnect = () => {if (!closed) {closed=true; a.onDisconnect.fire(); b.onDisconnect.fire();}};
      onConnect.fire(b); return a;
    }
  };
  window.chrome = {runtime, action:{onClicked:event()}, storage:{onChanged, local:{
    get: defaults => Promise.resolve({...defaults,...settings}),
    set: changes => {const diff={}; for (const [key,newValue] of Object.entries(changes)) {diff[key]={newValue}; settings[key]=newValue;} onChanged.fire(diff,'local'); return Promise.resolve();}
  }}};
})();
