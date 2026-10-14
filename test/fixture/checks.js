const trace=[];
window.addEventListener('message',event=>{
  const frame=[...document.querySelectorAll('iframe')].find(frame=>frame.contentWindow===event.source);
  if(!frame || !['input','value','ready','failure'].includes(event.data?.op))return;
  trace.push(`${frame.title}: ${event.data.op} rev=${event.data.revision} ${JSON.stringify(event.data.value)?.slice(0,100)}`);
  document.querySelector('#sync-debug').textContent=trace.slice(-16).join('\n');
});
window.runIntegrationChecks = async () => {
  const checks = [];
  const wait = async (predicate, label) => {
    for (let i=0;i<1500;i++) {if(predicate())return;await new Promise(r=>setTimeout(r,20));}
    throw new Error('Timed out: '+label);
  };
  const frameFor = field => field.nextElementSibling;
  const gridFor = field => frameFor(field)?.contentDocument?.querySelector('.neovim__grid');
  const key = (field, value) => gridFor(field).dispatchEvent(new KeyboardEvent('keydown', {key:value,bubbles:true,cancelable:true}));
  const plain = document.querySelector('#plain');
  plain.focus();
  await wait(()=>plain.getAttribute('tabindex')==='-1','inline startup');
  await wait(()=>gridFor(plain)?.textContent.includes('First line'),'initial native value in Neovim');
  if (document.querySelector('#events').textContent.includes('change')) throw Error('Unedited startup fired change');
  await wait(()=>frameFor(plain).contentDocument.activeElement===gridFor(plain),'startup keyboard focus');
  checks.push('Initial native value rendered by real WASM');
  key(plain,'i');key(plain,'X');
  await wait(()=>plain.value==='XFirst line\nSecond line','native buffer notification');
  key(plain,'Escape');
  checks.push('Neovim edit produces native input');
  const large = 'a'.repeat(2047)+'🙂λ\n'+'z'.repeat(70000)+'\n';
  plain.value=large;
  await wait(()=>gridFor(plain)?.textContent.includes('aaaaaaa'),'large Unicode application write');
  document.querySelector('#submit').click();
  await wait(()=>document.querySelector('#submitted').textContent===large,'complete large form submission');
  checks.push('Large text, Unicode chunk boundary and final newline survive both directions');
  document.querySelector('#plain-form').requestSubmit();
  await wait(()=>document.querySelector('#submitted').textContent===large,'submit without a submitter');
  document.querySelector('#reset').click();
  await wait(()=>gridFor(plain)?.textContent.includes('First line'),'form reset reaches Neovim');
  checks.push('Form reset reaches real WASM');
  const controlled=document.querySelector('#controlled');controlled.focus();
  await wait(()=>controlled.getAttribute('tabindex')==='-1','controlled startup');
  key(controlled,'i');key(controlled,'X');
  await wait(()=>document.querySelector('#controlled-value').textContent==='XControlled initial text','controlled state receives input');
  key(controlled,'Escape');
  checks.push('Real Reagent controlled field receives Neovim input');
  document.querySelector('#controlled-update').click();
  await wait(()=>gridFor(controlled)?.textContent.includes('Updated by the application'),'Reagent state reaches Neovim');
  checks.push('Reagent application write reaches Neovim');
  const command=(field,text)=>{key(field,'Escape');for(const character of text)key(field,character);key(field,'Enter');};
  const shared='Shared virtual project needle λ🙂\n';
  plain.value=shared;
  await wait(()=>gridFor(plain)?.textContent.includes('Shared virtual'),'shared file text');
  command(plain,':w shared-fixture.txt');
  let saved;
  chrome.storage.onChanged.addListener(changes=>{if(changes['nvim-file:shared-fixture.txt'])saved=changes['nvim-file:shared-fixture.txt'].newValue;});
  chrome.storage.local.get(null).then(values=>{saved=values['nvim-file:shared-fixture.txt'];});
  await wait(()=>saved===shared,'explicit write persisted');
  command(controlled,':e shared-fixture.txt');
  await wait(()=>controlled.value===shared,'shared file recalled in existing editor');
  key(controlled,'Escape');for(const character of ':e shared-fi')key(controlled,character);
  await wait(()=>gridFor(controlled)?.textContent.includes(':e shared-fi'),'filename completion prompt');
  key(controlled,'Tab');
  await wait(()=>gridFor(controlled)?.textContent.includes(':e shared-fixture.txt'),'native filename completion');
  key(controlled,'Escape');
  command(controlled,':enew');
  await wait(()=>!controlled.value.trim(),'leave the saved file before project search');
  const beforeSearch=controlled.value;
  controlled.style.height='32rem';
  await wait(()=>frameFor(controlled).getBoundingClientRect().height>450,'project search viewport');
  command(controlled,':Meta! needle');
  await wait(()=>gridFor(controlled)?.textContent.includes('Shared virtual'),'Meta project search');
  if(controlled.value!==beforeSearch)throw Error('Meta search UI replaced the native textarea text');
  checks.push('Explicit :w persists exact text; :e recalls it in another existing editor; Tab completes the filename; Meta searches a saved file from an empty buffer without mirroring its scratch UI');
  frameFor(controlled).contentDocument.querySelector('.inline-editor > button').click();
  await wait(()=>controlled.getAttribute('tabindex')===null,'release the file-search editor');
  const sized=document.querySelector('#flex-sized');
  const matches = field => {
    const native=field.getBoundingClientRect(), frame=frameFor(field).getBoundingClientRect();
    return ['x','y','width','height'].every(key=>Math.abs(native[key]-frame[key])<1);
  };
  if (!matches(plain)) throw Error('Ordinary textarea bounds changed');
  sized.focus();
  await wait(()=>sized.getAttribute('tabindex')==='-1','flex textarea startup');
  await wait(()=>matches(sized),'absolute textarea bounds');
  const original=sized.getBoundingClientRect();
  document.querySelector('#sizing').style.width='55%';
  document.querySelector('#sizing pre').style.height='16rem';
  await wait(()=>matches(sized) && sized.getBoundingClientRect().width<original.width && sized.getBoundingClientRect().height>original.height,'responsive textarea bounds');
  const viewport=frameFor(sized).contentDocument.querySelector('.neovim__viewport');
  await wait(()=>gridFor(sized).getBoundingClientRect().height<=viewport.clientHeight+1,'Neovim rows fit available height');
  checks.push('Native bounds preserved in ordinary and absolute flex layouts; width, height and grid follow layout resizing');
  document.querySelector('#fancy').focus();document.querySelector('#readonly').focus();
  if(fixture.editors()!==2)throw Error('Fancy or readonly textarea was replaced');
  checks.push('Rich and readonly backing fields skipped');
  frameFor(sized).contentDocument.querySelector('.inline-editor > button').click();
  await wait(()=>sized.getAttribute('tabindex')===null,'release the sizing editor');
  document.querySelector('#dynamic').click();const added=document.querySelector('#added');added.focus();
  await wait(()=>added.getAttribute('tabindex')==='-1','dynamic textarea startup');
  const count=fixture.workers();added.remove();
  await wait(()=>fixture.workers()===count-1,'removed textarea worker disposal');
  checks.push('Dynamic textarea mounted; removal releases native worker');
  frameFor(plain).contentDocument.querySelector('button').click();
  await wait(()=>gridFor(plain)?.textContent.includes('First line'),'restart preserves mirrored buffer');
  await fixture.blacklist(['127.0.0.1']);
  await wait(()=>fixture.editors()===0 && fixture.workers()===0,'blacklist cleanup');
  if(plain.getAttribute('tabindex')!==null || plain.style.position) throw Error('Native control not restored');
  checks.push('Live blacklist restores controls and disposes every worker');
  return checks;
};
document.querySelector('#run-checks').addEventListener('click', async () => {
  const output=document.querySelector('#check-results');
  output.textContent='Running…';
  try {output.textContent=(await window.runIntegrationChecks()).join('\n');}
  catch(error) {output.textContent='FAIL: '+error.message;}
});

