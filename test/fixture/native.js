document.querySelector('#form').addEventListener('submit', event => {
  event.preventDefault();document.querySelector('#submitted').textContent=new FormData(event.target).get('body');
});
for (const type of ['input','change']) document.querySelector('#plain').addEventListener(type,()=>document.querySelector('#events').textContent+=' '+type);
document.querySelector('#measure').addEventListener('click',()=>{
  document.querySelector('#dimensions').textContent=['plain','flex-sized'].map(id=>{
    const native=document.getElementById(id), frame=native.nextElementSibling;
    if(frame?.tagName!=='IFRAME')return id+': editor not started';
    const a=native.getBoundingClientRect(),b=frame.getBoundingClientRect();
    const match=['x','y','width','height'].every(key=>Math.abs(a[key]-b[key])<1);
    return `${id}: ${match?'PASS':'FAIL'} native ${a.width.toFixed(2)} × ${a.height.toFixed(2)}, Neovim ${b.width.toFixed(2)} × ${b.height.toFixed(2)}`;
  }).join('\n');
});
