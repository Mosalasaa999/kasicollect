package za.org.kasicollect.reclaimer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private lateinit var viewResident: LinearLayout
    private lateinit var viewReclaimer: LinearLayout
    private lateinit var btnTabResident: Button
    private lateinit var btnTabReclaimer: Button
    private lateinit var etLandmark: EditText
    private lateinit var btnSubmitManifest: Button
    private lateinit var reclaimerFeedContainer: LinearLayout
    private lateinit var tvNearbyTitle: TextView

    private val cbList = mutableListOf<CheckBox>()
    private var pickupCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewResident = findViewById(R.id.viewResident)
        viewReclaimer = findViewById(R.id.viewReclaimer)
        btnTabResident = findViewById(R.id.btnTabResident)
        btnTabReclaimer = findViewById(R.id.btnTabReclaimer)
        etLandmark = findViewById(R.id.etLandmark)
        btnSubmitManifest = findViewById(R.id.btnSubmitManifest)
        tvNearbyTitle = findViewById(R.id.tvNearbyTitle)

        // Find existing cards parent container
        reclaimerFeedContainer = viewReclaimer

        val cbPet = findViewById<CheckBox>(R.id.cbPet)
        val cbCans = findViewById<CheckBox>(R.id.cbCans)
        val cbCardboard = findViewById<CheckBox>(R.id.cbCardboard)
        val cbMilk = findViewById<CheckBox>(R.id.cbMilk)
        cbList.addAll(listOf(cbPet, cbCans, cbCardboard, cbMilk))

        // Role Tab Switching
        btnTabResident.setOnClickListener { switchTab(isResident = true) }
        btnTabReclaimer.setOnClickListener { switchTab(isResident = false) }

        // Dynamic Manifest Submission: Resident -> Reclaimer Feed
        btnSubmitManifest.setOnClickListener {
            val landmark = etLandmark.text.toString().trim()
            if (landmark.isEmpty()) {
                Toast.makeText(this, "Please enter your street or landmark in Alex", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMaterials = cbList.filter { it.isChecked }.map { it.text.toString() }
            val materialsSummary = if (selectedMaterials.isEmpty()) "Mixed Recyclables" else selectedMaterials.joinToString(", ")

            // Add new dynamic pickup card into Reclaimer feed
            addDynamicPickupCard(materialsSummary, landmark)

            AlertDialog.Builder(this)
                .setTitle("Pickup Live on Reclaimer Radar! ♻️")
                .setMessage("Your bag at '$landmark' is now broadcast to nearby trolley reclaimers.\n\nEstimated pickup: ~15 mins.\nYour R5.00 Spaza code: KC-${(1000..9999).random()}")
                .setPositiveButton("View in Reclaimer Feed") { _, _ ->
                    etLandmark.setText("")
                    switchTab(isResident = false)
                }
                .show()
        }

        // Bind existing static cards
        findViewById<Button>(R.id.btnClaim1).setOnClickListener { triggerScaleModal("8th Ave, near Taxi Rank", "Clear PET Plastic") }
        findViewById<Button>(R.id.btnClaim2).setOnClickListener { triggerScaleModal("12th Ave, corner Vas St", "Drink Cans, Cardboard") }
    }

    private fun switchTab(isResident: Boolean) {
        if (isResident) {
            viewResident.visibility = View.VISIBLE
            viewReclaimer.visibility = View.GONE
            btnTabResident.setBackgroundColor(Color.parseColor("#059669"))
            btnTabResident.setTextColor(Color.WHITE)
            btnTabReclaimer.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabReclaimer.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            viewResident.visibility = View.GONE
            viewReclaimer.visibility = View.VISIBLE
            btnTabReclaimer.setBackgroundColor(Color.parseColor("#059669"))
            btnTabReclaimer.setTextColor(Color.WHITE)
            btnTabResident.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabResident.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun addDynamicPickupCard(materials: String, landmark: String) {
        pickupCount++
        tvNearbyTitle.text = "Available Clean Pickups ($pickupCount nearby)"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#131E32"))
            setPadding(40, 40, 40, 40)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 30)
            layoutParams = params
        }

        val badge = TextView(this).apply {
            text = "JUST NOW • READY AT GATE"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 11f
        }

        val title = TextView(this).apply {
            text = materials
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }

        val loc = TextView(this).apply {
            text = "📍 $landmark"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }

        val claimBtn = Button(this).apply {
            text = "🛒 I'm on my way (Claim Bag)"
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                triggerScaleModal(landmark, materials)
                card.visibility = View.GONE
                pickupCount--
                tvNearbyTitle.text = "Available Clean Pickups ($pickupCount nearby)"
            }
        }

        card.addView(badge)
        card.addView(title)
        card.addView(loc)
        card.addView(claimBtn)

        // Insert at the top of the feed
        reclaimerFeedContainer.addView(card, 2)
    }

    private fun triggerScaleModal(location: String, materials: String) {
        AlertDialog.Builder(this)
            .setTitle("Weighbridge & Voucher Handoff")
            .setMessage("Location: $location\nStream: $materials\n\nScale: WH-A08 (Auto-Calibrated)\nVerified Net Weight: 3.80 kg\n\n✅ Resident R5.00 Spaza Voucher Dispatched.\n✅ Transaction sealed with HMAC signature.")
            .setPositiveButton("Complete Pickup") { _, _ ->
                Toast.makeText(this, "Batch synced to encrypted ledger!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
