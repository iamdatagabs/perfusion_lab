const API_KEY='perfusionlab.apiBase';
const SPRING_API_BASE='https://perfusionlab-api.onrender.com';
const defaultBase=()=>location.hostname==='localhost'||location.hostname==='127.0.0.1'?'':SPRING_API_BASE;
let apiBase=(localStorage.getItem(API_KEY)||defaultBase()).replace(/\/$/,'');
const $=id=>document.getElementById(id);
const state={jobs:[],nodes:[],capacity:[],activeJob:null,pollTimer:null,batchPollTimer:null,refreshTimer:null,sweepInputs:[],sweepResults:[],timeline:null,multivariateTimeline:null,mathReference:null,supportsCoupled:false};
const fmt=(v,d=1)=>v==null?'—':Number(v).toLocaleString('pt-BR',{minimumFractionDigits:d,maximumFractionDigits:d});
const date=v=>v?new Date(v).toLocaleString('pt-BR'):'—';
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const url=path=>apiBase+path;
const newCorrelationId=()=>globalThis.crypto?.randomUUID?.()||('corr-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2));
function toast(message){const el=$('toast');el.textContent=message;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),3200)}
async function request(path,options={}){const response=await fetch(url(path),{...options,headers:{'Content-Type':'application/json','X-Correlation-ID':newCorrelationId(),...(options.headers||{})}});if(!response.ok){let msg=response.status+' '+response.statusText;try{const t=await response.text();if(t)msg=t}catch{}throw new Error(msg)}if(response.status===204)return null;return response.json()}
function setConnection(ok,label){$('connectionDot').parentElement.className='connection '+(ok?'online':'offline');$('connectionText').textContent=label}
async function checkHealth(){const submit=$('simulationForm')?.querySelector('button[type=submit]');try{const health=await request('/api/v1/health');const capabilities=Array.isArray(health.capabilities)?health.capabilities:[];const hasGrid=capabilities.includes('grid');state.supportsCoupled=capabilities.includes('ode-pde-coupled');const coupledButton=$('runCoupledConsole');if(coupledButton){coupledButton.disabled=!state.supportsCoupled;$('coupledConsoleStatus').textContent=state.supportsCoupled?'Backend conectado; acoplamento ODE–PDE disponível.':'API conectada sem o recurso de acoplamento ODE–PDE.'}if(submit){submit.disabled=!hasGrid;submit.textContent=hasGrid?'▶ Enviar para o grid':'Grid indisponível nesta API'}document.querySelectorAll('.nav-item[data-view="jobs"],.nav-item[data-view="cluster"]').forEach(item=>item.hidden=!hasGrid);const overviewStats=document.querySelectorAll('#view-overview>.stat-grid');if(overviewStats[1])overviewStats[1].hidden=!hasGrid;const dashboard=document.querySelector('#view-overview .dashboard-grid');if(dashboard)dashboard.hidden=!hasGrid;setConnection(true,hasGrid?'Spring Boot · Grid disponível':'API de prévia · Grid indisponível');return true}catch{state.supportsCoupled=false;const coupledButton=$('runCoupledConsole');if(coupledButton)coupledButton.disabled=true;if(submit){submit.disabled=true;submit.textContent='API indisponível'}setConnection(false,'API indisponível');return false}}
function go(view){document.querySelectorAll('.view').forEach(v=>v.classList.toggle('active',v.id==='view-'+view));document.querySelectorAll('.nav-item[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===view));const names={overview:'Visão geral',simulate:'Nova simulação',jobs:'Experimentos',cluster:'Computação distribuída',settings:'Configurações'};$('pageTitle').textContent=names[view]||view;if(view==='jobs')loadJobs();if(view==='cluster')loadCluster();if(view==='overview')loadOverview();if(view==='settings')$('apiBaseInput').value=apiBase}
document.querySelectorAll('[data-view]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.view)));document.querySelectorAll('[data-go]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.go)));
async function loadJobs(){try{state.jobs=await request('/api/v1/simulations/jobs?limit=50');renderJobs();renderRecent()}catch(e){$('allJobs').innerHTML='<div class="empty">Não foi possível carregar jobs. '+esc(e.message)+'</div>';}}
function statusBadge(s){const x=String(s||'UNKNOWN').toLowerCase();return '<span class="status '+x+'">'+esc(s)+'</span>'}
function jobsTable(items){if(!items?.length)return '<div class="empty">Nenhuma simulação encontrada. Crie a primeira simulação para começar.</div>';return '<table class="data-table"><thead><tr><th>JOB ID</th><th>ESTADO</th><th>SUBMETIDO</th><th>PAM EST.</th><th>DO₂ INDEX.</th><th></th></tr></thead><tbody>'+items.map(j=>'<tr><td class="mono">'+esc(String(j.jobId).slice(0,8))+'</td><td>'+statusBadge(j.state)+'</td><td>'+esc(date(j.submittedAt))+'</td><td>'+fmt(j.result?.estimatedMapMmHg)+'</td><td>'+fmt(j.result?.estimatedOxygenDeliveryIndexMlMinM2,0)+'</td><td><button class="text-button" data-job="'+esc(j.jobId)+'">Detalhes ↗</button></td></tr>').join('')+'</tbody></table>'}
function bindJobButtons(root){root.querySelectorAll('[data-job]').forEach(b=>b.addEventListener('click',()=>showJob(b.dataset.job)))}
function renderJobs(){$('allJobs').innerHTML=jobsTable(state.jobs);bindJobButtons($('allJobs'))}
function renderRecent(){$('recentJobs').innerHTML=jobsTable(state.jobs.slice(0,6));bindJobButtons($('recentJobs'))}
async function showJob(id){try{const j=await request('/api/v1/simulations/jobs/'+encodeURIComponent(id));state.activeJob=j;$('jobDetail').hidden=false;$('jobDetailContent').innerHTML='<div class="detail-kv"><div><small>JOB ID</small><strong class="mono">'+esc(j.jobId)+'</strong></div><div><small>ESTADO</small><strong>'+statusBadge(j.state)+'</strong></div><div><small>SUBMETIDO</small><strong>'+esc(date(j.submittedAt))+'</strong></div><div><small>INICIADO</small><strong>'+esc(date(j.startedAt))+'</strong></div><div><small>CONCLUÍDO</small><strong>'+esc(date(j.completedAt))+'</strong></div><div><small>ERRO</small><strong>'+esc(j.error||'—')+'</strong></div></div>'+(j.result?renderResult(j.result):'<p class="muted">Resultado ainda não disponível.</p>');$('jobDetail').scrollIntoView({behavior:'smooth',block:'start'});if(['QUEUED','RUNNING'].includes(j.state))watchJob(id)}catch(e){toast('Falha ao consultar job: '+e.message)}}
function renderResult(r){return '<h3 style="font-size:13px;margin:18px 0 10px">Resultados do modelo</h3><div class="result-grid"><div class="result-card"><small>PAM ESTIMADA</small><strong>'+fmt(r.estimatedMapMmHg)+'</strong></div><div class="result-card"><small>DO₂</small><strong>'+fmt(r.estimatedOxygenDeliveryMlMin,0)+'</strong></div><div class="result-card"><small>DO₂ INDEXADA</small><strong>'+fmt(r.estimatedOxygenDeliveryIndexMlMinM2,0)+'</strong></div><div class="result-card"><small>VERSÃO DO MODELO</small><strong style="font-size:14px">'+esc(r.modelVersion)+'</strong></div></div>'}
$('closeDetail').addEventListener('click',()=>$('jobDetail').hidden=true);

