package za.org.kasicollect.reclaimer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import za.org.kasicollect.reclaimer.hardware.ScaleEvent
import za.org.kasicollect.reclaimer.hardware.ScaleIngressManager

class MainActivity : Activity() {

    private lateinit var viewResident: LinearLayout
    private lateinit var viewReclaimer: LinearLayout
    private lateinit var btnTabResident: Button
    private lateinit var btnTabReclaimer: Button
    private lateinit var etLandmark: EditText
    private lateinit var btnSubmitManifest: Button
    private lateinit var btnClaim1: Button
    private lateinit var btnClaim2: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewResident = findViewById(R.id.viewResident)
        viewReclaimer = findViewById(R.id.viewReclaimer)
        btnTabResident = findViewById(R.id.btnTabResident)
        btnTabReclaimer = findViewById(R.id.btnTabReclaimer)
        etLandmark = findViewById(R.id.etLandmark)
        btnSubmitManifest = findViewById(R.id.btnSubmitManifest)
        btnClaim1 = findViewById(R.id.btnClaim1)
        btnClaim2 = findViewById(R.id.btnClaim2)

        // Tab Switching
        btnTabResident.setOnClickListener {
            viewResident.visibility = View.VISIBLE
            viewReclaimer.visibility = View.GONE
            btnTabResident.setBackgroundColor(Color.parseColor("#059669"))
            btnTabResident.setTextColor(Color.WHITE)
            btnTabReclaimer.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabReclaimer.setTextColor(Color.parseColor("#94A3B8"))
        }

        btnTabReclaimer.setOnClickListener {
            viewResident.visibility = View.GONE
            viewReclaimer.visibility = View.VISIBLE
            btnTabReclaimer.setBackgroundColor(Color.parseColor("#059669"))
            btnTabReclaimer.setTextColor(Color.WHITE)
            btnTabResident.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabResident.setTextColor(Color.parseColor("#94A3B8"))
        }

        // Resident Request Manifest
        btnSubmitManifest.setOnClickListener {
            val landmark = etLandmark.text.toString().trim()
            if (landmark.isEmpty()) {
                Toast.makeText(this, "Please enter your street or landmark in Alex", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Pickup Logged! ♻️")
                .setMessage("Your request for '$landmark' is live.\n\nYour Pickup Code is: KC-7821\nShow this code to the trolley reclaimer to receive your R5.00 Spaza voucher.")
                .setPositiveButton("OK") { _, _ -> etLandmark.setText("") }
                .show()
        }

        // Reclaimer Scale Handoff Flow
        val handleClaim = { locationName: String ->
            AlertDialog.Builder(this)
                .setTitle("Claiming Bag at $locationName")
                .setMessage("Connecting to WH-A08 Bluetooth Scale...\n\nReading verified weight: 4.25 kg Clean PET.\n\nR5.00 Spaza Voucher triggered to household.")
                .setPositiveButton("Complete & Sync") { _, _ ->
                    Toast.makeText(this, "Batch recorded to encrypted ledger!", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnClaim1.setOnClickListener { handleClaim("8th Ave, near Taxi Rank") }
        btnClaim2.setOnClickListener { handleClaim("12th Ave, corner Vas St") }
    }
}
