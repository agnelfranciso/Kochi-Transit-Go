package app.agneldev.kochitransitgo

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StationDetailActivity : AppCompatActivity() {

    private lateinit var departuresContainer: LinearLayout
    private var stopId: String? = null
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_station_detail)

        stopId = intent.getStringExtra("stopId")
        val stop = GtfsRepository.stops.find { it.stopId == stopId }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        val titleView = findViewById<TextView>(R.id.stationNameTitle)
        titleView.text = stop?.stopName ?: "Unknown Station"

        departuresContainer = findViewById(R.id.departuresContainer)
        val chipGroup = findViewById<ChipGroup>(R.id.directionFilterGroup)

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds[0]) {
                R.id.chipAll -> currentFilter = "All"
                R.id.chipAluva -> currentFilter = "Aluva"
                R.id.chipTripunithura -> currentFilter = "Tripunithura"
            }
            renderDepartures()
        }

        renderDepartures()
    }

    private fun renderDepartures() {
        departuresContainer.removeAllViews()
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val stopTimesForStop = GtfsRepository.stopTimes
            .filter { it.stopId == stopId && it.departureTime >= currentTime }
            .sortedBy { it.departureTime }

        val filteredTimes = stopTimesForStop.filter { st ->
            val trip = GtfsRepository.trips.find { it.tripId == st.tripId }
            if (currentFilter == "All") true
            else trip?.headsign?.contains(currentFilter, ignoreCase = true) == true
        }.take(20)

        val inflater = LayoutInflater.from(this)

        if (filteredTimes.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No upcoming metros found."
                setPadding(0, 32, 0, 0)
            }
            departuresContainer.addView(emptyText)
            return
        }

        for (st in filteredTimes) {
            val trip = GtfsRepository.trips.find { it.tripId == st.tripId }
            val headsign = trip?.headsign ?: "Unknown Destination"
            
            val itemView = inflater.inflate(R.layout.upcoming_metro_item, departuresContainer, false)
            val depText = itemView.findViewById<TextView>(R.id.departureTime)
            val destText = itemView.findViewById<TextView>(R.id.arrivalTime)
            
            depText.text = GtfsRepository.formatTime(st.departureTime)
            destText.text = "Towards $headsign"
            
            departuresContainer.addView(itemView)
        }
    }
}
