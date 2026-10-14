document.querySelector('#form').addEventListener('submit', event => {
  event.preventDefault();document.querySelector('#submitted').textContent=new FormData(event.target).get('body');
});
for (const type of ['input','change']) document.querySelector('#plain').addEventListener(type,()=>document.querySelector('#events').textContent+=' '+type);
