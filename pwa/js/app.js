(() => {
  "use strict";

  const STORAGE_KEY = "cargaseconommy.entries";
  const MAP_KEY = "cargaseconommy.selectedMap";

  const $ = (id) => document.getElementById(id);

  const els = {
    alcoholPrice: $("alcoholPrice"),
    gasolinePrice: $("gasolinePrice"),
    alcoholPercentage: $("alcoholPercentage"),
    alcoholConsumption: $("alcoholConsumption"),
    gasolineConsumption: $("gasolineConsumption"),
    currentKm: $("currentKm"),
    totalPaid: $("totalPaid"),
    litersAdded: $("litersAdded"),
    comparisonCard: $("comparisonCard"),
    comparisonRecommendation: $("comparisonRecommendation"),
    lineAlc: $("lineAlc"),
    lineGas: $("lineGas"),
    savingsValue: $("savingsValue"),
    diffLine: $("diffLine"),
    warningMsg: $("warningMsg"),
    resultMsg: $("resultMsg"),
    historyCard: $("historyCard"),
    historyRows: $("historyRows"),
    saveAlcoholBtn: $("saveAlcoholBtn"),
    saveGasolineBtn: $("saveGasolineBtn"),
    googleMapBtn: $("googleMapBtn"),
    wazeMapBtn: $("wazeMapBtn"),
    infoBtn: $("infoBtn"),
    infoModal: $("infoModal"),
    infoCloseBtn: $("infoCloseBtn"),
    toast: $("toast"),
  };

  // ---------- Persistência (equivalente ao Room/FuelDao do app Android) ----------

  function loadEntries() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  function saveEntries(entries) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  }

  let entries = loadEntries().sort((a, b) => b.timestamp - a.timestamp);

  function lastEntry() {
    return entries.length ? entries[0] : null;
  }

  // ---------- Utilitários numéricos (equivalentes ao cleanNumeric/toDoubleOrNull) ----------

  function cleanNumeric(value) {
    return value.replace(/\s/g, "").replace(",", ".");
  }

  function toDoubleOrNull(value) {
    if (value === undefined || value === null || value === "") return null;
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  function fmt(n, digits = 2) {
    return n.toLocaleString("en-US", { minimumFractionDigits: digits, maximumFractionDigits: digits });
  }

  // ---------- Regras de negócio (porte fiel de FuelViewModel.kt) ----------

  function calculateDetailedComparison(alc, gas, pct, alcC, gasC) {
    const aPrice = toDoubleOrNull(alc);
    const gPrice = toDoubleOrNull(gas);
    if (aPrice === null || gPrice === null) return { isValid: false };

    const aCons = toDoubleOrNull(alcC) ?? 8.0;
    const gCons = toDoubleOrNull(gasC) ?? 10.0;

    const costPerKmAlc = aCons > 0 ? aPrice / aCons : 0.0;
    const costPerKmGas = gCons > 0 ? gPrice / gCons : 0.0;

    const distAlc = aPrice > 0 ? (100.0 / aPrice) * aCons : 0.0;
    const distGas = gPrice > 0 ? (100.0 / gPrice) * gCons : 0.0;

    const recommendation = costPerKmAlc < costPerKmGas ? "ÁLCOOL" : "GASOLINA";
    const savings = Math.abs(costPerKmGas - costPerKmAlc) * 100;

    return {
      recommendation,
      costPerKmAlc,
      costPerKmGas,
      distAlc,
      distGas,
      savingsPer100Km: savings,
      isValid: true,
    };
  }

  function calculateRealConsumption(kmStr, last, totalStr, litersStr, alcPrice, gasPrice) {
    const currentKmValue = toDoubleOrNull(kmStr);
    if (currentKmValue === null) return { isValid: false, msg: "", warning: "" };

    const total = toDoubleOrNull(totalStr) ?? 0.0;
    const liters = toDoubleOrNull(litersStr) ?? 0.0;

    let warningMsg = "";
    if (total > 0 && liters > 0) {
      const pricePerLiter = total / liters;
      const refAlc = toDoubleOrNull(alcPrice) ?? 0.0;
      const refGas = toDoubleOrNull(gasPrice) ?? 0.0;

      const diffAlc = Math.abs(pricePerLiter - refAlc);
      const diffGas = Math.abs(pricePerLiter - refGas);

      if (refAlc > 0 && diffAlc > 0.05 && refGas > 0 && diffGas > 0.05) {
        warningMsg = `Aviso: Preço da bomba (R$ ${fmt(pricePerLiter)}) diverge!`;
      }
    }

    if (!last) return { isValid: false, msg: "Salve o 1º abastecimento para histórico.", warning: warningMsg };

    const distance = currentKmValue - last.km;
    if (distance <= 0) return { isValid: false, msg: "KM deve ser maior que anterior.", warning: warningMsg };

    const avg = distance / last.liters;
    return {
      avg,
      distance,
      isValid: true,
      msg: `Média real: ${fmt(avg)} Km/L`,
      warning: warningMsg,
    };
  }

  // ---------- Renderização ----------

  function renderComparison() {
    const result = calculateDetailedComparison(
      els.alcoholPrice.value,
      els.gasolinePrice.value,
      els.alcoholPercentage.value,
      els.alcoholConsumption.value,
      els.gasolineConsumption.value
    );

    if (!result.isValid) {
      els.comparisonCard.hidden = true;
      return;
    }

    els.comparisonCard.hidden = false;
    els.comparisonRecommendation.textContent = `ABASTEÇA COM: ${result.recommendation}`;

    const alcPerReal = result.costPerKmAlc > 0 ? 1.0 / result.costPerKmAlc : 0.0;
    const gasPerReal = result.costPerKmGas > 0 ? 1.0 / result.costPerKmGas : 0.0;

    els.lineAlc.textContent = `Álcool: ${fmt(alcPerReal)} Km/R$ = Total ${Math.trunc(result.distAlc)} KMs`;
    els.lineAlc.classList.toggle("active", result.recommendation === "ÁLCOOL");

    els.lineGas.textContent = `Gasolina: ${fmt(gasPerReal)} Km/R$ = Total ${Math.trunc(result.distGas)} KMs`;
    els.lineGas.classList.toggle("active", result.recommendation === "GASOLINA");

    els.savingsValue.textContent = `R$ ${fmt(result.savingsPer100Km)}`;

    const diff = Math.abs(result.distGas - result.distAlc);
    els.diffLine.textContent = `Diferença: ${fmt(diff, 1)} Km a mais com ${result.recommendation}.`;
  }

  function renderRealConsumption() {
    const result = calculateRealConsumption(
      els.currentKm.value,
      lastEntry(),
      els.totalPaid.value,
      els.litersAdded.value,
      els.alcoholPrice.value,
      els.gasolinePrice.value
    );

    els.warningMsg.hidden = !result.warning;
    els.warningMsg.textContent = result.warning || "";

    els.resultMsg.hidden = !result.msg;
    els.resultMsg.textContent = result.msg || "";
  }

  function renderHistory() {
    if (entries.length === 0) {
      els.historyCard.hidden = true;
      els.historyRows.innerHTML = "";
      return;
    }

    els.historyCard.hidden = false;
    const top5 = entries.slice(0, 5);
    els.historyRows.innerHTML = top5
      .map((entry, index) => {
        let avg = 0;
        if (index < entries.length - 1) {
          const prev = entries[index + 1];
          const dist = entry.km - prev.km;
          if (dist > 0 && prev.liters > 0) avg = dist / prev.liters;
        }
        const avgText = avg > 0 ? `${fmt(avg)} Km/L` : "--";
        return `<div class="history-row">
          <span class="km">${Math.trunc(entry.km)} KM</span>
          <span class="liters">${entry.liters}L</span>
          <span class="avg">${avgText}</span>
        </div>`;
      })
      .join("");
  }

  function renderMapSelection() {
    const selected = localStorage.getItem(MAP_KEY) || "google";
    els.googleMapBtn.classList.toggle("selected", selected === "google");
    els.wazeMapBtn.classList.toggle("selected", selected === "waze");
  }

  function renderAll() {
    renderComparison();
    renderRealConsumption();
    renderHistory();
    renderMapSelection();
  }

  // ---------- Toast ----------

  let toastTimer = null;
  function showToast(message) {
    els.toast.textContent = message;
    els.toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => els.toast.classList.remove("show"), 2500);
  }

  // ---------- Eventos: inputs numéricos (equivalente ao updateX do ViewModel) ----------

  function bindNumericField(input, { percentage = false } = {}) {
    input.addEventListener("input", () => {
      const cursor = input.selectionStart;
      const before = input.value;
      input.value = percentage ? before.replace(/\s/g, "") : cleanNumeric(before);
      if (cursor !== null && input.value.length === before.length) {
        input.setSelectionRange(cursor, cursor);
      }
      renderAll();
    });
  }

  bindNumericField(els.alcoholPrice);
  bindNumericField(els.gasolinePrice);
  bindNumericField(els.alcoholPercentage, { percentage: true });
  bindNumericField(els.alcoholConsumption);
  bindNumericField(els.gasolineConsumption);
  bindNumericField(els.currentKm);
  bindNumericField(els.totalPaid);
  bindNumericField(els.litersAdded);

  // ---------- Salvar abastecimento (equivalente a saveEntry) ----------

  function saveEntry(fuelType) {
    const km = toDoubleOrNull(els.currentKm.value);
    const liters = toDoubleOrNull(els.litersAdded.value);
    if (km === null || liters === null) {
      showToast("Informe KM Atual e Litros para salvar.");
      return;
    }

    const alcPrice = toDoubleOrNull(els.alcoholPrice.value) ?? 0.0;
    const gasPrice = toDoubleOrNull(els.gasolinePrice.value) ?? 0.0;
    const totalPaidValue = toDoubleOrNull(els.totalPaid.value) ?? 0.0;
    const pct = toDoubleOrNull(els.alcoholPercentage.value) ?? 32.0;

    const entry = {
      timestamp: Date.now(),
      alcoholPrice: alcPrice,
      gasolinePrice: gasPrice,
      totalPaid: totalPaidValue,
      km,
      liters,
      fuelType,
      alcoholPercentage: pct,
    };

    entries.unshift(entry);
    saveEntries(entries);

    els.currentKm.value = "";
    els.litersAdded.value = "";
    els.totalPaid.value = "";

    showToast(`Abastecimento (${fuelType === "ALCOOL" ? "Álcool" : "Gasolina"}) salvo.`);
    renderAll();
  }

  els.saveAlcoholBtn.addEventListener("click", () => saveEntry("ALCOOL"));
  els.saveGasolineBtn.addEventListener("click", () => saveEntry("GASOLINA"));

  // ---------- Navegação (equivalente a launchNavigation) ----------

  function launchNavigation(type) {
    localStorage.setItem(MAP_KEY, type);
    renderMapSelection();

    const query = encodeURIComponent("Posto de Combustível");
    const url =
      type === "google"
        ? `https://www.google.com/maps/search/?api=1&query=${query}`
        : `https://waze.com/ul?q=${query}&navigate=yes`;

    window.open(url, "_blank", "noopener");
  }

  els.googleMapBtn.addEventListener("click", () => launchNavigation("google"));
  els.wazeMapBtn.addEventListener("click", () => launchNavigation("waze"));

  // ---------- Modal de dica ----------

  els.infoBtn.addEventListener("click", () => els.infoModal.classList.add("show"));
  els.infoCloseBtn.addEventListener("click", () => els.infoModal.classList.remove("show"));
  els.infoModal.addEventListener("click", (e) => {
    if (e.target === els.infoModal) els.infoModal.classList.remove("show");
  });

  // ---------- Valores padrão (equivalentes aos MutableStateFlow iniciais) ----------

  if (!els.alcoholPercentage.value) els.alcoholPercentage.value = "32";
  if (!els.alcoholConsumption.value) els.alcoholConsumption.value = "8";
  if (!els.gasolineConsumption.value) els.gasolineConsumption.value = "10";

  renderAll();

  // ---------- PWA: registro do service worker ----------

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {});
    });
  }

  // ---------- PWA: prompt de instalação ----------

  const installBanner = $("installBanner");
  const installBtn = $("installBtn");
  let deferredPrompt = null;

  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferredPrompt = e;
    installBanner.classList.add("show");
  });

  installBtn.addEventListener("click", async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    deferredPrompt = null;
    installBanner.classList.remove("show");
  });

  window.addEventListener("appinstalled", () => {
    installBanner.classList.remove("show");
  });
})();
