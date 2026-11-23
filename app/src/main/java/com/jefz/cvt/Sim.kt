package com.jefz.cvt

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class Curve(
    val kmh: DoubleArray,
    val rpm: DoubleArray,
    val ratio: DoubleArray,
    val thrust: DoubleArray,
    val resist: DoubleArray
)

class SimResult(
    val kmh: Double,
    val rpm: Double,
    val ratio: Double,
    val clutchRpm: Double,
    val torque: Double,
    val status: String,
    val curve: Curve
)

object Sim {
    private val RPM = doubleArrayOf(1500.0, 2000.0, 3000.0, 4000.0, 5000.0, 6000.0, 7000.0, 8000.0, 8500.0, 9000.0, 9500.0, 10000.0, 10500.0, 12000.0)
    private val NM = doubleArrayOf(8.0, 9.5, 11.5, 12.8, 13.1, 12.6, 11.8, 11.0, 10.56, 9.5, 8.2, 6.5, 4.0, 0.5)
    private const val G = 9.81
    private const val RHO = 1.18

    private class Pt(val r: Double, val rpm: Double, val f: Double)

    private fun curveTorque(rpm: Double): Double {
        if (rpm <= RPM[0]) return NM[0]
        for (i in 1 until RPM.size) {
            if (rpm <= RPM[i]) {
                return NM[i - 1] + (NM[i] - NM[i - 1]) * (rpm - RPM[i - 1]) / (RPM[i] - RPM[i - 1])
            }
        }
        return NM[NM.size - 1]
    }

    // Torsi bersih mesin (Nm) pada rpm & bukaan throttle (%)
    fun torque(p: Params, rpm: Double, tps: Double): Double {
        val th = tps / 100.0 * PI / 2.0
        val tbr = (p.tb / 26.0).pow(2.0)
        val area = (1.0 - cos(th)) * tbr
        val load = min(1.0, area.pow(0.6))
        val rf = ((rpm - 5000.0) / 4000.0).coerceIn(0.0, 1.0)
        val full = curveTorque(rpm) / 13.1 * p.peakT * (p.cc / 149.3) * (p.trim / 100.0) * (1.0 + 0.5 * (tbr - 1.0) * rf)
        val pump = 0.4 + 0.00045 * rpm
        return full * load - pump * (1.0 - load)
    }

    private fun rd(p: Params, s: Double): Double = (p.rdMin + s * (p.rdMax - p.rdMin)) / 1000.0

    // Radius belt puli belakang dari panjang belt & jarak sumbu
    private fun rnOf(p: Params, rd: Double): Double {
        val c = p.cd / 1000.0
        val l = p.belt / 1000.0
        val a = 1.0 / c
        val b = PI - 2.0 * rd / c
        val cc = rd * rd / c + PI * rd + 2.0 * c - l
        val d = max(0.0, b * b - 4.0 * a * cc)
        return (-b + sqrt(d)) / (2.0 * a)
    }

    private fun sRange(p: Params): Pair<Double, Double> {
        val rnMin = p.rnMin / 1000.0
        val rnMax = p.rnMax / 1000.0
        var lo = 0.0
        var hi = 1.0
        if (rnOf(p, rd(p, 0.0)) > rnMax) {
            var a = 0.0
            var b = 1.0
            for (k in 0 until 40) {
                val m = (a + b) / 2.0
                if (rnOf(p, rd(p, m)) > rnMax) a = m else b = m
            }
            lo = b
        }
        if (rnOf(p, rd(p, 1.0)) < rnMin) {
            var a = 0.0
            var b = 1.0
            for (k in 0 until 40) {
                val m = (a + b) / 2.0
                if (rnOf(p, rd(p, m)) >= rnMin) a = m else b = m
            }
            hi = a
        }
        if (hi < lo) hi = lo
        return Pair(lo, hi)
    }

