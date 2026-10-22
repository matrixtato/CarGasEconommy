package com.example.cargaseconommy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cargaseconommy.shared.ComparisonData
import com.example.cargaseconommy.shared.FuelViewModel
import com.example.cargaseconommy.shared.RealConsumptionData
import com.example.cargaseconommy.shared.database.FuelDatabase
import com.example.cargaseconommy.shared.database.FuelEntry
import com.example.cargaseconommy.ui.theme.CarGasEconommyTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Removido enableEdgeToEdge() temporariamente para testar estabilidade em centrais multimídia
        // enableEdgeToEdge() 

        setContent {
            CarGasEconommyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val context = LocalContext.current
                    
                    // Inicialização do DAO de forma preguiçosa para evitar travar o onCreate
                    val daoState = remember {
                        mutableStateOf<com.example.cargaseconommy.shared.database.FuelDao?>(null)
                    }
                    val errorState = remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        try {
                            // Carrega o banco em uma thread secundária implicitamente pelo Room/Coroutine
                            val database = FuelDatabase.getDatabase(context.applicationContext)
                            daoState.value = database.fuelDao()
                        } catch (e: Exception) {
                            Log.e("CarGas", "Erro ao carregar banco", e)
                            errorState.value = e.message
                        }
                    }

                    when {
                        errorState.value != null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Erro ao abrir banco: ${errorState.value}", color = Color.Red, modifier = Modifier.padding(16.dp))
                            }
                        }
                        daoState.value == null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFFFD700))
                            }
                        }
                        else -> {
                            Scaffold(containerColor = Color.Black) { innerPadding ->
                                FuelInputScreen(
                                    modifier = Modifier.padding(innerPadding), 
                                    factory = FuelViewModelFactory(daoState.value!!)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class FuelViewModelFactory(private val dao: com.example.cargaseconommy.shared.database.FuelDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FuelViewModel(dao) as T
    }
}

@Composable
fun FuelInputScreen(modifier: Modifier = Modifier, factory: FuelViewModelFactory, viewModel: FuelViewModel = viewModel(factory = factory)) {
    val selectedMap by viewModel.selectedMapApp.collectAsState()
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Dica de Consumo", color = Color(0xFFFFD700)) },
            text = { 
                Text(
                    "Esta informação é uma estimativa de consumo real.\n\n" +
                    "Dica: Use a luz de reserva como referência. Quando ela acender, informe o KM, abasteça e anote os litros.\n\n" +
                    "Na próxima vez que a luz acender, informe o novo KM. O cálculo será a diferença entre os KMs sobre os litros abastecidos anteriormente.",
                    color = Color.White,
                    fontSize = 14.sp
                ) 
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("ENTENDI", color = Color(0xFFFFD700))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo()
            UnifiedFuelCard(viewModel)
            RefillSection(viewModel) { showInfoDialog = true }
            HistoryTable(viewModel)
            Spacer(Modifier.height(16.dp))
            NavigationFooter(selectedMap, onMapSelected = { viewModel.selectMapApp(it) }) {
                launchNavigation(context, it)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AppLogo() {
    val context = LocalContext.current
    val logoId = remember { context.resources.getIdentifier("logo", "drawable", context.packageName) }
    
    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
        if (logoId != 0) {
            Image(
                painter = painterResource(id = logoId),
                contentDescription = "Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("POSTO ECONÔMICO", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
fun UnifiedFuelCard(viewModel: FuelViewModel) {
    val alcoholPrice by viewModel.alcoholPrice.collectAsState()
    val gasolinePrice by viewModel.gasolinePrice.collectAsState()
    val alcoholPercentage by viewModel.alcoholPercentage.collectAsState()
    val alcoholCons by viewModel.alcoholConsumption.collectAsState()
    val gasolineCons by viewModel.gasolineConsumption.collectAsState()
    val result by viewModel.comparisonResult.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFB8860B), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preços nos Postos e Consumo do Carro", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFD700))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = alcoholPrice, onValueChange = { viewModel.updateAlcoholPrice(it) }, label = { Text("Álcool (R$)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                OutlinedTextField(value = gasolinePrice, onValueChange = { viewModel.updateGasolinePrice(it) }, label = { Text("Gasolina (R$)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                OutlinedTextField(value = alcoholPercentage, onValueChange = { viewModel.updateAlcoholPercentage(it) }, label = { Text("% Álc", fontSize = 10.sp) }, modifier = Modifier.width(60.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = alcoholCons, onValueChange = { viewModel.updateAlcoholConsumption(it) }, label = { Text("Média Álc (Km/L)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                OutlinedTextField(value = gasolineCons, onValueChange = { viewModel.updateGasolineConsumption(it) }, label = { Text("Média Gas (Km/L)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
            }
        }
    }

    if (result.isValid) { DetailedComparisonCard(result) }
}

@Composable
fun DetailedComparisonCard(data: ComparisonData) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFFD700), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "ABASTEÇA COM: ${data.recommendation}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFB8860B))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Rendimento por Real (Ref. R$ 100,00):", fontSize = 11.sp, color = Color.Gray)
                    val alcPerReal = if (data.costPerKmAlc > 0) 1.0/data.costPerKmAlc else 0.0
                    val gasPerReal = if (data.costPerKmGas > 0) 1.0/data.costPerKmGas else 0.0
                    
                    Text(
                        text = "Álcool: ${String.format(Locale.US, "%.2f", alcPerReal)} Km/R$ = Total ${data.distAlc.toInt()} KMs",
                        fontSize = 12.sp,
                        color = if(data.recommendation == "ÁLCOOL") Color(0xFFFFD700) else Color.White,
                        fontWeight = if(data.recommendation == "ÁLCOOL") FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "Gasolina: ${String.format(Locale.US, "%.2f", gasPerReal)} Km/R$ = Total ${data.distGas.toInt()} KMs",
                        fontSize = 12.sp,
                        color = if(data.recommendation == "GASOLINA") Color(0xFFFFD700) else Color.White,
                        fontWeight = if(data.recommendation == "GASOLINA") FontWeight.Bold else FontWeight.Normal
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Economia:", fontSize = 11.sp, color = Color.Gray)
                    Text("R$ ${String.format(Locale.US, "%.2f", data.savingsPer100Km)}", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    Text("a cada 100Km", fontSize = 9.sp, color = Color.Gray)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            val diff = Math.abs(data.distGas - data.distAlc)
            Text(
                text = "Diferença: ${String.format(Locale.US, "%.1f", diff)} Km a mais com ${data.recommendation}.",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RefillSection(viewModel: FuelViewModel, onShowInfo: () -> Unit) {
    val currentKm by viewModel.currentKm.collectAsState()
    val totalPaid by viewModel.totalPaid.collectAsState()
    val litersAdded by viewModel.litersAdded.collectAsState()
    val realCons by viewModel.realConsumptionResult.collectAsState()
    val focusManager = LocalFocusManager.current

    Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFFD700), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Registro de Abastecimento", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFD700))
                IconButton(onClick = onShowInfo, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, null, tint = Color.Gray)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = currentKm, onValueChange = { viewModel.updateCurrentKm(it) }, label = { Text("KM Atual", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                OutlinedTextField(value = totalPaid, onValueChange = { viewModel.updateTotalPaid(it) }, label = { Text("Valor (R$)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                OutlinedTextField(value = litersAdded, onValueChange = { viewModel.updateLitersAdded(it) }, label = { Text("Litros", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
            }

            if (realCons.warning.isNotEmpty()) { Text(realCons.warning, fontWeight = FontWeight.SemiBold, color = Color.Red, fontSize = 11.sp) }
            if (realCons.msg.isNotEmpty()) { Text(realCons.msg, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 12.sp) }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveEntry("ALCOOL") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))) { Text("Salvar Álc", fontSize = 11.sp) }
                Button(onClick = { viewModel.saveEntry("GASOLINA") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) { Text("Salvar Gas", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun HistoryTable(viewModel: FuelViewModel) {
    val entries by viewModel.allEntries.collectAsState()
    if (entries.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Histórico (Últimos 5)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                entries.take(5).forEachIndexed { index, entry ->
                    val avg = if (index < entries.size - 1) {
                        val prev = entries[index + 1]
                        val dist = entry.km - prev.km
                        if (dist > 0 && prev.liters > 0) dist / prev.liters else 0.0
                    } else 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${entry.km.toInt()} KM", modifier = Modifier.weight(1.2f), fontSize = 11.sp, color = Color.White)
                        Text("${entry.liters}L", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color.White)
                        Text(if (avg > 0) String.format(Locale.US, "%.2f", avg) + " Km/L" else "--", modifier = Modifier.weight(1.5f), fontSize = 11.sp, color = Color(0xFFFFD700))
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationFooter(selectedMap: String, onMapSelected: (String) -> Unit, onLaunch: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Buscar postos mais próximos:", fontSize = 12.sp, color = Color.Gray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MapButton("Google Maps", selectedMap == "google") { 
                onMapSelected("google")
                onLaunch("google")
            }
            MapButton("Waze", selectedMap == "waze") { 
                onMapSelected("waze")
                onLaunch("waze")
            }
        }
    }
}

@Composable
fun MapButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFFFD700) else Color(0xFF1A1A1A),
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFFFD700))
    ) {
        Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

fun launchNavigation(context: android.content.Context, type: String) {
    val uri = if (type == "google") Uri.parse("google.navigation:q=Posto+de+Combustivel") else Uri.parse("waze://?q=Posto+de+Combustivel&navigate=yes")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "Erro GPS", Toast.LENGTH_SHORT).show() }
}
