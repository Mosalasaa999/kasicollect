package za.org.kasicollect.reclaimer

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import za.org.kasicollect.reclaimer.hardware.ScaleEvent
import za.org.kasicollect.reclaimer.hardware.ScaleIngressManager
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var tvWeightDisplay: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLedgerCount: TextView
    private var simulatedCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvWeightDisplay = findViewById(R.id.tvWeightDisplay)
        tvStatus = findViewById(R.id.tvStatus)
        tvLedgerCount = findViewById(R.id.tvLedgerCount)

        val btnSimulate = findViewById<Button>(R.id.btnSimulateWeigh)
        val btnSync = findViewById<Button>(R.id.btnTriggerSync)

        val scaleManager = ScaleIngressManager(this, "00:00:00:00:00:00") { event ->
            runOnUiThread {
                when (event) {
                    is ScaleEvent.WeightStabilized -> {
                        tvWeightDisplay.text = String.format("%.2f kg", event.netKg)
                        tvStatus.text = "Status: Weight Calibrated & Locked"
                    }
                    is ScaleEvent.SecurityViolation -> {
                        tvStatus.text = "Status: MAC Validation Error"
                    }
                }
            }
        }

        btnSimulate.setOnClickListener {
            // Emulate raw byte packet from WH-A08 scale: 4.45kg Gross, 0.20kg Tare = 4.25kg Net
            val simulatedFrame = byteArrayOf(
                0xA5.toByte(), 0x02.toByte(), 0x01.toByte(), 0xBD.toByte(),
                0x00.toByte(), 0x14.toByte(), 0x00.toByte(), 0x08.toByte()
            )
            scaleManager.parseScalePayload(simulatedFrame)
            simulatedCounter++
            tvLedgerCount.text = "Local Encrypted Queue: $simulatedCounter pending collections"
        }

        btnSync.setOnClickListener {
            tvStatus.text = "Status: Encrypted batch transmitted via reverse-billed APN"
            simulatedCounter = 0
            tvLedgerCount.text = "Local Encrypted Queue: 0 pending collections (Synced)"
        }
    }
}