    // Selisih gaya: dorong roller (puli depan) - gaya jepit yang dibutuhkan (pegas + torque cam)
    private fun balance(p: Params, s: Double, v: Double, tps: Double, rt: Double): Double {
        val rdv = rd(p, s)
        val rn = rnOf(p, rdv)
        val r = rn / rdv
        val w = v * r * p.gear / rt
        val rpm = w * 60.0 / (2.0 * PI)
        val t = max(torque(p, rpm, tps), 0.0)
        val rr = (p.rrLow + s * (p.rrHigh - p.rrLow)) / 1000.0
        val fd = p.nRoll * p.massG / 1000.0 * w * w * rr * tan(Math.toRadians(p.ramp))
        val k = (rn - p.rnMin / 1000.0) * 1000.0 * 0.46 * p.spK
        val fs = p.spPre + max(0.0, k)
        val fc = t * r / (p.camR / 1000.0 * tan(Math.toRadians(p.cam)))
        val c = p.cd / 1000.0
        val ang = asin(((rn - rdv) / c).coerceIn(-1.0, 1.0))
        val wr = (PI - 2.0 * ang) / (PI + 2.0 * ang)
        return fd - (fs + fc) * wr
    }

    private fun solveS(p: Params, v: Double, tps: Double, lo: Double, hi: Double, rt: Double): Double {
        if (balance(p, lo, v, tps, rt) <= 0.0) return lo
        if (balance(p, hi, v, tps, rt) >= 0.0) return hi
        var a = lo
        var b = hi
        for (k in 0 until 40) {
            val m = (a + b) / 2.0
            if (balance(p, m, v, tps, rt) > 0.0) a = m else b = m
        }
        return (a + b) / 2.0
    }

    private fun point(p: Params, v: Double, tps: Double, lo: Double, hi: Double, rt: Double): Pt {
        val s = solveS(p, v, tps, lo, hi, rt)
        val rdv = rd(p, s)
        val r = rnOf(p, rdv) / rdv
        val rpm = v * r * p.gear / rt * 60.0 / (2.0 * PI)
        val t = if (rpm > p.rev) 0.0 else torque(p, rpm, tps)
        return Pt(r, rpm, t * r * p.gear * p.eff / rt)
    }

    private fun sanitize(p: Params): Params = p.copy(
        cc = max(p.cc, 50.0),
        peakT = max(p.peakT, 0.1),
        trim = max(p.trim, 1.0),
        tb = max(p.tb, 5.0),
        nRoll = max(p.nRoll, 0.0),
        massG = max(p.massG, 0.0),
        rrLow = max(p.rrLow, 1.0),
        rrHigh = max(p.rrHigh, 1.0),
        ramp = p.ramp.coerceIn(1.0, 89.0),
        rdMin = max(p.rdMin, 5.0),
        rdMax = max(p.rdMax, max(p.rdMin, 5.0) + 1.0),
        belt = max(p.belt, 100.0),
        cd = max(p.cd, 50.0),
        rnMin = max(p.rnMin, 5.0),
        rnMax = max(p.rnMax, max(p.rnMin, 5.0) + 1.0),
        spPre = max(p.spPre, 0.0),
        spK = max(p.spK, 0.0),
        cam = p.cam.coerceIn(1.0, 89.0),
        camR = max(p.camR, 1.0),
        engage = max(p.engage, 500.0),
        rev = max(p.rev, max(p.engage, 500.0) + 500.0),
        idle = max(p.idle, 500.0),
        gear = max(p.gear, 1.0),
        eff = p.eff.coerceIn(0.3, 1.0),
        tireW = max(p.tireW, 50.0),
        tireA = max(p.tireA, 20.0),
        rim = max(p.rim, 8.0),
        motorKg = max(p.motorKg, 10.0),
        riderKg = max(p.riderKg, 0.0),
        cdA = max(p.cdA, 0.05),
        crr = max(p.crr, 0.0)
    )

