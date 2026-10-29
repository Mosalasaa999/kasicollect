package za.org.kasicollect.reclaimer.hardware

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.UUID

sealed class ScaleEvent {
    data class WeightStabilized(val grossKg: Double, val tareKg: Double, val netKg: Double) : ScaleEvent()
    data class SecurityViolation(val reason: String) : ScaleEvent()
}

class ScaleIngressManager(
    private val context: Context,
    private val expectedHardwareMac: String,
    private val onScaleEvent: (ScaleEvent) -> Unit
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }

    fun parseScalePayload(data: ByteArray) {
        if (data.size < 8 || (data[0].toInt() and 0xFF) != 0xA5) return

        var xorCheck = 0
        for (i in 0..6) {
            xorCheck = xorCheck xor (data[i].toInt() and 0xFF)
        }
        if (xorCheck != (data[7].toInt() and 0xFF)) return

        val status = data[1].toInt() and 0xFF
        val isStable = (status and 0x02) != 0

        if (isStable) {
            val grossRaw = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val tareRaw = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)

            val grossKg = grossRaw / 100.0
            val tareKg = tareRaw / 100.0
            val netKg = grossKg - tareKg

            if (netKg > 0.05) {
                onScaleEvent(ScaleEvent.WeightStabilized(grossKg, tareKg, netKg))
            }
        }
    }
}
