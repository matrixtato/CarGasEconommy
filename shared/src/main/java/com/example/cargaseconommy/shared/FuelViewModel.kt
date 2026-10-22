package com.example.cargaseconommy.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cargaseconommy.shared.database.FuelDao
import com.example.cargaseconommy.shared.database.FuelEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class FuelViewModel(private val fuelDao: FuelDao? = null) : ViewModel() {
    private val _alcoholPrice = MutableStateFlow("")
    val alcoholPrice = _alcoholPrice.asStateFlow()

    private val _gasolinePrice = MutableStateFlow("")
    val gasolinePrice = _gasolinePrice.asStateFlow()

    private val _alcoholPercentage = MutableStateFlow("32")
    val alcoholPercentage = _alcoholPercentage.asStateFlow()

    private val _alcoholConsumption = MutableStateFlow("8")
    val alcoholConsumption = _alcoholConsumption.asStateFlow()

    private val _gasolineConsumption = MutableStateFlow("10")
    val gasolineConsumption = _gasolineConsumption.asStateFlow()

    private val _currentKm = MutableStateFlow("")
    val currentKm = _currentKm.asStateFlow()

    private val _litersAdded = MutableStateFlow("")
    val litersAdded = _litersAdded.asStateFlow()

    private val _totalPaid = MutableStateFlow("")
    val totalPaid = _totalPaid.asStateFlow()

    private val _selectedMapApp = MutableStateFlow("google")
    val selectedMapApp = _selectedMapApp.asStateFlow()

    private val _lastEntry = MutableStateFlow<FuelEntry?>(null)
    val lastEntry = _lastEntry.asStateFlow()

    val allEntries: StateFlow<List<FuelEntry>> = (fuelDao?.getAllEntries() ?: flowOf(emptyList()))
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            fuelDao?.getLastEntry()?.let {
                _lastEntry.value = it
            }
        }
    }

    val comparisonResult = combine(
        _alcoholPrice, _gasolinePrice, _alcoholPercentage, _alcoholConsumption, _gasolineConsumption
    ) { alcohol, gasoline, percentage, alcCons, gasCons ->
        calculateDetailedComparison(alcohol, gasoline, percentage, alcCons, gasCons)
    }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ComparisonData())

    val realConsumptionResult = combine(
        _currentKm, _lastEntry, _totalPaid, _litersAdded, _alcoholPrice, _gasolinePrice
    ) { args ->
        val km = args[0] as String
        val last = args[1] as? FuelEntry
        val total = args[2] as String
        val liters = args[3] as String
        val alc = args[4] as String
        val gas = args[5] as String
        calculateRealConsumption(km, last, total, liters, alc, gas)
    }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RealConsumptionData())

    fun updateAlcoholPrice(price: String) { _alcoholPrice.value = cleanNumeric(price) }
    fun updateGasolinePrice(price: String) { _gasolinePrice.value = cleanNumeric(price) }
    fun updateAlcoholPercentage(pct: String) { _alcoholPercentage.value = pct.replace(" ", "") }
    fun updateAlcoholConsumption(cons: String) { _alcoholConsumption.value = cleanNumeric(cons) }
    fun updateGasolineConsumption(cons: String) { _gasolineConsumption.value = cleanNumeric(cons) }
    fun updateCurrentKm(km: String) { _currentKm.value = cleanNumeric(km) }
    fun updateLitersAdded(liters: String) { _litersAdded.value = cleanNumeric(liters) }
    fun updateTotalPaid(total: String) { _totalPaid.value = cleanNumeric(total) }
    fun selectMapApp(app: String) { _selectedMapApp.value = app }

    private fun cleanNumeric(value: String) = value.replace(" ", "").replace(",", ".")

    private fun calculateDetailedComparison(alc: String, gas: String, pct: String, alcC: String, gasC: String): ComparisonData {
        val aPrice = alc.toDoubleOrNull() ?: return ComparisonData()
        val gPrice = gas.toDoubleOrNull() ?: return ComparisonData()
        val aCons = alcC.toDoubleOrNull() ?: 8.0
        val gCons = gasC.toDoubleOrNull() ?: 10.0
        
        val costPerKmAlc = if (aCons > 0) aPrice / aCons else 0.0
        val costPerKmGas = if (gCons > 0) gPrice / gCons else 0.0
        
        val distAlc = if (aPrice > 0) (100.0 / aPrice) * aCons else 0.0
        val distGas = if (gPrice > 0) (100.0 / gPrice) * gCons else 0.0

        val recommendation = if (costPerKmAlc < costPerKmGas) "ÁLCOOL" else "GASOLINA"
        val savings = Math.abs(costPerKmGas - costPerKmAlc) * 100
        
        return ComparisonData(
            recommendation = recommendation, 
            costPerKmAlc = costPerKmAlc, 
            costPerKmGas = costPerKmGas,
            distAlc = distAlc,
            distGas = distGas,
            savingsPer100Km = savings,
            isValid = true
        )
    }

    private fun calculateRealConsumption(kmStr: String, last: FuelEntry?, totalStr: String, litersStr: String, alcPrice: String, gasPrice: String): RealConsumptionData {
        val currentKmValue = kmStr.toDoubleOrNull() ?: return RealConsumptionData()
        val total = totalStr.toDoubleOrNull() ?: 0.0
        val liters = litersStr.toDoubleOrNull() ?: 0.0
        
        var warningMsg = ""
        if (total > 0 && liters > 0) {
            val pricePerLiter = total / liters
            val refAlc = alcPrice.toDoubleOrNull() ?: 0.0
            val refGas = gasPrice.toDoubleOrNull() ?: 0.0
            
            val diffAlc = Math.abs(pricePerLiter - refAlc)
            val diffGas = Math.abs(pricePerLiter - refGas)
            
            if ((refAlc > 0 && diffAlc > 0.05) && (refGas > 0 && diffGas > 0.05)) {
                warningMsg = "Aviso: Preço da bomba (R$ ${String.format(Locale.US, "%.2f", pricePerLiter)}) diverge!"
            }
        }

        if (last == null) return RealConsumptionData(msg = "Salve o 1º abastecimento para histórico.", warning = warningMsg)
        
        val distance = currentKmValue - last.km
        if (distance <= 0) return RealConsumptionData(msg = "KM deve ser maior que anterior.", warning = warningMsg)
        
        val avg = distance / last.liters
        return RealConsumptionData(
            avg = avg,
            distance = distance,
            isValid = true,
            msg = "Média real: ${String.format(Locale.US, "%.2f", avg)} Km/L",
            warning = warningMsg
        )
    }

    fun saveEntry(fuelType: String) {
        val km = _currentKm.value.toDoubleOrNull() ?: return
        val liters = _litersAdded.value.toDoubleOrNull() ?: return
        val alcPrice = _alcoholPrice.value.toDoubleOrNull() ?: 0.0
        val gasPrice = _gasolinePrice.value.toDoubleOrNull() ?: 0.0
        val totalPaidValue = _totalPaid.value.toDoubleOrNull() ?: 0.0
        val pct = _alcoholPercentage.value.toDoubleOrNull() ?: 32.0

        val entry = FuelEntry(
            timestamp = System.currentTimeMillis(),
            alcoholPrice = alcPrice,
            gasolinePrice = gasPrice,
            totalPaid = totalPaidValue,
            km = km,
            liters = liters,
            fuelType = fuelType,
            alcoholPercentage = pct
        )

        viewModelScope.launch {
            fuelDao?.insertEntry(entry)
            _lastEntry.value = entry
            _currentKm.value = ""
            _litersAdded.value = ""
            _totalPaid.value = ""
        }
    }
}

data class RealConsumptionData(
    val avg: Double = 0.0, 
    val distance: Double = 0.0, 
    val isValid: Boolean = false, 
    val msg: String = "", 
    val warning: String = ""
)

data class ComparisonData(
    val recommendation: String = "", 
    val costPerKmAlc: Double = 0.0, 
    val costPerKmGas: Double = 0.0, 
    val distAlc: Double = 0.0,
    val distGas: Double = 0.0,
    val savingsPer100Km: Double = 0.0,
    val isValid: Boolean = false
)