document.querySelector('#run-layout-checks').addEventListener('click',async()=>{
  const output=document.querySelector('#layout-results'),checks=[];
  const wait=async(predicate,label)=>{
    for(let i=0;i<1500;i++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,20));}
    throw Error('Timed out: '+label);
  };
  output.textContent='Running…';
  try {
    document.querySelector('#sizing pre').style.height='3rem';
    for(const id of ['plain','flex-sized']) {
      const field=document.getElementById(id),parent=field.parentElement;
      Object.assign(field.style,{height:'3rem',minHeight:'0',maxHeight:'3rem'});
      const originals=[field.style.cssText,parent.style.cssText];
      field.focus();
      await wait(()=>field.getAttribute('tabindex')==='-1',id+' startup');
      const frame=field.nextElementSibling,doc=frame.contentDocument;
      const grid=doc.querySelector('.neovim__grid'),viewport=doc.querySelector('.neovim__viewport');
      await wait(()=>grid.children.length>=8 && grid.getBoundingClientRect().height<=viewport.clientHeight+1,id+' eight fitted rows');
      const native=field.getBoundingClientRect(),bounds=frame.getBoundingClientRect();
      if(!['x','y','width','height'].every(key=>Math.abs(native[key]-bounds[key])<1))throw Error(id+' mismatched bounds');
      if(doc.querySelector('.neovim__toolbar'))throw Error('Extra mode title remains');
      for(const label of ['Restart editor','Use regular textarea']) {
        const button=doc.querySelector(`button[aria-label="${label}"]`);
        if(button?.title!==label || doc.defaultView.getComputedStyle(button).position!=='absolute')throw Error(label+' is not a labelled floating icon');
      }
      if(id==='flex-sized' && parent.getBoundingClientRect().height<native.height-1)throw Error('Absolute field did not reserve page space');
      doc.querySelector('.neovim__restore').click();
      await wait(()=>!field.nextElementSibling?.matches('iframe'),id+' restore');
      if(field.style.cssText!==originals[0] || parent.style.cssText!==originals[1])throw Error(id+' did not restore height constraints');
      checks.push(`${id}: PASS — eight fitted native rows, matching bounds, no title, floating icons, original height constraints restored`);
      output.textContent=checks.join('\n');
    }
  } catch(error){output.textContent=checks.join('\n')+'\nFAIL: '+error.message;}
});
