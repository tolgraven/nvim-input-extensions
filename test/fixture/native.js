const fields=['plain','flex-sized'].map(id=>document.getElementById(id));
if(new URL(location.href).searchParams.has('short')) {
  Object.assign(fields[0].style,{height:'3rem',minHeight:'0',maxHeight:'3rem'});
  fields[0].rows=2;
  fields[1].style.maxHeight='3rem';
  document.querySelector('#sizing pre').style.height='3rem';
}
const initialStyles=new Map(fields.flatMap(field=>[field,field.parentElement]).map(element=>[element,element.style.cssText]));
document.querySelector('#form').addEventListener('submit', event => {
  event.preventDefault();document.querySelector('#submitted').textContent=new FormData(event.target).get('body');
});
for (const type of ['input','change']) document.querySelector('#plain').addEventListener(type,()=>document.querySelector('#events').textContent+=' '+type);
document.querySelector('#measure').addEventListener('click',()=>{
  document.querySelector('#dimensions').textContent=fields.map(native=>{
    const id=native.id,frame=native.nextElementSibling;
    if(frame?.tagName!=='IFRAME')return `${id}: native styles ${[native,native.parentElement].every(element=>element.style.cssText===initialStyles.get(element))?'PASS':'FAIL'}`;
    const a=native.getBoundingClientRect(),b=frame.getBoundingClientRect();
    const match=['x','y','width','height'].every(key=>Math.abs(a[key]-b[key])<1);
    return `${id}: ${match?'PASS':'FAIL'} native ${a.width.toFixed(2)} × ${a.height.toFixed(2)}, Neovim ${b.width.toFixed(2)} × ${b.height.toFixed(2)}`;
  }).join('\n');
});