$('batchForm').addEventListener('submit',async e=>{
  e.preventDefault();
  const source=$('simulationForm');
  if(!source.reportValidity()){toast('Revise os parâmetros em Nova simulação antes de criar o lote.');return}
  const count=Number($('batchReplicates').value),parallelism=Number($('batchParallelism').value);
  if(!Number.isInteger(count)||count<2||count>100||!Number.isInteger(parallelism)||parallelism<1||parallelism>256){toast('Revise o número de réplicas e o paralelismo.');return}
  const button=$('batchSubmit');
  try{
    button.disabled=true;button.textContent='Criando lote...';
    const base=formPayload();
    const batch=await request('/api/v1/experiments/batches',{method:'POST',body:JSON.stringify({
      executionProfile:$('batchProfile').value,parallelism,simulations:state.sweepInputs.length?state.sweepInputs:Array.from({length:count},()=>({...base}))
    })});
    $('batchStatus').hidden=false;
    $('batchStatus').innerHTML='<strong>Lote criado</strong><div class="mono">'+esc(batch.batchId)+'</div><p>Consultando estado dos '+batch.jobCount+' jobs...</p>';
    toast('Lote experimental enviado · '+String(batch.batchId).slice(0,8));
    watchBatch(batch.batchId);
  }catch(err){toast('Falha ao criar lote: '+err.message)}
  finally{button.disabled=false;button.textContent='▶ Criar lote'}
});
function watchBatch(id){
  if(state.batchPollTimer)clearInterval(state.batchPollTimer);
  let attempts=0;
  const refresh=async()=>{
    attempts++;
    try{
      const b=await request('/api/v1/experiments/batches/'+encodeURIComponent(id));
      const done=b.succeeded+b.failed,percent=b.jobCount?Math.round(done/b.jobCount*100):0;
      $('batchStatus').hidden=false;
      $('batchStatus').innerHTML='<div><strong>'+esc(b.state)+'</strong> · '+done+' / '+b.jobCount+' concluídos</div><div class="batch-progress"><i style="width:'+percent+'%"></i></div><div>Na fila: '+b.queued+' · Executando: '+b.running+' · Sucesso: '+b.succeeded+' · Falhas: '+b.failed+'</div><p>Perfil resolvido: '+esc(b.executionProfile)+' · Paralelismo: '+b.parallelism+'</p><div class="mono">Batch ID: '+esc(b.batchId)+'</div>'+(b.schedulingReason?'<p>'+esc(b.schedulingReason)+'</p>':'');
      if(['SUCCEEDED','PARTIAL','FAILED'].includes(b.state)||attempts>=150){
        clearInterval(state.batchPollTimer);state.batchPollTimer=null;
        await loadJobs();await loadOverview();
      }
    }catch(err){
      $('batchStatus').innerHTML='<strong>Falha ao consultar lote</strong><p>'+esc(err.message)+'</p>';
      if(attempts>=150){clearInterval(state.batchPollTimer);state.batchPollTimer=null}
    }
  };
  refresh();state.batchPollTimer=setInterval(refresh,2000);
}