    fun run(p0: Params, tps: Double, slopeDeg: Double): SimResult? {
        return try {
            val r = runInner(sanitize(p0), tps, slopeDeg)
            if (r.kmh.isNaN() || r.rpm.isNaN()) null else r
        } catch (e: Exception) {
            null
        }
    }

    private fun runInner(p: Params, tps: Double, slopeDeg: Double): SimResult {
        val rt = (p.rim * 25.4 / 2.0 + p.tireW * p.tireA / 100.0) / 1000.0 * 0.99
        val range = sRange(p)
        val lo = range.first
        val hi = range.second
        val rLow = rnOf(p, rd(p, lo)) / rd(p, lo)
        val rHigh = rnOf(p, rd(p, hi)) / rd(p, hi)
        val mass = p.motorKg + p.riderKg
        val ph = Math.toRadians(slopeDeg)
        val a0 = p.crr * mass * G * cos(ph) + mass * G * sin(ph)
        val bq = 0.5 * RHO * p.cdA
        fun fres(v: Double): Double = a0 + bq * v * v

        val vs = p.engage * 2.0 * PI / 60.0 * rt / (rLow * p.gear)
        val vEnd = max(p.rev * 2.0 * PI / 60.0 * rt / (rHigh * p.gear), vs + 1.0)
        val step = 0.1
        val n = floor((vEnd - vs) / step).toInt() + 1

        val vArr = DoubleArray(n) { vs + it * step }
        val rpmA = DoubleArray(n)
        val ratA = DoubleArray(n)
        val thA = DoubleArray(n)
        val reA = DoubleArray(n)
        var eq = -1
        for (i in 0 until n) {
            val pt = point(p, vArr[i], tps, lo, hi, rt)
            rpmA[i] = pt.rpm
            ratA[i] = pt.r
            thA[i] = pt.f
            reA[i] = fres(vArr[i])
            if (eq < 0 && pt.f - reA[i] <= 0.0) eq = i
        }
        val kmhA = DoubleArray(n) { vArr[it] * 3.6 }
        val curve = Curve(kmhA, rpmA, ratA, thA, reA)

        if (eq == 0) {
            val fConst = max(torque(p, p.engage, tps), 0.0) * rLow * p.gear * p.eff / rt
            val vSlip = if (fConst <= a0) 0.0 else min(sqrt((fConst - a0) / bq), vs)
            val rpmShow = if (vSlip <= 0.0 && tps < 2.0) p.idle else p.engage
            return SimResult(
                vSlip * 3.6, rpmShow, rLow, rpmShow / rLow,
                torque(p, rpmShow, tps),
                "Kopling slip: tenaga kurang untuk gas/tanjakan ini", curve
            )
        }
        if (eq < 0) {
            val i = n - 1
            return SimResult(
                kmhA[i], rpmA[i], ratA[i], rpmA[i] / ratA[i],
                torque(p, rpmA[i], tps), "Limiter RPM tercapai (tenaga masih sisa)", curve
            )
        }
        val d0 = thA[eq - 1] - reA[eq - 1]
        val d1 = thA[eq] - reA[eq]
        val fr = if (d0 - d1 != 0.0) (d0 / (d0 - d1)).coerceIn(0.0, 1.0) else 0.0
        fun lerp(a: DoubleArray): Double = a[eq - 1] + (a[eq] - a[eq - 1]) * fr
        val kmh = lerp(kmhA)
        val rpm = lerp(rpmA)
        val ratio = lerp(ratA)
        val status = when {
            ratio <= rHigh * 1.005 -> "Top speed: CVT sudah di rasio akhir"
            ratio >= rLow * 0.995 -> "CVT masih di rasio awal (belum shifting)"
            else -> "Seimbang: CVT sedang shifting"
        }
        return SimResult(kmh, rpm, ratio, rpm / ratio, torque(p, rpm, tps), status, curve)
    }
}
