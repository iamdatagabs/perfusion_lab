import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';

const $=id=>document.getElementById(id);
const root=$('heartViewport');
const scene=new THREE.Scene();scene.background=new THREE.Color('#091925');
const camera=new THREE.PerspectiveCamera(38,1,.1,1000);camera.position.set(8,5.8,10.5);
const renderer=new THREE.WebGLRenderer({antialias:true});renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.outputColorSpace=THREE.SRGBColorSpace;root.appendChild(renderer.domElement);
const controls=new OrbitControls(camera,renderer.domElement);controls.enableDamping=true;controls.target.set(0,.2,0);
scene.add(new THREE.HemisphereLight(0xd7f7ff,0x182638,2.1));
const key=new THREE.DirectionalLight(0xffffff,2.8);key.position.set(5,8,7);scene.add(key);
const rim=new THREE.DirectionalLight(0x55d6df,1.8);rim.position.set(-7,3,-6);scene.add(rim);
const grid=new THREE.GridHelper(10,20,0x31536a,0x20384a);grid.position.y=-2.5;scene.add(grid);
const heart=new THREE.Group();scene.add(heart);
const mat=(color,opacity=.9)=>new THREE.MeshStandardMaterial({color,roughness:.38,metalness:.04,transparent:opacity<1,opacity});
const chambers=[];
function ellipsoid(name,pos,size,color){const mesh=new THREE.Mesh(new THREE.SphereGeometry(1,40,28),mat(color));mesh.name=name;mesh.position.set(...pos);mesh.scale.set(...size);heart.add(mesh);chambers.push(mesh);return mesh;}
const ra=ellipsoid('Átrio direito',[-1.05,.85,.05],[.68,.72,.62],0xb95767);
const rv=ellipsoid('Ventrículo direito',[-.95,-.55,.08],[.72,1.08,.68],0xc65b69);
const la=ellipsoid('Átrio esquerdo',[1.05,.85,-.05],[.68,.72,.62],0x4aa9b9);
const lv=ellipsoid('Ventrículo esquerdo',[.95,-.55,-.08],[.78,1.12,.72],0x3e91a5);
const septum=new THREE.Mesh(new THREE.BoxGeometry(.16,2.25,1.05),mat(0xd9a3a1,.8));septum.position.set(0,-.05,0);heart.add(septum);
function tube(points,color,r=.09){const curve=new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p)));const mesh=new THREE.Mesh(new THREE.TubeGeometry(curve,48,r,12,false),mat(color));heart.add(mesh);return curve;}
const paths=[
tube([[-1.05,1.05,.1],[-1.65,1.5,.1],[-2.3,1.35,.1],[-2.55,.9,.1]],0xb95767),
tube([[-.95,-.8,.1],[-1.15,-1.55,.1],[-1.15,-2.0,.1],[0,-2.0,.1]],0xdf626b),
tube([[0,-2.0,.1],[1.15,-2.0,.1],[1.15,-1.45,.1],[.95,-.8,.1]],0x4aa9b9),
tube([[1.05,1.05,-.1],[1.65,1.5,-.1],[2.3,1.35,-.1],[2.55,.9,-.1]],0x4aa9b9),
tube([[.95,-.8,-.1],[1.15,-1.55,-.1],[1.15,-2.0,-.1],[2.1,-2.0,-.1]],0xdf535c),
tube([[.95,-.1,-.1],[1.7,.15,-.1],[2.15,.75,-.1],[2.35,1.4,-.1]],0xdf535c)
];
const labels=[];
function label(text,pos){const canvas=document.createElement('canvas');canvas.width=256;canvas.height=64;const c=canvas.getContext('2d');c.fillStyle='#eaf7fb';c.font='bold 27px system-ui';c.textAlign='center';c.fillText(text,128,42);const tex=new THREE.CanvasTexture(canvas);const sprite=new THREE.Sprite(new THREE.SpriteMaterial({map:tex,transparent:true}));sprite.position.set(...pos);sprite.scale.set(1.35,.34,1);heart.add(sprite);labels.push(sprite);}
label('ÁTRIO DIREITO',[-1.05,1.85,.1]);label('VENTRÍCULO DIREITO',[-1.05,-1.8,.1]);label('ÁTRIO ESQUERDO',[1.05,1.85,.1]);label('VENTRÍCULO ESQUERDO',[1.05,-1.85,.1]);
const particleMat=mat(0xf5d58a);const particles=[];
paths.forEach((curve,k)=>{for(let i=0;i<8;i++){const p=new THREE.Mesh(new THREE.SphereGeometry(.055,10,8),particleMat);heart.add(p);particles.push({mesh:p,curve,offset:i/8,route:k});}});
let flow=4.8,playing=true,showLabels=true,modelResult=null;
function resize(){const w=root.clientWidth,h=root.clientHeight;camera.aspect=w/h;camera.updateProjectionMatrix();renderer.setSize(w,h,false)}new ResizeObserver(resize).observe(root);resize();
function rebuild(){const scale=Number($('scale').value),wall=Number($('wall').value),sep=Number($('septum').value);chambers.forEach((m,i)=>{m.scale.set((i===2||i===0?.68:.75)*scale*(1+wall*.18),(i===1||i===3?1.08:.72)*scale*(1+wall*.3),.66*scale)});septum.scale.x=sep/.18;heart.scale.y=scale;labels.forEach(x=>x.visible=showLabels);$('scaleOut').textContent=scale.toFixed(2).replace('.',',')+'×';$('wallOut').textContent=wall.toFixed(2).replace('.',',');$('septumOut').textContent=sep.toFixed(2).replace('.',',');}
function metrics(){flow=Number($('flow').value);const hb=Number($('hb').value),sat=Number($('sat').value),svr=Number($('svr').value),bsa=1.9;const map=flow*svr/80,do2=flow*10*hb*1.34*(sat/100),do2i=do2/bsa;const velocity=(flow*1e6/60)/Number($('area').value);$('flowOut').textContent=flow.toFixed(1).replace('.',',')+' L/min';$('hbOut').textContent=hb.toFixed(1).replace('.',',')+' g/dL';$('satOut').textContent=sat+'%';$('svrOut').textContent=svr.toFixed(0);$('map').textContent=map.toFixed(1);$('do2').textContent=do2.toFixed(0);$('do2i').textContent=do2i.toFixed(0);$('velocity').textContent=velocity.toFixed(1)+' mm/s';$('hemoStatus').textContent='PAM = Q × RVS / 80; DO₂ = Q × Hb × 1,34 × SaO₂. Estimativas matemáticas ilustrativas.';particles.forEach(p=>p.mesh.scale.setScalar(.65+Math.min(flow/8,.8)*.6));}
['flow','hb','sat','svr','area'].forEach(id=>$(id).addEventListener('input',metrics));
['scale','wall','septum'].forEach(id=>$(id).addEventListener('input',rebuild));
$('resetGeometry').addEventListener('click',()=>{$('scale').value=1;$('wall').value=.12;$('septum').value=.18;rebuild()});
$('toggleLabels').addEventListener('click',()=>{showLabels=!showLabels;labels.forEach(x=>x.visible=showLabels);$('toggleLabels').textContent='Rótulos: '+(showLabels?'ON':'OFF')});
$('playFlow').addEventListener('click',()=>{playing=!playing;$('playFlow').textContent=playing?'Pausar fluxo':'Retomar fluxo'});
function drawProfile(points){const canvas=$('profileChart'),w=canvas.clientWidth||700,h=150,dpr=devicePixelRatio||1;canvas.width=w*dpr;canvas.height=h*dpr;const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);ctx.clearRect(0,0,w,h);ctx.strokeStyle='#294252';ctx.beginPath();ctx.moveTo(38,12);ctx.lineTo(38,h-24);ctx.lineTo(w-12,h-24);ctx.stroke();if(!points?.length)return;const vals=points.map(p=>p.concentration),lo=Math.min(...vals),hi=Math.max(...vals);ctx.strokeStyle='#55d6df';ctx.lineWidth=2.5;ctx.beginPath();points.forEach((p,i)=>{const x=38+i/(points.length-1)*(w-52),y=12+(1-(p.concentration-lo)/Math.max(hi-lo,1e-8))*(h-38);i?ctx.lineTo(x,y):ctx.moveTo(x,y)});ctx.stroke();ctx.fillStyle='#9bb3c2';ctx.font='10px system-ui';ctx.fillText('Concentração',42,11);ctx.fillText('x: 0 → L',w-72,h-6);}
$('runCoupled').addEventListener('click',async()=>{const button=$('runCoupled');button.disabled=true;$('runStatus').textContent='Executando modelo ODE e solver PDE…';try{const base=$('apiBase').value.trim().replace(/\/$/,'');const payload={effectiveCrossSectionAreaMm2:Number($('area').value),domainLengthMm:Number($('length').value),dispersionMm2PerSecond:Number($('diffusion').value),consumptionRatePerSecond:.025,initialConcentration:.2,inletConcentration:Number($('sat').value)/100,equilibriumConcentration:.25,spatialNodes:81,ode:{initialFlowLMin:3,targetFlowLMin:Number($('flow').value),hemoglobinGdL:Number($('hb').value),arterialSaturation:Number($('sat').value)/100,systemicVascularResistance:Number($('svr').value),bodySurfaceAreaM2:1.9,arterialComplianceMlPerMmHg:2,flowTimeConstantSeconds:3,venousPressureMmHg:5,durationSeconds:Number($('duration').value),stepSeconds:.1}};const response=await fetch(base+'/api/v2/simulations/coupled-preview',{method:'POST',headers:{'Content-Type':'application/json','X-Correlation-ID':crypto.randomUUID?.()||'workbench-preview'},body:JSON.stringify(payload)});if(!response.ok)throw new Error((await response.text())||response.statusText);modelResult=await response.json();$('outlet').textContent=Number(modelResult.outletConcentration).toFixed(4);$('pdeDt').textContent=Number(modelResult.pdeTimeStepSeconds).toExponential(2);$('modelInfo').textContent=modelResult.modelVersion+' · '+modelResult.spatialNodes+' nós · '+modelResult.odeTrajectory.length+' pontos ODE. '+modelResult.couplingDescription+' '+modelResult.limitations;drawProfile(modelResult.finalConcentrationProfile);$('runStatus').textContent='Solver concluído. A animação 3D usa o fluxo calculado como referência de velocidade; o perfil PDE é 1D.';}catch(error){$('runStatus').textContent='Falha: '+error.message+'. Verifique URL, CORS e disponibilidade da API.';}finally{button.disabled=false;}});
let clock=0;function animate(){requestAnimationFrame(animate);const dt=.016;if(playing){let rate=.12+flow/30;if(modelResult?.velocityTrajectory?.length){const series=modelResult.velocityTrajectory,duration=series[series.length-1].timeSeconds||1;const simTime=clock%duration;let nearest=series[0];for(const point of series){if(point.timeSeconds>simTime)break;nearest=point;}rate=Math.max(.06,Math.min(1.8,Math.abs(nearest.axialVelocityMmPerSecond)/40));}clock+=dt*rate;particles.forEach(p=>{const t=(clock+p.offset)%1;const pos=p.curve.getPointAt(t);p.mesh.position.copy(pos);});}controls.update();renderer.render(scene,camera);}metrics();rebuild();animate();