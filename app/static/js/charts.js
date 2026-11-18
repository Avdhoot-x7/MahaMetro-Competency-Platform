(function(){
  if(!window.Chart) return;
  Chart.defaults.font.family='"Noto Sans","Noto Sans Devanagari",system-ui,sans-serif';
  Chart.defaults.color='#5b6878';
  const C={brand:'#2b6cb0',soft:'#a9c6e6',ok:'#1e7b4a',pri:'#d97706',gate:'#c0392b',dev:'#d4a800',na:'#9aa4b1'};
  window.MMC=C;
  const el=document.getElementById('profileData');
  if(el){
    const d=JSON.parse(el.textContent);
    new Chart(document.getElementById('profileChart'),{type:'bar',data:{labels:d.labels,datasets:[
      {label:d.lRequired,data:d.required,backgroundColor:C.soft,borderRadius:3},
      {label:d.lAttained,data:d.attained,backgroundColor:d.attained.map((a,i)=>a>=d.required[i]?C.ok:(d.required[i]-a>=2?C.pri:C.dev)),borderRadius:3}]},
      options:{indexAxis:'y',maintainAspectRatio:false,scales:{x:{min:0,max:5,ticks:{stepSize:1}}},
        plugins:{tooltip:{callbacks:{title:(it)=>d.labels[it[0].dataIndex]+' '+d.names[it[0].dataIndex]}}}}});
  }
  document.querySelectorAll('script[data-chart]').forEach(s=>{
    const cfg=JSON.parse(s.textContent);const cv=document.getElementById(s.dataset.chart);
    (cfg.data.datasets||[]).forEach(ds=>{if(typeof ds.backgroundColor==='string'&&C[ds.backgroundColor])ds.backgroundColor=C[ds.backgroundColor];
      if(Array.isArray(ds.backgroundColor))ds.backgroundColor=ds.backgroundColor.map(c=>C[c]||c);});
    cfg.options=Object.assign({maintainAspectRatio:false},cfg.options||{});
    new Chart(cv,cfg);
  });
})();