async function loadCluster(){try{const [nodes,capacity]=await Promise.all([request('/api/v1/cluster/nodes'),request('/api/v1/cluster/capacity')]);state.nodes=nodes;state.capacity=capacity;renderCluster()}catch(e){$('nodeTable').innerHTML='<div class="empty">Não foi possível consultar o cluster. '+esc(e.message)+'</div>'}}
function renderCapacity(target,items){target.innerHTML=items.length?items.map(c=>{const used=Math.max(0,c.totalSlots-c.availableSlots),pct=c.totalSlots?Math.min(100,used/c.totalSlots*100):0;return '<div class="capacity-row"><div class="capacity-title"><strong>'+esc(c.executionProfile)+'</strong><span>'+c.availableSlots+' / '+c.totalSlots+' slots livres</span></div><div class="progress"><i style="width:'+pct+'%"></i></div><div class="subtle-note">'+c.activeNodes+' nós · '+c.totalCpuCores+' vCPU declaradas · '+fmt(c.totalMemoryMb/1024,1)+' GB declarados</div></div>'}).join(''):'<div class="empty">Nenhuma capacidade publicada.</div>'}
function renderCluster(){const active=state.nodes.filter(n=>n.nodeStatus==='READY');const total=state.capacity.reduce((a,c)=>a+c.totalSlots,0),free=state.capacity.reduce((a,c)=>a+c.availableSlots,0);$('clusterNodes').textContent=active.length;$('clusterTotalSlots').textContent=total;$('clusterFreeSlots').textContent=free;$('nodeTable').innerHTML=state.nodes.length?'<table class="data-table"><thead><tr><th>NÓ</th><th>ESTADO</th><th>PERFIL</th><th>CPU / RAM</th><th>SLOTS</th><th>HEARTBEAT</th><th>JVM HEAP</th></tr></thead><tbody>'+state.nodes.map(n=>'<tr><td><strong>'+esc(n.nodeId)+'</strong><div class="mono">'+esc(n.hostname||n.workerId)+'</div></td><td>'+statusBadge(n.nodeStatus)+'</td><td>'+esc(n.executionProfile)+'</td><td>'+n.cpuCores+' vCPU / '+fmt(n.memoryMb/1024,1)+' GB</td><td>'+n.availableSlots+' / '+n.slots+'</td><td>'+esc(date(n.heartbeatAt))+'</td><td>'+n.jvmHeapUsedMb+' / '+n.jvmHeapMaxMb+' MB</td></tr>').join('')+'</tbody></table>':'<div class="empty">Nenhum worker ativo foi localizado.</div>';renderCapacity($('profileCapacity'),state.capacity);renderCapacity($('capacityOverview'),state.capacity)}
async function loadOverview(){await Promise.all([loadJobs(),loadCluster()]);const pending=state.jobs.filter(j=>['QUEUED','RUNNING'].includes(j.state)).length;$('statJobs').textContent=state.jobs.length;$('statRunning').textContent=pending;$('statNodes').textContent=state.nodes.filter(n=>n.nodeStatus==='READY').length;$('statSlots').textContent=state.capacity.reduce((a,c)=>a+c.availableSlots,0)}
function formPayload(){const f=new FormData($('simulationForm'));return {pumpFlowLMin:Number(f.get('pumpFlowLMin')),hemoglobinGdL:Number(f.get('hemoglobinGdL')),arterialSaturation:Number(f.get('arterialSaturationPct'))/100,systemicVascularResistance:Number(f.get('systemicVascularResistance')),bodySurfaceAreaM2:Number(f.get('bodySurfaceAreaM2')),temperatureC:Number(f.get('temperatureC'))}}
function renderPreview(r){$('resultState').textContent='PRÉVIA CALCULADA';$('previewResults').className='';$('previewResults').innerHTML=renderResult(r)+'<p class="subtle-note">Modelo '+esc(r.modelVersion)+' · '+esc(date(r.generatedAt))+'</p>'}

function calculateMathMetrics(input){
 const map=input.pumpFlowLMin*input.systemicVascularResistance/80;
 const do2=input.pumpFlowLMin*10*input.hemoglobinGdL*1.34*input.arterialSaturation;
 return {map,do2,do2i:do2/input.bodySurfaceAreaM2};
}
function setMathReference(){
 if(!$('simulationForm').checkValidity()){toast('Corrija os parâmetros antes de definir a referência.');return}
 state.mathReference=calculateMathMetrics(formPayload());
 $('mathReferenceState').textContent='Referência atualizada · '+new Date().toLocaleTimeString('pt-BR');
 updateMathFlag();
}
function updateMathFlag(){
 const panel=$('mathFlag');
 if(!$('simulationForm').checkValidity()||!state.mathReference){
  panel.className='math-flag pending';$('mathFlagTitle').textContent='Aguardando parâmetros válidos';
  $('mathFlagDescription').textContent='Preencha os campos para calcular o indicador.';$('mathFlagScore').textContent='—';$('mathFlagDetails').textContent='—';return;
 }
 const stableLimit=Number($('mathStableLimit').value),criticalLimit=Number($('mathCriticalLimit').value);
 if(!Number.isFinite(stableLimit)||!Number.isFinite(criticalLimit)||stableLimit<0||criticalLimit<=stableLimit){
  panel.className='math-flag pending';$('mathFlagTitle').textContent='Revise as faixas matemáticas';
  $('mathFlagDescription').textContent='O limite crítico deve ser maior que o limite estável.';$('mathFlagScore').textContent='—';$('mathFlagDetails').textContent='—';return;
 }
 const current=calculateMathMetrics(formPayload()),reference=state.mathReference;
 const deviations=[['PAM',current.map,reference.map],['DO₂',current.do2,reference.do2],['DO₂i',current.do2i,reference.do2i]]
  .map(([name,value,base])=>({name,pct:Math.abs((value-base)/Math.max(Math.abs(base),1e-9))*100}));
 const score=Math.sqrt(deviations.reduce((sum,item)=>sum+item.pct**2,0)/deviations.length);
 const status=score<=stableLimit?'stable':score>criticalLimit?'critical':'unstable';
 const labels={stable:'ESTÁVEL · FAIXA MATEMÁTICA',unstable:'INSTÁVEL · DESVIO MATEMÁTICO',critical:'CRÍTICO · DESVIO MATEMÁTICO'};
 const descriptions={stable:'Resultados próximos da referência definida.',unstable:'Resultados com afastamento intermediário da referência.',critical:'Resultados com afastamento elevado da referência.'};
 panel.className='math-flag '+status;
 $('mathFlagTitle').textContent=labels[status];$('mathFlagDescription').textContent=descriptions[status];
 $('mathFlagScore').textContent=fmt(score,1)+'%';
 $('mathFlagDetails').textContent=deviations.map(item=>item.name+' '+fmt(item.pct,1)+'%').join(' · ');
 $('mathFlagThresholds').textContent='Estável ≤ '+fmt(stableLimit,1)+'% · Crítico > '+fmt(criticalLimit,1)+'%';
}
$('setMathReference').addEventListener('click',setMathReference);
$('simulationForm').querySelectorAll('input[type=number]').forEach(input=>input.addEventListener('input',updateMathFlag));
$('mathStableLimit').addEventListener('input',updateMathFlag);
$('mathCriticalLimit').addEventListener('input',updateMathFlag);
setMathReference();


