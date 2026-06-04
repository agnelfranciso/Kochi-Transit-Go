package app.agneldev.kochitransitgo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Color

class TrainTrackerBottomSheet(private val tripId: String) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_train_tracker, container, false)
        
        val trainName = view.findViewById<TextView>(R.id.trackerTrainName)
        val statusText = view.findViewById<TextView>(R.id.trackerStatusText)
        val stopsContainer = view.findViewById<LinearLayout>(R.id.trackerStopsContainer)

        val trip = GtfsRepository.trips.find { it.tripId == tripId }
        val stopTimes = GtfsRepository.stopTimes.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
        
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        trainName.text = "Towards ${trip?.headsign ?: "Unknown"}"

        var currentStopIndex = -1
        for (i in stopTimes.indices) {
            if (stopTimes[i].arrivalTime >= currentTime) {
                currentStopIndex = i
                break
            }
        }
        
        if (stopTimes.isNotEmpty()) {
            if (currentTime < stopTimes.first().departureTime) {
                val stopInfo = GtfsRepository.stops.find { it.stopId == stopTimes.first().stopId }
                statusText.text = "Scheduled: Departs ${stopInfo?.stopName ?: "Station"} at ${GtfsRepository.formatTime(stopTimes.first().departureTime)}"
                currentStopIndex = 0
            } else if (currentTime > stopTimes.last().arrivalTime) {
                currentStopIndex = stopTimes.size - 1
                statusText.text = "Trip Completed"
            } else if (currentStopIndex != -1) {
                val stopInfo = GtfsRepository.stops.find { it.stopId == stopTimes[currentStopIndex].stopId }
                statusText.text = "Approaching: ${stopInfo?.stopName ?: "Station"} at ${GtfsRepository.formatTime(stopTimes[currentStopIndex].arrivalTime)}"
            }
        }

        // Show all stops
        for (i in stopTimes.indices) {
            val st = stopTimes[i]
            val stopInfo = GtfsRepository.stops.find { it.stopId == st.stopId }
            
            val itemView = inflater.inflate(R.layout.upcoming_metro_item, stopsContainer, false)
            val depText = itemView.findViewById<TextView>(R.id.departureTime)
            val destText = itemView.findViewById<TextView>(R.id.arrivalTime)
            
            depText.text = GtfsRepository.formatTime(st.arrivalTime)
            destText.text = stopInfo?.stopName ?: "Station"
            destText.textSize = 18f
            destText.setTypeface(destText.typeface, android.graphics.Typeface.BOLD)
            
            if (i < currentStopIndex) {
                // Past stops
                destText.setTextColor(Color.GRAY)
                depText.setTextColor(Color.GRAY)
            } else if (i == currentStopIndex) {
                // Current Stop
                destText.setTextColor(Color.parseColor("#00BFA5"))
                depText.setTextColor(Color.parseColor("#00BFA5"))
                destText.text = "${destText.text} (Next)"
            }
            
            stopsContainer.addView(itemView)
        }

        return view
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.clearFocus()
    }
}
