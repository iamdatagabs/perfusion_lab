const ids = ["flow", "hb", "sat", "svr", "bsa"];

export function calculateMetrics(values) {
  const map = values.flow * values.svr / 80;
  const do2 = values.flow * 10 * values.hb * 1.34 * (values.sat / 100);
  return { map, do2, do2i: do2 / values.bsa };
}

export function calculateDeviation(current, reference) {
  const pairs = [
    ["PAM", current.map, reference.map],
    ["DO₂", current.do2, reference.do2],
    ["DO₂i", current.do2i, reference.do2i]
  ];
  const deviations = pairs.map(([name, value, baseline]) => ({
    name,
    percent: Math.abs(value - baseline) / Math.max(Math.abs(baseline), 1e-9) * 100
  }));
  const score = Math.sqrt(deviations.reduce((sum, item) => sum + item.percent ** 2, 0) / deviations.length);
  return { score, deviations };
}

export function classifyDeviation(score, stableLimit, criticalLimit) {
  if (![score, stableLimit, criticalLimit].every(Number.isFinite) || stableLimit < 0 || criticalLimit <= stableLimit) {
    throw new RangeError("Invalid mathematical indicator thresholds");
  }
  return score <= stableLimit ? "stable" : score > criticalLimit ? "critical" : "unstable";
}

const fmt = (value, digits = 1) => Number(value).toLocaleString("pt-BR", {
  minimumFractionDigits: digits,
  maximumFractionDigits: digits
});

function initializeSimulator(root = document) {
  const byId = id => root.getElementById(id);
  if (!ids.every(id => byId(id)) || !byId("mathFlag")) return false;
  let reference = null;

  function readValues() {
    return Object.fromEntries(ids.map(id => [id, Number(byId(id).value)]));
  }

  function update() {
    const values = readValues();
    byId("flowOut").textContent = fmt(values.flow) + " L/min";
    byId("hbOut").textContent = fmt(values.hb) + " g/dL";
    byId("satOut").textContent = Math.round(values.sat) + "%";
    byId("svrOut").textContent = Math.round(values.svr).toLocaleString("pt-BR");
    byId("bsaOut").textContent = fmt(values.bsa) + " m²";

    const metrics = calculateMetrics(values);
    byId("map").textContent = fmt(metrics.map);
    byId("do2").textContent = fmt(metrics.do2, 0);
    byId("do2i").textContent = fmt(metrics.do2i, 0);
    updateIndicator(metrics);
  }

  function updateIndicator(current) {
    const panel = byId("mathFlag");
    const stableLimit = Number(byId("mathStableLimit").value);
    const criticalLimit = Number(byId("mathCriticalLimit").value);
    if (!reference || !Number.isFinite(stableLimit) || !Number.isFinite(criticalLimit) || stableLimit < 0 || criticalLimit <= stableLimit) {
      panel.className = "math-flag pending";
      byId("mathFlagTitle").textContent = "Revise os limites matemáticos";
      byId("mathFlagDescription").textContent = "O limite crítico deve ser maior que o limite estável.";
      byId("mathFlagScore").textContent = "—";
      byId("mathFlagDetails").textContent = "Indicador indisponível até corrigir os limites.";
      byId("mathFlagThresholds").textContent = "Verifique os dois limites.";
      return;
    }
    const { score, deviations } = calculateDeviation(current, reference);
    const status = classifyDeviation(score, stableLimit, criticalLimit);
    const labels = {
      stable: "ESTÁVEL · BAIXO DESVIO MATEMÁTICO",
      unstable: "INSTÁVEL · DESVIO MATEMÁTICO INTERMEDIÁRIO",
      critical: "CRÍTICO · ALTO DESVIO MATEMÁTICO"
    };
    const descriptions = {
      stable: "Resultados próximos da referência matemática definida.",
      unstable: "Resultados com afastamento intermediário da referência.",
      critical: "Resultados com afastamento elevado da referência."
    };
    panel.className = "math-flag " + status;
    byId("mathFlagTitle").textContent = labels[status];
    byId("mathFlagDescription").textContent = descriptions[status];
    byId("mathFlagScore").textContent = fmt(score) + "%";
    byId("mathFlagDetails").textContent = deviations.map(item => item.name + " " + fmt(item.percent) + "%").join(" · ");
    byId("mathFlagThresholds").textContent = "Estável ≤ " + fmt(stableLimit) + "% · Crítico > " + fmt(criticalLimit) + "%";
  }

  byId("setMathReference").addEventListener("click", () => {
    reference = calculateMetrics(readValues());
    byId("mathFlagReference").textContent = "Nova referência definida com os parâmetros atuais · " + new Date().toLocaleTimeString("pt-BR");
    update();
  });
  ids.forEach(id => byId(id).addEventListener("input", update));
  ["mathStableLimit", "mathCriticalLimit"].forEach(id => byId(id).addEventListener("input", update));
  reference = calculateMetrics(readValues());
  update();

  if (typeof IntersectionObserver !== "undefined") {
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add("visible");
        observer.unobserve(entry.target);
      }
    }), { threshold: 0.12 });
    root.querySelectorAll(".reveal").forEach(element => observer.observe(element));
  } else {
    root.querySelectorAll(".reveal").forEach(element => element.classList.add("visible"));
  }
  return true;
}