const sweepSpecs={
 pumpFlowLMin:{min:.1,max:10,step:1,label:'Fluxo de bomba (L/min)'},
 hemoglobinGdL:{min:1,max:25,step:1,label:'Hemoglobina (g/dL)'},
 arterialSaturation:{min:.5,max:1,step:.01,label:'Saturação arterial (%)',scale:100},
 systemicVascularResistance:{min:100,max:5000,step:100,label:'Resistência vascular sistêmica'},
 bodySurfaceAreaM2:{min:.3,max:3,step:.1,label:'Superfície corporal (m²)'},
 temperatureC:{min:15,max:40,step:1,label:'Temperatura (°C)'}
};
function downloadText(name,text,mime){const a=document.createElement('a'),blob=new Blob([text],{type:mime});a.href=URL.createObjectURL(blob);a.download=name;a.click();URL.revokeObjectURL(a.href)}
function renderSweep(){
 const rows=state.sweepResults;
 if(!rows.length){$('sweepOutput').innerHTML='<div class="empty">Configure uma varredura para visualizar a comparação.</div>';return}
 const max=Math.max(...rows.map(x=>x.result.estimatedOxygenDeliveryIndexMlMinM2));
 const min=Math.min(...rows.map(x=>x.result.estimatedOxygenDeliveryIndexMlMinM2));
 const points=rows.map((x,i)=>{const px=35+i*(570/Math.max(1,rows.length-1));const py=155-(x.result.estimatedOxygenDeliveryIndexMlMinM2-min)/(max-min||1)*115;return px+','+py}).join(' ');
 $('sweepOutput').innerHTML='<div class="sweep-chart"><div class="chart-caption">DO₂ INDEXADA — comparação dos cenários (unidade conforme modelo)</div><svg viewBox="0 0 640 190" role="img" aria-label="Gráfico de comparação da entrega de oxigênio indexada"><line x1="35" y1="155" x2="605" y2="155" stroke="#365063"/><line x1="35" y1="25" x2="35" y2="155" stroke="#365063"/><polyline points="'+points+'" fill="none" stroke="#55d6df" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>'+rows.map((x,i)=>{const px=35+i*(570/Math.max(1,rows.length-1));const py=155-(x.result.estimatedOxygenDeliveryIndexMlMinM2-min)/(max-min||1)*115;return '<circle cx="'+px+'" cy="'+py+'" r="4" fill="#72dfb2"><title>'+esc(fmt(x.value,2))+' → '+esc(fmt(x.result.estimatedOxygenDeliveryIndexMlMinM2,1))+'</title></circle>'}).join('')+'</svg></div><div class="table-wrap"><table class="data-table"><thead><tr><th>CENÁRIO</th><th>VALOR VARIADO</th><th>PAM EST.</th><th>DO₂</th><th>DO₂ INDEXADA</th></tr></thead><tbody>'+rows.map((x,i)=>'<tr><td>'+(i+1)+'</td><td>'+fmt(x.value,2)+'</td><td>'+fmt(x.result.estimatedMapMmHg,2)+'</td><td>'+fmt(x.result.estimatedOxygenDeliveryMlMin,1)+'</td><td>'+fmt(x.result.estimatedOxygenDeliveryIndexMlMinM2,1)+'</td></tr>').join('')+'</tbody></table></div><p class="subtle-note">Comparação calculada pelo endpoint de prévia do modelo educacional. Não representa série temporal nem comportamento fisiológico validado.</p>';
 $('exportSweepCsv').disabled=false;$('exportSweepJson').disabled=false;
}
$('generateSweep').addEventListener('click',async()=>{
 const form=$('simulationForm');if(!form.reportValidity()){toast('Revise os parâmetros-base em Nova simulação.');return}
 const key=$('sweepParameter').value,spec=sweepSpecs[key],start=Number($('sweepStart').value),end=Number($('sweepEnd').value),count=Number($('sweepPoints').value);
 if(!Number.isFinite(start)||!Number.isFinite(end)||start===end||!Number.isInteger(count)||count<2||count>25||start<spec.min||start>spec.max||end<spec.min||end>spec.max){toast('Intervalo inválido. Respeite os limites do modelo e use de 2 a 25 pontos.');return}
 const btn=$('generateSweep');btn.disabled=true;btn.textContent='Calculando...';
 try{
  const base=formPayload(),values=Array.from({length:count},(_,i)=>start+(end-start)*i/(count-1));
  const inputs=values.map(value=>({...base,[key]:key==='arterialSaturation'?value/100:value}));
  const results=await Promise.all(inputs.map(async(input,i)=>({value:values[i],input,result:await request('/api/v1/simulations/preview',{method:'POST',body:JSON.stringify(input)})})));
  state.sweepInputs=inputs;state.sweepResults=results;renderSweep();
  $('batchReplicates').value=String(count);toast(count+' cenários calculados. Você pode enviá-los como lote ao grid.');
 }catch(e){toast('Falha ao calcular a varredura: '+e.message)}
 finally{btn.disabled=false;btn.textContent='Gerar cenários'}
});
$('exportSweepCsv').addEventListener('click',()=>{
 const rows=state.sweepResults;if(!rows.length)return;
 const headers=['parameter_value','estimated_map_mmhg','oxygen_delivery_ml_min','oxygen_delivery_index_ml_min_m2','temperature_c'];
 const csv=[headers.join(','),...rows.map(x=>[x.value,x.result.estimatedMapMmHg,x.result.estimatedOxygenDeliveryMlMin,x.result.estimatedOxygenDeliveryIndexMlMinM2,x.input.temperatureC].join(','))].join('\\n');
 downloadText('perfusionlab-parameter-sweep.csv',csv,'text/csv;charset=utf-8');
});
$('exportSweepJson').addEventListener('click',()=>{if(state.sweepResults.length)downloadText('perfusionlab-parameter-sweep.json',JSON.stringify({model:'educational',parameter:$('sweepParameter').value,results:state.sweepResults},null,2),'application/json')});

