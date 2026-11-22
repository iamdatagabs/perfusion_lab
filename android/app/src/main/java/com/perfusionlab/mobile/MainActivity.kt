package com.perfusionlab.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max

private val Navy = Color(0xFF07131F)
private val Panel = Color(0xFF102638)
private val Cyan = Color(0xFF55D6DF)
private val Mint = Color(0xFF72E0B1)
private val Muted = Color(0xFF9FB4C2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan, secondary = Mint, background = Navy, surface = Panel,
                onBackground = Color(0xFFEDF7FB), onSurface = Color(0xFFEDF7FB)
            )) { PerfusionLabApp() }
        }
    }
}

@Composable
private fun PerfusionLabApp() {
    var targetFlow by remember { mutableFloatStateOf(4.8f) }
    var initialFlow by remember { mutableFloatStateOf(3.0f) }
    var hemoglobin by remember { mutableFloatStateOf(10f) }
    var saturation by remember { mutableFloatStateOf(.98f) }
    var svr by remember { mutableFloatStateOf(1200f) }
    var bsa by remember { mutableFloatStateOf(1.9f) }
    var compliance by remember { mutableFloatStateOf(2f) }
    var tau by remember { mutableFloatStateOf(3f) }
    var duration by remember { mutableFloatStateOf(60f) }
    var result by remember { mutableStateOf<OdeSimulationResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = Navy) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("PERFUSIONLAB", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Laboratório de simulação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Motor ODE · integração Android/API", color = Mint)
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Parâmetros do cenário", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    ParameterSlider("Fluxo inicial", initialFlow, .1f..10f, "%.1f L/min".format(initialFlow)) { initialFlow = it }
                    ParameterSlider("Fluxo-alvo", targetFlow, .1f..10f, "%.1f L/min".format(targetFlow)) { targetFlow = it }
                    ParameterSlider("Hemoglobina", hemoglobin, 1f..25f, "%.1f g/dL".format(hemoglobin)) { hemoglobin = it }
                    ParameterSlider("Saturação arterial", saturation, .5f..1f, "%.2f".format(saturation)) { saturation = it }
                    ParameterSlider("Resistência sistêmica", svr, 100f..5000f, "%.0f dyn·s/cm⁵".format(svr)) { svr = it }
                    ParameterSlider("Superfície corporal", bsa, .3f..3f, "%.2f m²".format(bsa)) { bsa = it }
                    ParameterSlider("Complacência arterial", compliance, .1f..10f, "%.2f mL/mmHg".format(compliance)) { compliance = it }
                    ParameterSlider("Constante de tempo do fluxo", tau, .1f..60f, "%.1f s".format(tau)) { tau = it }
                    ParameterSlider("Duração", duration, 1f..300f, "%.0f s".format(duration)) { duration = it }
                    Button(enabled = !loading, onClick = {
                        loading = true; error = null; result = null
                        scope.launch {
                            try {
                                result = ApiClient.service.runOdePreview(OdeSimulationRequest(
                                    initialFlow.toDouble(), targetFlow.toDouble(), hemoglobin.toDouble(),
                                    saturation.toDouble(), svr.toDouble(), bsa.toDouble(), compliance.toDouble(),
                                    tau.toDouble(), 5.0, duration.toDouble(), .1
                                ))
                            } catch (e: Exception) {
                                error = e.message ?: "Não foi possível executar a simulação."
                            } finally { loading = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        if (loading) CircularProgressIndicator() else Text("Executar simulação")
                    }
                    OutlinedButton(onClick = {
                        initialFlow=3f; targetFlow=4.8f; hemoglobin=10f; saturation=.98f
                        svr=1200f; bsa=1.9f; compliance=2f; tau=3f; duration=60f
                        result=null; error=null
                    }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar parâmetros") }
                }
            }
            error?.let { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2024))) {
                Text("Falha na simulação: $it", color = Color(0xFFFFC7C7), modifier = Modifier.padding(16.dp))
            } }
            result?.let { simulation ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Resultados", style = MaterialTheme.typography.titleLarge)
                        Text("Modelo ${simulation.modelVersion} · ${simulation.integrator}", color = Mint)
                        simulation.points.lastOrNull()?.let { last ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Metric("Fluxo final", "%.2f L/min".format(last.flowLMin))
                                Metric("Pressão final", "%.1f mmHg".format(last.pressureMmHg))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Metric("DO₂", "%.0f mL/min".format(last.oxygenDeliveryMlMin))
                                Metric("DO₂i", "%.0f mL/min/m²".format(last.oxygenDeliveryIndexMlMinM2))
                            }
                        }
                        Text("Fluxo ao longo do tempo", fontWeight = FontWeight.SemiBold)
                        TimeSeriesChart(simulation.points.map { it.timeSeconds.toFloat() to it.flowLMin.toFloat() }, Cyan)
                        Text("Pressão ao longo do tempo", fontWeight = FontWeight.SemiBold)
                        TimeSeriesChart(simulation.points.map { it.timeSeconds.toFloat() to it.pressureMmHg.toFloat() }, Mint)
                        Text(simulation.modelDescription, color = Muted)
                        Text(simulation.limitations, color = Color(0xFFFFD58A), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2418))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Uso educacional e de pesquisa", color = Color(0xFFFFD58A), fontWeight = FontWeight.Bold)
                    Text("Protótipo sem validação clínica. Não utilizar para decisões assistenciais, ajuste de bomba ou manejo de pacientes.", color = Color(0xFFFFE8C2))
                }
            }
            Text("PerfusionLab · versão 0.2.0", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold, color = Cyan)
    }
}

@Composable
private fun TimeSeriesChart(points: List<Pair<Float, Float>>, lineColor: Color) {
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
        if (points.size < 2) return@Canvas
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val rangeY = max(.0001f, maxY - minY)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = size.width * index / (points.size - 1).toFloat()
            val y = size.height - ((point.second - minY) / rangeY) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Muted.copy(alpha = .35f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun ParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, formatted: String, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(formatted, color = Mint, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}
