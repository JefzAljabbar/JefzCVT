package com.jefz.cvt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    var motorIdx by remember { mutableIntStateOf(0) }
    var params by remember { mutableStateOf(Motors.list[0].params) }
    var settings by remember { mutableStateOf(false) }
    BackHandler(enabled = settings) { settings = false }
    if (settings) {
        SettingsScreen(Motors.list[motorIdx], params, { params = it }) { settings = false }
    } else {
        MainScreen(Motors.list, motorIdx, params, { motorIdx = it; params = Motors.list[it].params }) { settings = true }
    }
}

fun fmt(v: Double): String =
    if (v == Math.floor(v) && abs(v) < 1e9) v.toLong().toString() else v.toString()

@Composable
fun MainScreen(
    motors: List<Motor>,
    motorIdx: Int,
    params: Params,
    onMotor: (Int) -> Unit,
    onSettings: () -> Unit
) {
    var gas by remember { mutableFloatStateOf(0f) }
    var uphill by remember { mutableStateOf(false) }
    var angle by remember { mutableFloatStateOf(8f) }
    val slope = if (uphill) angle.toDouble() else 0.0
    val res = remember(params, gas, slope) { Sim.run(params, gas.toDouble(), slope) }
    val motor = motors[motorIdx]
    val tpsV = 0.5 + 4.0 * gas / 100.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("JefzCVT", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onSettings) { Text("Pengaturan") }
        }

        MotorCard(motors, motor, params, onMotor)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pedal gas: ${gas.roundToInt()}%", fontWeight = FontWeight.SemiBold)
                Slider(value = gas, onValueChange = { gas = it }, valueRange = 0f..100f)
                Text(
                    "Bukaan throttle (TPS) ${gas.roundToInt()}% • ${"%.2f".format(tpsV)} V",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mode jalan", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !uphill, onClick = { uphill = false }, label = { Text("Datar") })
                    FilterChip(selected = uphill, onClick = { uphill = true }, label = { Text("Tanjakan") })
                }
                if (uphill) {
                    val grade = tan(Math.toRadians(angle.toDouble())) * 100.0
                    Slider(value = angle, onValueChange = { angle = it }, valueRange = 0f..30f)
                    Text(
                        "Sudut ${"%.1f".format(angle)}° ≈ kemiringan ${"%.0f".format(grade)}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (res == null) {
                    Text("Input tidak valid, cek Pengaturan.")
                } else {
                    Text("${"%.1f".format(res.kmh)} km/h", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(res.status, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Metric("RPM mesin", "${res.rpm.roundToInt()}")
                        Metric("Rasio CVT", "${"%.2f".format(res.ratio)}")
                        Metric("RPM sentri", "${res.clutchRpm.roundToInt()}")
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Metric("Throttle", "${gas.roundToInt()}%")
                        Metric("Torsi mesin", "${"%.1f".format(res.torque)} Nm")
                        Metric("Total massa", "${fmt(params.motorKg + params.riderKg)} kg")
                    }
                }
            }
        }

        if (res != null) {
            val c = res.curve
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LineChart(
                        "RPM mesin vs kecepatan", c.kmh,
                        listOf(ChartSeries("RPM", Color(0xFFFF8A65), c.rpm)), res.kmh
                    )
                    LineChart(
                        "Rasio CVT vs kecepatan", c.kmh,
                        listOf(ChartSeries("Rasio", Color(0xFF4FC3F7), c.ratio)), res.kmh
                    )
                    LineChart(
                        "Gaya dorong vs hambatan (N)", c.kmh,
                        listOf(
                            ChartSeries("Dorong", Color(0xFF81C784), c.thrust),
                            ChartSeries("Hambatan", Color(0xFFE57373), c.resist)
                        ), res.kmh
                    )
                }
            }
        }
        Text(
            "Hasil berupa estimasi dari model fisika. Data default adalah perkiraan, sesuaikan di Pengaturan.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RowScope.Metric(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MotorCard(motors: List<Motor>, motor: Motor, params: Params, onMotor: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp).background(Color(motor.colorHex), CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(motor.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "Warna ${motor.colorName} • ${fmt(params.cc)} cc",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                TextButton(onClick = { open = true }) { Text("Ganti") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    motors.forEachIndexed { i, m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { open = false; onMotor(i) })
                    }
                }
            }
        }
    }
}

