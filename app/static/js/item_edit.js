(function(){
  const init=JSON.parse(document.getElementById('optInit').textContent);
  const L=JSON.parse(document.getElementById('optI18n').textContent);
  const rows=document.getElementById('optRows'), qt=document.getElementById('qtype');
  const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  function row(o,i){
    const t=qt.value, d=document.createElement('div');
    if(t==='match'){d.className='orow match';
      d.innerHTML=`<span>${i+1}</span><input type="text" name="opt_en" placeholder="${L.len}" value="${esc(o.en)}"><input type="text" name="opt_mr" placeholder="${L.lmr}" value="${esc(o.mr)}"><input type="text" name="match_en" placeholder="${L.ren}" value="${esc(o.men)}"><input type="text" name="match_mr" placeholder="${L.rmr}" value="${esc(o.mmr)}"><button type="button" class="btn ghost sm">✕</button>`;
    } else {d.className='orow';
      let extra='';
      if(t==='sjt') extra=`<input type="number" step="0.05" min="0" max="1" name="opt_weight" value="${o.w==null?(o.c?1:0):o.w}" title="credit">`;
      else if(t==='sequence') extra=`<input type="number" min="1" name="opt_seq" value="${o.s||i+1}" title="position">`;
      else extra=`<label class="check"><input type="${t==='multi'?'checkbox':'radio'}" name="opt_correct" value="${i}" ${o.c?'checked':''}>✓</label>`;
      d.innerHTML=`<span>${i+1}</span><input type="text" name="opt_en" placeholder="${L.en}" value="${esc(o.en)}"><input type="text" name="opt_mr" placeholder="${L.mr}" value="${esc(o.mr)}">${extra}<button type="button" class="btn ghost sm">✕</button>`;
    }
    d.querySelector('button').onclick=()=>{const c=collect();c.splice(i,1);draw(c);};
    return d;
  }
  function collect(){
    return [...rows.children].map(d=>{const g=n=>{const e=d.querySelector(`[name="${n}"]`);return e?(e.type==='checkbox'||e.type==='radio'?e.checked:e.value):null};
      return {en:g('opt_en'),mr:g('opt_mr'),c:g('opt_correct'),w:g('opt_weight')===null?null:parseFloat(g('opt_weight')),s:g('opt_seq'),men:g('match_en'),mmr:g('match_mr')};});
  }
  function draw(list){rows.innerHTML='';list.forEach((o,i)=>rows.appendChild(row(o,i)));}
  function toggle(){
    const t=qt.value, show=(id,on)=>{document.getElementById(id).style.display=on?'':'none'};
    show('optCard',['single','multi','truefalse','sjt','sequence','match'].includes(t));
    show('numCard',t==='numeric');show('fillCard',t==='fill_blank');show('rubricCard',['short_text','audio_response'].includes(t));
    document.getElementById('optHelp').textContent=L.help[t]||'';
    let list=rows.children.length?collect():init;
    if(t==='truefalse'&&list.length===0) list=[{en:'True',mr:'बरोबर'},{en:'False',mr:'चूक'}];
    if(list.length===0) list=[{},{},{},{}];
    draw(list);
  }
  function live(){
    const t=qt.value, out=[], c=L.chk, list=collect(), box=document.getElementById('liveChecks');
    if(!box) return;
    const stemMr=document.querySelector('textarea[name=stem_mr]');
    if(stemMr && !stemMr.value.trim()) out.push(c.stem_mr);
    if(['single','sjt','multi','sequence','match','truefalse'].includes(t)){
      const filled=list.filter(o=>(o.en||'').trim());
      if(['single','sjt'].includes(t) && filled.length<4) out.push(c.few);
      if(filled.some(o=>/\b(all|none|both|neither) of (the )?(above|these|them)\b/i.test(o.en)||/वरील सर्व|वरीलपैकी/.test(o.mr||''))) out.push(c.allnone);
      if(filled.some(o=>!(o.mr||'').trim())) out.push(c.mr);
      if(['single','sjt'].includes(t) && filled.length>=2){
        const score=o=>t==='sjt'?(parseFloat(o.w)||0):(o.c?1:0);
        const best=filled.reduce((a,b)=>score(b)>score(a)?b:a);
        const others=filled.filter(o=>o!==best).map(o=>o.en.length).sort((a,b)=>b-a);
        if(others.length && best.en.length>=others[0]*1.15 && best.en.length-others[0]>=8) out.push(c.longest);
      }
    }
    box.innerHTML=out.map(x=>'⚠ '+esc(x)).join('<br>');
  }
  document.getElementById('itemForm').addEventListener('input',live);
  document.getElementById('itemForm').addEventListener('change',live);
  setTimeout(live,50);
  document.getElementById('addOpt').onclick=()=>{const c=collect();c.push({});draw(c);};
  qt.addEventListener('change',toggle);toggle();
})();