const timelineLabels={pumpFlowLMin:'Fluxo de bomba (L/min)',hemoglobinGdL:'Hemoglobina (g/dL)',arterialSaturation:'Saturação arterial (%)',systemicVascularResistance:'Resistência vascular sistêmica',bodySurfaceAreaM2:'Superfície corporal (m²)',temperatureC:'Temperatura (°C)'};
function renderTimeline(data){
 state.timeline=data;const pts=data.points,values=pts.map(p=>p.estimatedOxygenDeliveryIndexMlMinM2),lo=Math.min(...values),hi=Math.max(...values);
 const coords=pts.map((p,i)=>{const x=38+i*564/Math.max(1,pts.length-1),y=150-(p.estimatedOxygenDeliveryIndexMlMinM2-lo)/(hi-lo||1)*112;return x+','+y});
 const svg='<svg viewBox="0 0 640 190" role="img" aria-label="Série temporal didática de DO2 indexada"><line x1="38" y1="150" x2="602" y2="150" stroke="#365063"/><line x1="38" y1="24" x2="38" y2="150" stroke="#365063"/><polyline points="'+coords.join(' ')+'" fill="none" stroke="#55d6df" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>'+pts.map((p,i)=>'<circle cx="'+(38+i*564/Math.max(1,pts.length-1))+'" cy="'+(150-(p.estimatedOxygenDeliveryIndexMlMinM2-lo)/(hi-lo||1)*112)+'" r="2.8" fill="#72dfb2"/>').join('')+'</svg>';
 $('timelineOutput').innerHTML='<div class="timeline-chart"><div class="chart-caption">DO₂ INDEXADA · VALOR DO MODELO POR TEMPO</div>'+svg+'<div class="timeline-axis"><span>0 s</span><span>'+data.durationSeconds+' s</span></div></div><div class="timeline-meta"><span><b>'+pts.length+'</b> pontos</span><span><b>'+esc(data.parameter)+'</b> · '+esc(fmt(pts[0].parameterValue,2))+' → '+esc(fmt(pts[pts.length-1].parameterValue,2))+'</span><span>Modelo '+esc(data.modelVersion)+'</span></div><div class="table-wrap timeline-table"><table class="data-table"><thead><tr><th>TEMPO (S)</th><th>VALOR VARIADO</th><th>PAM EST.</th><th>DO₂</th><th>DO₂ INDEXADA</th></tr></thead><tbody>'+pts.map(p=>'<tr><td>'+p.elapsedSeconds+'</td><td>'+fmt(p.parameterValue,2)+'</td><td>'+fmt(p.estimatedMapMmHg,2)+'</td><td>'+fmt(p.estimatedOxygenDeliveryMlMin,1)+'</td><td>'+fmt(p.estimatedOxygenDeliveryIndexMlMinM2,1)+'</td></tr>').join('')+'</tbody></table></div>';
 $('exportTimelineCsv').disabled=false;$('exportTimelineJson').disabled=false;
}
$('runTimeline').addEventListener('click',async()=>{
 const form=$('simulationForm');if(!form.reportValidity()){toast('Revise primeiro os parâmetros-base.');return}
 const button=$('runTimeline'),parameter=$('timelineParameter').value,endValue=Number($('timelineEndValue').value),durationSeconds=Number($('timelineDuration').value),stepSeconds=Number($('timelineStep').value);
 if(!Number.isFinite(endValue)||!Number.isInteger(durationSeconds)||durationSeconds<1||durationSeconds>3600||!Number.isInteger(stepSeconds)||stepSeconds<1||stepSeconds>300||Math.ceil(durationSeconds/stepSeconds)+1>361){toast('Parâmetros temporais inválidos. Limite: 361 pontos.');return}
 button.disabled=true;button.textContent='Calculando...';
 try{const data=await request('/api/v1/simulations/timeline',{method:'POST',body:JSON.stringify({baseline:formPayload(),parameter,endValue:parameter==='arterialSaturation'?endValue/100:endValue,durationSeconds,stepSeconds})});renderTimeline(data);toast('Cenário temporal calculado.')}
 catch(e){toast('Falha na simulação temporal: '+e.message)}
 finally{button.disabled=false;button.textContent='Gerar linha temporal'}
});
$('exportTimelineCsv').addEventListener('click',()=>{if(!state.timeline)return;const d=state.timeline,head='elapsed_seconds,parameter_value,estimated_map_mmhg,oxygen_delivery_ml_min,oxygen_delivery_index_ml_min_m2';const rows=d.points.map(p=>[p.elapsedSeconds,p.parameterValue,p.estimatedMapMmHg,p.estimatedOxygenDeliveryMlMin,p.estimatedOxygenDeliveryIndexMlMinM2].join(','));downloadText('perfusionlab-time-scenario.csv',[head,...rows].join('\\n'),'text/csv;charset=utf-8')});
$('exportTimelineJson').addEventListener('click',()=>{if(state.timeline)downloadText('perfusionlab-time-scenario.json',JSON.stringify(state.timeline,null,2),'application/json')});

