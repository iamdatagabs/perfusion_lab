package com.perfusionlab.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF07131F)
private val Panel = Color(0xFF102638)
private val Cyan = Color(0xFF55D6DF)
private val Mint = Color(0xFF72E0B1)

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
    var pumpFlow by remember { mutableFloatStateOf(4.8f) }
    var hemoglobin by remember { mutableFloatStateOf(10.0f) }
    var saturation by remember { mutableFloatStateOf(0.98f) }

    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("PERFUSIONLAB", color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Laboratório de simulação", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Modelo dinâmico ODE/PDE · MVP Android", color = Mint)

            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Novo cenário", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    ParameterSlider("Fluxo-alvo da bomba", pumpFlow, 1f..8f, "%.1f L/min".format(pumpFlow)) { pumpFlow = it }
                    ParameterSlider("Hemoglobina", hemoglobin, 5f..18f, "%.1f g/dL".format(hemoglobin)) { hemoglobin = it }
                    ParameterSlider("Saturação arterial", saturation, 0.70f..1f, "%.2f".format(saturation)) { saturation = it }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { /* API ODE será conectada na próxima etapa */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Executar simulação")
                    }
                    OutlinedButton(onClick = { pumpFlow = 4.8f; hemoglobin = 10f; saturation = .98f }, modifier = Modifier.fillMaxWidth()) {
                        Text("Restaurar parâmetros")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Motor matemático", style = MaterialTheme.typography.titleLarge)
                    Text("EDOs: dinâmica temporal de fluxo, pressão e balanço de oxigênio.", color = Color.LightGray)
                    Text("EDPs: transporte espacial em módulo experimental, previsto para etapa posterior.", color = Color.LightGray)
                    Text("A série temporal será exibida após integração com a API.", color = Mint)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2418))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Uso educacional e de pesquisa", color = Color(0xFFFFD58A), fontWeight = FontWeight.Bold)
                    Text("Protótipo sem validação clínica. Não utilizar para decisões assistenciais, ajuste de bomba ou manejo de pacientes.", color = Color(0xFFFFE8C2))
                }
            }
            Text("PerfusionLab · versão 0.1.0", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, formatted: String, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(formatted, color = Mint, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}
