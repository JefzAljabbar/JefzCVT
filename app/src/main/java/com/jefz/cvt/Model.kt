package com.jefz.cvt

data class Params(
    // Mesin & ECU
    val cc: Double = 149.3,
    val peakT: Double = 13.1,
    val trim: Double = 100.0,
    val rev: Double = 10500.0,
    val idle: Double = 1500.0,
    val tb: Double = 26.0,
    // Puli depan & roller
    val nRoll: Double = 6.0,
    val massG: Double = 7.5,
    val rrLow: Double = 44.0,
    val rrHigh: Double = 50.0,
    val ramp: Double = 30.0,
    val rdMin: Double = 23.5,
    val rdMax: Double = 46.0,
    // V-belt & puli belakang
    val belt: Double = 669.0,
    val cd: Double = 200.0,
    val rnMin: Double = 38.0,
    val rnMax: Double = 62.0,
    val spPre: Double = 460.0,
    val spK: Double = 5.6,
    val cam: Double = 45.0,
    val camR: Double = 44.0,
    // Sentri & gear final
    val engage: Double = 3500.0,
    val gear: Double = 9.3,
    val eff: Double = 0.88,
    // Ban & beban
    val tireW: Double = 90.0,
    val tireA: Double = 90.0,
    val rim: Double = 14.0,
    val motorKg: Double = 111.0,
    val riderKg: Double = 70.0,
    val cdA: Double = 0.55,
    val crr: Double = 0.018
)

class Motor(val name: String, val colorName: String, val colorHex: Long, val params: Params)

object Motors {
    val list: List<Motor> = listOf(
        Motor("Honda Vario 150 New Gen 1", "Silver", 0xFFC0C0C0, Params())
    )
}

class Field(
    val label: String,
    val unit: String,
    val get: (Params) -> Double,
    val set: (Params, Double) -> Params
)

val GROUPS: List<Pair<String, List<Field>>> = listOf(
    "Mesin & ECU" to listOf(
        Field("Kapasitas", "cc", { it.cc }, { p, v -> p.copy(cc = v) }),
        Field("Torsi puncak pabrik", "Nm", { it.peakT }, { p, v -> p.copy(peakT = v) }),
        Field("Trim ECU / injeksi", "%", { it.trim }, { p, v -> p.copy(trim = v) }),
        Field("Rev limiter ECU", "rpm", { it.rev }, { p, v -> p.copy(rev = v) }),
        Field("RPM idle", "rpm", { it.idle }, { p, v -> p.copy(idle = v) }),
        Field("Diameter throttle body", "mm", { it.tb }, { p, v -> p.copy(tb = v) })
    ),
    "CVT: puli depan & roller" to listOf(
        Field("Jumlah roller", "pcs", { it.nRoll }, { p, v -> p.copy(nRoll = v) }),
        Field("Berat roller", "gram", { it.massG }, { p, v -> p.copy(massG = v) }),
        Field("Posisi roller di low", "mm", { it.rrLow }, { p, v -> p.copy(rrLow = v) }),
        Field("Posisi roller di top", "mm", { it.rrHigh }, { p, v -> p.copy(rrHigh = v) }),
        Field("Kemiringan ramp puli depan", "derajat", { it.ramp }, { p, v -> p.copy(ramp = v) }),
        Field("Radius belt puli depan min", "mm", { it.rdMin }, { p, v -> p.copy(rdMin = v) }),
        Field("Radius belt puli depan max", "mm", { it.rdMax }, { p, v -> p.copy(rdMax = v) })
    ),
    "CVT: V-belt & puli belakang" to listOf(
        Field("Panjang V-belt", "mm", { it.belt }, { p, v -> p.copy(belt = v) }),
        Field("Panjang CVT (jarak sumbu)", "mm", { it.cd }, { p, v -> p.copy(cd = v) }),
        Field("Radius belt puli belakang min", "mm", { it.rnMin }, { p, v -> p.copy(rnMin = v) }),
        Field("Radius belt puli belakang max", "mm", { it.rnMax }, { p, v -> p.copy(rnMax = v) }),
        Field("Pre-load pegas CVT", "N", { it.spPre }, { p, v -> p.copy(spPre = v) }),
        Field("Kekakuan pegas CVT", "N/mm", { it.spK }, { p, v -> p.copy(spK = v) }),
        Field("Sudut torque cam", "derajat", { it.cam }, { p, v -> p.copy(cam = v) }),
        Field("Radius torque cam", "mm", { it.camR }, { p, v -> p.copy(camR = v) })
    ),
    "Sentri & gear final" to listOf(
        Field("RPM mesin saat sentri nyangkut", "rpm", { it.engage }, { p, v -> p.copy(engage = v) }),
        Field("Rasio gear final", ":1", { it.gear }, { p, v -> p.copy(gear = v) }),
        Field("Efisiensi transmisi", "0-1", { it.eff }, { p, v -> p.copy(eff = v) })
    ),
    "Ban & beban" to listOf(
        Field("Lebar ban belakang", "mm", { it.tireW }, { p, v -> p.copy(tireW = v) }),
        Field("Aspect ratio ban", "%", { it.tireA }, { p, v -> p.copy(tireA = v) }),
        Field("Diameter velg", "inci", { it.rim }, { p, v -> p.copy(rim = v) }),
        Field("Berat motor", "kg", { it.motorKg }, { p, v -> p.copy(motorKg = v) }),
        Field("Berat badan pengendara", "kg", { it.riderKg }, { p, v -> p.copy(riderKg = v) }),
        Field("Hambatan angin (CdA)", "m2", { it.cdA }, { p, v -> p.copy(cdA = v) }),
        Field("Koefisien gelinding ban", "", { it.crr }, { p, v -> p.copy(crr = v) })
    )
)