class ChartSeries(val name: String, val color: Color, val ys: DoubleArray)

@Composable
fun LineChart(title: String, xs: DoubleArray, series: List<ChartSeries>, markX: Double) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (sr in series) Text("● ${sr.name}", color = sr.color, fontSize = 11.sp)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val padL = 44.dp.toPx()
            val padB = 18.dp.toPx()
            val padT = 6.dp.toPx()
            val padR = 8.dp.toPx()
            val w = size.width - padL - padR
            val h = size.height - padT - padB
            val xMin = xs.first()
            val xMax = if (xs.last() > xMin) xs.last() else xMin + 1.0
            var yMax = series.maxOf { it.ys.max() }
            val yMin = min(0.0, series.minOf { it.ys.min() })
            if (yMax <= yMin) yMax = yMin + 1.0
            val paint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = 10.sp.toPx()
                isAntiAlias = true
            }
            fun px(x: Double): Float = padL + ((x - xMin) / (xMax - xMin)).toFloat() * w
            fun py(y: Double): Float = padT + h - ((y - yMin) / (yMax - yMin)).toFloat() * h

            for (i in 0..3) {
                val yv = yMin + (yMax - yMin) * i / 3.0
                val yy = py(yv)
                drawLine(gridColor, Offset(padL, yy), Offset(padL + w, yy), strokeWidth = 1.dp.toPx())
                val label = if (abs(yv) >= 100.0) "${yv.roundToInt()}" else "${"%.1f".format(yv)}"
                drawContext.canvas.nativeCanvas.drawText(label, 2.dp.toPx(), yy + 4.dp.toPx(), paint)
            }
            for (i in 0..2) {
                val xv = xMin + (xMax - xMin) * i / 2.0
                drawContext.canvas.nativeCanvas.drawText(
                    "${xv.roundToInt()}", px(xv) - 6.dp.toPx(), size.height - 2.dp.toPx(), paint
                )
            }
            drawContext.canvas.nativeCanvas.drawText("km/h", padL + w - 24.dp.toPx(), padT + 10.dp.toPx(), paint)

            for (sr in series) {
                val path = Path()
                for (i in xs.indices) {
                    val x = px(xs[i])
                    val y = py(sr.ys[i])
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, sr.color, style = Stroke(width = 2.dp.toPx()))
            }
            if (markX >= xMin && markX <= xMax) {
                drawLine(
                    Color.White,
                    Offset(px(markX), padT),
                    Offset(px(markX), padT + h),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(motor: Motor, params: Params, onChange: (Params) -> Unit, onBack: () -> Unit) {
    var resetKey by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Kembali") }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Pengaturan: ${motor.name}", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = { onChange(motor.params); resetKey++ }) {
            Text("Reset semua ke default pabrik")
        }
        for (group in GROUPS) {
            val title = group.first
            val fields = group.second
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
            TextButton(onClick = {
                var np = params
                for (f in fields) np = f.set(np, f.get(motor.params))
                onChange(np)
                resetKey++
            }) { Text("Reset grup ini") }
            for (f in fields) {
                FieldRow(f, params, resetKey, onChange)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FieldRow(f: Field, params: Params, resetKey: Int, onChange: (Params) -> Unit) {
    var text by remember(resetKey) { mutableStateOf(fmt(f.get(params))) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            val v = t.replace(',', '.').toDoubleOrNull()
            if (v != null) onChange(f.set(params, v))
        },
        label = { Text(if (f.unit.isEmpty()) f.label else "${f.label} (${f.unit})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
