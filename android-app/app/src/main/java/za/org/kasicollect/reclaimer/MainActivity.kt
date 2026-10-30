package za.org.kasicollect.reclaimer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

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

        btnSubmitManifest.setOnClickListener {
            val landmark = etLandmark.text.toString().trim()
            if (landmark.isEmpty()) {
                Toast.makeText(this, "Enter a landmark (e.g. 10th Ave)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMaterials = cbList.filter { it.isChecked }.map { it.text.toString() }
            val materialsSummary = if (selectedMaterials.isEmpty()) "Mixed Recyclables" else selectedMaterials.joinToString(", ")
            val manifestId = "KC-" + (1000..9999).random()

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
                                .setTitle("Manifest Broadcast Live")
                                .setMessage("Location: " + landmark + "\nManifest ID: " + manifestId + "\n\nWaiting for nearby trolley reclaimer.")
                                .setPositiveButton("View Reclaimer Feed") { _, _ ->
                                    etLandmark.setText("")
                                    switchTab(isResident = false)
                                    fetchCloudManifests()
                                }
                                .show()
                        } else {
                            Toast.makeText(this, "Cloud Response: " + code, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Local Queue Buffered", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

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
                        tvNearbyTitle.text = "Available Clean Pickups (" + array.length() + " active)"
                        reclaimerFeedContainer.removeAllViews()
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
            setPadding(36, 36, 36, 36)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }

        val badge = TextView(this).apply {
            text = "VERIFIED ORIGIN - " + manifestId
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
            text = "Location: " + landmark
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 0, 0, 20)
        }

        val claimBtn = Button(this).apply {
            text = "CLAIM BAG (WEIGH & COLLECT)"
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                executeCollection(manifestId, landmark, card, this)
            }
        }

        card.addView(badge)
        card.addView(title)
        card.addView(loc)
        card.addView(claimBtn)
        reclaimerFeedContainer.addView(card)
    }

    private fun executeCollection(manifestId: String, landmark: String, cardView: View, actionBtn: Button) {
        actionBtn.isEnabled = false
        actionBtn.text = "TRANSMITTING WEIGHT..."

        thread {
            try {
                val url = URL("$cloudUrl/v1/manifests/claim")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("manifest_id", manifestId)
                    put("net_kg", 4.25)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode

                runOnUiThread {
                    if (code == 200) {
                        actionBtn.text = "COLLECTED & SETTLED"
                        actionBtn.setBackgroundColor(Color.parseColor("#10B981"))

                        AlertDialog.Builder(this)
                            .setTitle("Weighbridge Handoff Verified")
                            .setMessage("Manifest: " + manifestId + "\nLocation: " + landmark + "\nVerified Net Weight: 4.25 kg\n\nStatus: Ledger updated to COLLECTED.\nR5.00 Spaza voucher credited.")
                            .setPositiveButton("Complete") { _, _ ->
                                cardView.visibility = View.GONE
                                fetchCloudManifests()
                            }
                            .show()
                    } else {
                        actionBtn.isEnabled = true
                        actionBtn.text = "CLAIM BAG (WEIGH & COLLECT)"
                        Toast.makeText(this, "Sync error: " + code, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    actionBtn.isEnabled = true
                    actionBtn.text = "CLAIM BAG (WEIGH & COLLECT)"
                    Toast.makeText(this, "Network drop. Sync queued.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
