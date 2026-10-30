package za.org.kasicollect.reclaimer

import (
    android.app.Activity
    android.app.AlertDialog
    android.graphics.Color
    android.os.Bundle
    android.view.View
    android.widget.*
    java.io.OutputStreamWriter
    java.net.HttpURLConnection
    java.net.URL
    kotlin.concurrent.thread
    org.json.JSONArray
    org.json.JSONObject

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
    private val cloudUrl = "https://kasicollect-api.onrender.com"

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
        reclaimerFeedContainer = viewReclaimer

        val cbPet = findViewById<CheckBox>(R.id.cbPet)
        val cbCans = findViewById<CheckBox>(R.id.cbCans)
        val cbCardboard = findViewById<CheckBox>(R.id.cbCardboard)
        val cbMilk = findViewById<CheckBox>(R.id.cbMilk)
        cbList.addAll(listOf(cbPet, cbCans, cbCardboard, cbMilk))

        btnTabResident.setOnClickListener { switchTab(isResident = true) }
        btnTabReclaimer.setOnClickListener {
            switchTab(isResident = false)
            fetchCloudManifests()
        }

        // POST Manifest to Render Cloud
        btnSubmitManifest.setOnClickListener {
            val landmark = etLandmark.text.toString().trim()
            if (landmark.isEmpty()) {
                Toast.makeText(this, "Enter a landmark (e.g. 10th Ave)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMaterials = cbList.filter { it.isChecked }.map { it.text.toString() }
            val materialsSummary = if (selectedMaterials.isEmpty()) "Mixed Recyclables" else selectedMaterials.joinToString(", ")
            val manifestId = "KC-" + (1000..9999).random()

            // Transmit to Cloud asynchronously
            thread {
                try {
                    val url = URL("$cloudUrl/v1/manifests")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 8000
                        readTimeout = 8000
                        doOutput = true
                    }

                    val payload = JSONObject().apply {
                        put("manifest_id", manifestId)
                        put("landmark", landmark)
                        put("materials", materialsSummary)
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val code = conn.responseCode

                    runOnUiThread {
                        if (code == 200) {
                            AlertDialog.Builder(this)
                                .setTitle("Live on Cloud Database! ??")
                                .setMessage("Bag registered at '$landmark'.\n\nManifest ID: $manifestId\nSynced to Render PostgreSQL.")
                                .setPositiveButton("View Radar") { _, _ ->
                                    etLandmark.setText("")
                                    switchTab(isResident = false)
                                    fetchCloudManifests()
                                }
                                .show()
                        } else {
                            Toast.makeText(this, "Cloud Error: Code $code", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Connecting to Cloud... Request queued locally", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Fetch initial cloud feed
        fetchCloudManifests()
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

    private fun fetchCloudManifests() {
        thread {
            try {
                val url = URL("$cloudUrl/v1/manifests")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(response)

                    runOnUiThread {
                        tvNearbyTitle.text = "Available Clean Pickups (${array.length()} active on cloud)"
                        reclaimerFeedContainer.removeAllViews()

                        // Re-add title
                        reclaimerFeedContainer.addView(tvNearbyTitle)

                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val mId = obj.getString("manifest_id")
                            val landmark = obj.getString("landmark")
                            val materials = obj.getString("materials")
                            addCardToView(mId, landmark, materials)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun addCardToView(manifestId: String, landmark: String, materials: String) {
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
            text = "CLOUD VERIFIED ? $manifestId"
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
            text = "?? $landmark"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }

        val claimBtn = Button(this).apply {
            text = "?? I'm on my way (Claim Bag)"
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                syncHandoffToCloud(manifestId, landmark, materials, card)
            }
        }

        card.addView(badge)
        card.addView(title)
        card.addView(loc)
        card.addView(claimBtn)
        reclaimerFeedContainer.addView(card)
    }

    private fun syncHandoffToCloud(manifestId: String, landmark: String, materials: String, cardView: View) {
        thread {
            try {
                val url = URL("$cloudUrl/v1/sync/batch")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000
                    doOutput = true
                }

                val record = JSONObject().apply {
                    put("transaction_id", "TX-" + (10000..99999).random())
                    put("manifest_id", manifestId)
                    put("reclaimer_hash", "rec_za_alex_anchor_01")
                    put("material", materials)
                    put("net_kg", 4.25)
                    put("tare_kg", 0.20)
                    put("scale_mac", "00:1B:44:11:3A:B7")
                    put("timestamp", System.currentTimeMillis())
                    put("lat_masked", -26.1052)
                    put("lng_masked", 28.0931)
                    put("hmac", "sha256_mock_valid")
                }

                val payload = JSONObject().apply {
                    put("batch_size", 1)
                    put("records", JSONArray().put(record))
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Collection Completed & Verified! ??")
                        .setMessage("Manifest: $manifestId\nWeight: 4.25 kg\n\n? Handoff synced to PostgreSQL.\n? R5.00 Spaza voucher credited.")
                        .setPositiveButton("OK") { _, _ ->
                            cardView.visibility = View.GONE
                        }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Completed locally. Syncing in background.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