if (typeof document !== "undefined") {
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => initializeSimulator());
  else initializeSimulator();
}


const COUPLED_API_KEY = "perfusionlab.coupledApiBase";
function initializeCoupledExperiment(root = document) {
  const byId = id => root.getElementById(id);
  const required = ["coupledApiBase", "saveCoupledApi", "runCoupled", "coupledResults", "coupledProfileChart"];
  if (!required.every(id => byId(id))) return false;
  const saved = localStorage.getItem(COUPLED_API_KEY) || "";
  byId("coupledApiBase").value = saved;

  const setApiState = () => {
    const value = byId("coupledApiBase").value.trim().replace(/\/$/, "");
    const valid = /^https?:\/\/[a-z0-9.-]+(?::\d+)?(?:\/.*)?$/i.test(value);
    byId("runCoupled").disabled = !valid;
    byId("coupledApiStatus").textContent = valid
      ? "Endpoint informado. A chamada será feita em /api/v2/simulations/coupled-preview."
      : "Backend não configurado. A API Node de prévia existente não oferece este endpoint.";
  };

  byId("coupledApiBase").addEventListener("input", setApiState);
  byId("saveCoupledApi").addEventListener("click", () => {
    const value = byId("coupledApiBase").value.trim().replace(/\/$/, "");
    if (!/^https?:\/\/[a-z0-9.-]+(?::\d+)?(?:\/.*)?$/i.test(value)) {
      byId("coupledApiStatus").textContent = "Informe uma URL HTTP(S) válida para a API Spring Boot.";
      return;
    }
    localStorage.setItem(COUPLED_API_KEY, value);
    byId("coupledApiBase").value = value;
    setApiState();
    byId("coupledApiStatus").textContent = "Endpoint salvo neste navegador: " + value;
  });

  const fmtCoupled = (value, digits = 3) => Number(value).toLocaleString("pt-BR", {
    minimumFractionDigits: digits, maximumFractionDigits: digits
  });

  function drawProfile(profile) {
    const canvas = byId("coupledProfileChart");
    const rect = canvas.getBoundingClientRect();
    const width = Math.max(320, rect.width || 640);
    const height = 180;
    const ratio = window.devicePixelRatio || 1;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    const ctx = canvas.getContext("2d");
    ctx.scale(ratio, ratio);
    ctx.clearRect(0, 0, width, height);
    const left = 42, right = 14, top = 12, bottom = 28;
    const plotW = width - left - right, plotH = height - top - bottom;
    ctx.strokeStyle = "rgba(155,179,194,.35)";
    ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(left, top); ctx.lineTo(left, top + plotH); ctx.lineTo(width-right, top+plotH); ctx.stroke();
    ctx.fillStyle = "#9bb3c2"; ctx.font = "10px system-ui";
    ctx.fillText("1,0", 8, top+4); ctx.fillText("0,0", 8, top+plotH+3);
    ctx.fillText("x (mm)", width-48, height-6);
    ctx.strokeStyle = "#55d6df"; ctx.lineWidth = 2.5; ctx.beginPath();
    profile.forEach((point, index) => {
      const x = left + (point.xMm / Math.max(profile[profile.length-1].xMm, 1e-9)) * plotW;
      const y = top + (1 - point.concentration) * plotH;
      if (index === 0) ctx.moveTo(x,y); else ctx.lineTo(x,y);
    });
    ctx.stroke();
  }

  byId("runCoupled").addEventListener("click", async () => {
    const base = byId("coupledApiBase").value.trim().replace(/\/$/, "");
    const button = byId("runCoupled");
    button.disabled = true;
    byId("coupledRunStatus").textContent = "Calculando trajetória ODE e transporte PDE...";
    byId("coupledResults").hidden = true;
    const number = id => Number(byId(id).value);
    const values = {
      effectiveCrossSectionAreaMm2: number("coupledArea"),
      domainLengthMm: number("coupledLength"),
      dispersionMm2PerSecond: number("coupledDiffusion"),
      consumptionRatePerSecond: number("coupledReaction"),
      initialConcentration: number("coupledInitial"),
      inletConcentration: number("coupledInlet"),
      equilibriumConcentration: number("coupledEquilibrium"),
      spatialNodes: 81,
      ode: {
        initialFlowLMin: 3.0,
        targetFlowLMin: Number(byId("flow").value),
        hemoglobinGdL: Number(byId("hb").value),
        arterialSaturation: Number(byId("sat").value) / 100,
        systemicVascularResistance: Number(byId("svr").value),
        bodySurfaceAreaM2: Number(byId("bsa").value),
        arterialComplianceMlPerMmHg: 2.0,
        flowTimeConstantSeconds: 3.0,
        venousPressureMmHg: 5.0,
        durationSeconds: number("coupledDuration"),
        stepSeconds: 0.1
      }
    };
    try {
      const response = await fetch(base + "/api/v2/simulations/coupled-preview", {
        method: "POST",
        headers: {"Content-Type":"application/json", "X-Correlation-ID": globalThis.crypto?.randomUUID?.() || "coupled-mvp"},
        body: JSON.stringify(values)
      });
      if (!response.ok) throw new Error((await response.text()) || response.statusText);
      const result = await response.json();
      byId("coupledMaxVelocity").textContent = fmtCoupled(result.maximumAxialVelocityMmPerSecond) + " mm/s";
      byId("coupledMeanVelocity").textContent = fmtCoupled(result.meanAxialVelocityMmPerSecond) + " mm/s";
      byId("coupledOutlet").textContent = fmtCoupled(result.outletConcentration, 4);
      byId("coupledDt").textContent = fmtCoupled(result.pdeTimeStepSeconds, 6);
      byId("coupledModelInfo").textContent = result.modelVersion + " · " + result.spatialNodes + " nós · " + result.odeTrajectory.length + " pontos ODE. " + result.couplingDescription + " " + result.limitations;
      byId("coupledResults").hidden = false;
      drawProfile(result.finalConcentrationProfile);
      byId("coupledRunStatus").textContent = "Simulação concluída. Resultados recebidos da API.";
    } catch (error) {
      byId("coupledRunStatus").textContent = "Falha na execução: " + error.message + ". Confirme se a API Spring Boot está implantada e permite CORS para este domínio.";
    } finally {
      button.disabled = false;
      setApiState();
    }
  });
  setApiState();
  return true;
}

if (typeof document !== "undefined") {
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => initializeCoupledExperiment());
  else initializeCoupledExperiment();
}
