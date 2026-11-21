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