function renderMultivariate(data){
 state.multivariateTimeline=data;const pts=data.points,vals=pts.map(p=>p.estimatedOxygenDeliveryIndexMlMinM2),lo=Math.min(...vals),hi=Math.max(...vals);
 const coords=pts.map((p,i)=>(38+i*564/Math.max(1,pts.length-1))+','+(150-(p.estimatedOxygenDeliveryIndexMlMinM2-lo)/(hi-lo||1)*112));
 const svg='<svg viewBox="0 0 640 190" role="img" aria-label="Trajetória multivariável de DO2 indexada"><line x1="38" y1="150" x2="602" y2="150" stroke="#365063"/><polyline points="'+coords.join(' ')+'" fill="none" stroke="#55d6df" stroke-width="3" stroke-linecap="round"/>'+pts.map((p,i)=>'<circle cx="'+(38+i*564/Math.max(1,pts.length-1))+'" cy="'+(150-(p.estimatedOxygenDeliveryIndexMlMinM2-lo)/(hi-lo||1)*112)+'" r="2.5" fill="#72dfb2"/>').join('')+'</svg>';
 const keys=data.parameters;
 $('multivariateOutput').innerHTML='<div class="timeline-chart"><div class="chart-caption">DO₂ INDEXADA · TRAJETÓRIA DO MODELO</div>'+svg+'<div class="timeline-axis"><span>0 s</span><span>'+data.durationSeconds+' s</span></div></div><div class="timeline-meta"><span><b>'+pts.length+'</b> pontos</span><span><b>'+keys.length+'</b> variáveis simultâneas</span><span>Modelo '+esc(data.modelVersion)+'</span></div><div class="table-wrap timeline-table"><table class="data-table"><thead><tr><th>TEMPO (S)</th>'+keys.map(k=>'<th>'+esc(timelineLabels[k]||k)+'</th>').join('')+'<th>PAM EST.</th><th>DO₂ INDEXADA</th></tr></thead><tbody>'+pts.map(p=>'<tr><td>'+p.elapsedSeconds+'</td>'+keys.map(k=>'<td>'+fmt(p.parameterValues[k],2)+'</td>').join('')+'<td>'+fmt(p.estimatedMapMmHg,2)+'</td><td>'+fmt(p.estimatedOxygenDeliveryIndexMlMinM2,1)+'</td></tr>').join('')+'</tbody></table></div>';
 $('exportMultivariateCsv').disabled=false;$('exportMultivariateJson').disabled=false;
}
$('runMultivariate').addEventListener('click',async()=>{
 const form=$('simulationForm');if(!form.reportValidity()){toast('Revise primeiro os parâmetros-base.');return}
 const selected=[...document.querySelectorAll('.multi-enable:checked')];
 if(selected.length<2){toast('Selecione pelo menos duas variáveis.');return}
 const ramps=selected.map(box=>{const key=box.value,input=document.querySelector('.multi-target[data-param="'+key+'"]');let endValue=Number(input.value);if(key==='arterialSaturation')endValue/=100;return {parameter:key,endValue}});
 const durationSeconds=Number($('timelineDuration').value),stepSeconds=Number($('timelineStep').value);
 if(!Number.isInteger(durationSeconds)||durationSeconds<1||durationSeconds>3600||!Number.isInteger(stepSeconds)||stepSeconds<1||stepSeconds>300||Math.ceil(durationSeconds/stepSeconds)+1>361){toast('Duração ou intervalo inválido. Limite: 361 pontos.');return}
 const btn=$('runMultivariate');btn.disabled=true;btn.textContent='Calculando...';
 try{const data=await request('/api/v1/simulations/timeline/multivariate',{method:'POST',body:JSON.stringify({baseline:formPayload(),ramps,durationSeconds,stepSeconds})});renderMultivariate(data);toast('Cenário multivariável calculado.')}
 catch(e){toast('Falha no cenário multivariável: '+e.message)}
 finally{btn.disabled=false;btn.textContent='Gerar cenário multivariável'}
});
$('exportMultivariateCsv').addEventListener('click',()=>{const d=state.multivariateTimeline;if(!d)return;const keys=d.parameters,head=['elapsed_seconds',...keys,'estimated_map_mmhg','oxygen_delivery_ml_min','oxygen_delivery_index_ml_min_m2'];const rows=d.points.map(p=>[p.elapsedSeconds,...keys.map(k=>p.parameterValues[k]),p.estimatedMapMmHg,p.estimatedOxygenDeliveryMlMin,p.estimatedOxygenDeliveryIndexMlMinM2]);downloadText('perfusionlab-multivariate-scenario.csv',[head.join(','),...rows.map(r=>r.join(','))].join('\\n'),'text/csv;charset=utf-8')});
$('exportMultivariateJson').addEventListener('click',()=>{if(state.multivariateTimeline)downloadText('perfusionlab-multivariate-scenario.json',JSON.stringify(state.multivariateTimeline,null,2),'application/json')});
function drawConsoleCoupledVelocity(trajectory){
 const canvas=$('consoleCoupledVelocityChart');if(!canvas||!trajectory?.length)return;
 const width=Math.max(320,canvas.clientWidth||640),height=180,ratio=window.devicePixelRatio||1;
 canvas.width=width*ratio;canvas.height=height*ratio;
 const ctx=canvas.getContext('2d');ctx.scale(ratio,ratio);ctx.clearRect(0,0,width,height);
 const left=48,right=14,top=12,bottom=26,plotW=width-left-right,plotH=height-top-bottom;
 const yMin=Math.min(...trajectory.map(p=>p.axialVelocityMmPerSecond)),yMax=Math.max(...trajectory.map(p=>p.axialVelocityMmPerSecond));
 const xMin=trajectory[0].timeSeconds,xMax=trajectory[trajectory.length-1].timeSeconds;
 ctx.strokeStyle='rgba(155,179,194,.35)';ctx.beginPath();ctx.moveTo(left,top);ctx.lineTo(left,top+plotH);ctx.lineTo(width-right,top+plotH);ctx.stroke();
 ctx.fillStyle='#9bb3c2';ctx.font='10px system-ui';ctx.fillText(fmt(yMax,2),4,top+4);ctx.fillText(fmt(yMin,2),4,top+plotH+3);ctx.fillText('t (s)',width-42,height-5);
 ctx.strokeStyle='#72e0b1';ctx.lineWidth=2.5;ctx.beginPath();
 trajectory.forEach((p,i)=>{const x=left+(p.timeSeconds-xMin)/Math.max(xMax-xMin,1e-9)*plotW,y=top+(1-(p.axialVelocityMmPerSecond-yMin)/Math.max(yMax-yMin,1e-9))*plotH;if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y)});
 ctx.stroke();
}
function drawConsoleCoupledProfile(profile){
 const canvas=$('consoleCoupledChart');if(!canvas||!profile?.length)return;
 const width=Math.max(320,canvas.clientWidth||640),height=180,ratio=window.devicePixelRatio||1;
 canvas.width=width*ratio;canvas.height=height*ratio;
 const ctx=canvas.getContext('2d');ctx.scale(ratio,ratio);ctx.clearRect(0,0,width,height);
 const left=42,right=14,top=12,bottom=26,plotW=width-left-right,plotH=height-top-bottom;
 ctx.strokeStyle='rgba(155,179,194,.35)';ctx.beginPath();ctx.moveTo(left,top);ctx.lineTo(left,top+plotH);ctx.lineTo(width-right,top+plotH);ctx.stroke();
 ctx.fillStyle='#9bb3c2';ctx.font='10px system-ui';ctx.fillText('1,0',8,top+4);ctx.fillText('0,0',8,top+plotH+3);ctx.fillText('x (mm)',width-48,height-5);
 ctx.strokeStyle='#55d6df';ctx.lineWidth=2.5;ctx.beginPath();
 profile.forEach((p,i)=>{const x=left+p.xMm/Math.max(profile[profile.length-1].xMm,1e-9)*plotW,y=top+(1-p.concentration)*plotH;if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y)});
 ctx.stroke();
}
$('runCoupledConsole').addEventListener('click',async()=>{
 if(!state.supportsCoupled){$('coupledConsoleStatus').textContent='Configure uma API Spring Boot que exponha /api/v2/simulations/coupled-preview.';return}
 const form=$('simulationForm');if(!form.reportValidity())return;
 const n=id=>Number($(id).value),f=form.elements;
 const btn=$('runCoupledConsole');btn.disabled=true;$('coupledConsoleStatus').textContent='Calculando EDO e EDP...';$('coupledConsoleResults').hidden=true;
 const body={
  ode:{initialFlowLMin:3.0,targetFlowLMin:Number(f.pumpFlowLMin.value),hemoglobinGdL:Number(f.hemoglobinGdL.value),arterialSaturation:Number(f.arterialSaturationPct.value)/100,systemicVascularResistance:Number(f.systemicVascularResistance.value),bodySurfaceAreaM2:Number(f.bodySurfaceAreaM2.value),arterialComplianceMlPerMmHg:2.0,flowTimeConstantSeconds:3.0,venousPressureMmHg:5.0,durationSeconds:n('consoleCoupledDuration'),stepSeconds:.1},
  effectiveCrossSectionAreaMm2:n('consoleCoupledArea'),domainLengthMm:n('consoleCoupledLength'),dispersionMm2PerSecond:n('consoleCoupledDiffusion'),consumptionRatePerSecond:n('consoleCoupledReaction'),initialConcentration:n('consoleCoupledInitial'),inletConcentration:n('consoleCoupledInlet'),equilibriumConcentration:n('consoleCoupledEquilibrium'),spatialNodes:81
 };
 try{
  const result=await request('/api/v2/simulations/coupled-preview',{method:'POST',body:JSON.stringify(body)});
  $('consoleCoupledMaxVelocity').textContent=fmt(result.maximumAxialVelocityMmPerSecond,3);
  $('consoleCoupledMeanVelocity').textContent=fmt(result.meanAxialVelocityMmPerSecond,3);
  $('consoleCoupledOutlet').textContent=fmt(result.outletConcentration,4);
  $('consoleCoupledDt').textContent=fmt(result.pdeTimeStepSeconds,6);
  $('consoleCoupledDescription').textContent=result.modelVersion+' · '+result.spatialNodes+' nós · '+result.odeTrajectory.length+' pontos ODE. '+result.couplingDescription+' '+result.limitations;
  $('coupledConsoleResults').hidden=false;drawConsoleCoupledProfile(result.finalConcentrationProfile);drawConsoleCoupledVelocity(result.velocityTrajectory);
  $('coupledConsoleStatus').textContent='Simulação concluída. Resultados retornados pelo backend.';
 }catch(e){$('coupledConsoleStatus').textContent='Falha: '+e.message+'. Verifique se o backend Spring Boot atualizado está implantado.'}
 finally{btn.disabled=!state.supportsCoupled}
});
$('previewButton').addEventListener('click',async()=>{const form=$('simulationForm');if(!form.reportValidity())return;try{$('previewButton').disabled=true;$('resultState').textContent='CALCULANDO...';const r=await request('/api/v1/simulations/preview',{method:'POST',body:JSON.stringify(formPayload())});renderPreview(r)}catch(e){toast('Não foi possível calcular a prévia: '+e.message);$('resultState').textContent='ERRO'}finally{$('previewButton').disabled=false}});
$('simulationForm').addEventListener('submit',async e=>{e.preventDefault();if(!e.currentTarget.reportValidity())return;const btn=e.submitter;try{btn.disabled=true;btn.textContent='Enviando...';const job=await request('/api/v1/simulations/jobs',{method:'POST',body:JSON.stringify(formPayload())});toast('Simulação enviada · '+String(job.jobId).slice(0,8));watchJob(job.jobId);go('jobs');await loadJobs();await showJob(job.jobId)}catch(err){toast('Falha ao enviar simulação: '+err.message)}finally{btn.disabled=false;btn.textContent='▶ Enviar para o grid'}});
function watchJob(id){if(state.pollTimer)clearInterval(state.pollTimer);let attempts=0;state.pollTimer=setInterval(async()=>{attempts++;try{const j=await request('/api/v1/simulations/jobs/'+encodeURIComponent(id));if(state.activeJob?.jobId===id){state.activeJob=j;$('jobDetailContent').innerHTML='<div class="detail-kv"><div><small>JOB ID</small><strong class="mono">'+esc(j.jobId)+'</strong></div><div><small>ESTADO</small><strong>'+statusBadge(j.state)+'</strong></div><div><small>SUBMETIDO</small><strong>'+esc(date(j.submittedAt))+'</strong></div><div><small>INICIADO</small><strong>'+esc(date(j.startedAt))+'</strong></div><div><small>CONCLUÍDO</small><strong>'+esc(date(j.completedAt))+'</strong></div><div><small>ERRO</small><strong>'+esc(j.error||'—')+'</strong></div></div>'+(j.result?renderResult(j.result):'<p class="muted">Resultado ainda não disponível.</p>')}if(['SUCCEEDED','FAILED'].includes(j.state)||attempts>=90){clearInterval(state.pollTimer);state.pollTimer=null;await loadJobs();await loadOverview()}}catch{if(attempts>=90){clearInterval(state.pollTimer);state.pollTimer=null}}},2000)}
$('refreshButton').addEventListener('click',()=>refreshAll());$('jobsRefresh').addEventListener('click',loadJobs);$('clusterRefresh').addEventListener('click',loadCluster);
async function refreshAll(){await checkHealth();const active=document.querySelector('.view.active')?.id;if(active==='view-cluster')await loadCluster();else await loadOverview();toast('Dados atualizados')}
$('saveSettings').addEventListener('click',async()=>{apiBase=$('apiBaseInput').value.trim().replace(/\/$/,'');localStorage.setItem(API_KEY,apiBase);const ok=await checkHealth();$('settingsMessage').textContent=ok?'Conexão salva e API acessível.':'Endereço salvo, mas a API não respondeu. Verifique URL, CORS e disponibilidade.';toast(ok?'API conectada':'Não foi possível conectar à API')});
$('testConnection').addEventListener('click',async()=>{apiBase=$('apiBaseInput').value.trim().replace(/\/$/,'');$('settingsMessage').textContent=await checkHealth()?'API respondeu corretamente.':'Falha de conexão. Verifique o endpoint.'});
(async()=>{await checkHealth();await loadOverview();state.refreshTimer=setInterval(()=>{if(document.visibilityState==='visible'){checkHealth();if(document.querySelector('.view.active')?.id==='view-cluster')loadCluster();}},15000)})();
