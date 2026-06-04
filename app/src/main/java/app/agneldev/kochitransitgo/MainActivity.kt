package app.agneldev.kochitransitgo

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var stationsListContainer: LinearLayout
    private lateinit var searchStationInput: TextInputEditText
    private lateinit var mapView: org.osmdroid.views.MapView
    
    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                findNearestStation()
            }
            else -> {
                android.widget.Toast.makeText(this, "Location permission denied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        org.osmdroid.config.Configuration.getInstance().load(applicationContext, androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_main)

        GtfsRepository.loadData(this)
        setupUI()
        setupStationsTab()
    }
    
    private fun findNearestStation() {
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }

        var location: android.location.Location? = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        if (location == null) {
            location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        }

        if (location != null) {
            var nearestStop: Stop? = null
            var minDistance = Float.MAX_VALUE
            
            for (stop in GtfsRepository.stops) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    location.latitude, location.longitude,
                    stop.stopLat, stop.stopLon,
                    results
                )
                val distanceInMeters = results[0]
                if (distanceInMeters < minDistance) {
                    minDistance = distanceInMeters
                    nearestStop = stop
                }
            }
            
            if (minDistance <= 2000f && nearestStop != null) {
                val fromStation = findViewById<AutoCompleteTextView>(R.id.fromStation)
                fromStation.setText(nearestStop.stopName, false)
            } else {
                android.widget.Toast.makeText(this, "No station near you", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(this, "Could not determine location", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("KochiTransitPrefs", android.content.Context.MODE_PRIVATE)
        val isSystem24Hour = android.text.format.DateFormat.is24HourFormat(this)
        GtfsRepository.use24HourFormat = prefs.getBoolean("use_24_hour", isSystem24Hour)
        
        // Refresh routes if there's text
        val fromStation = findViewById<AutoCompleteTextView>(R.id.fromStation)
        val toStation = findViewById<AutoCompleteTextView>(R.id.toStation)
        if (fromStation.text.isNotEmpty() && toStation.text.isNotEmpty()) {
            calculateRoutes(fromStation.text.toString(), toStation.text.toString())
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            } else false
        }

        viewFlipper = findViewById(R.id.viewFlipper)
        bottomNav = findViewById(R.id.bottomNavigation)
        stationsListContainer = findViewById(R.id.stationsListContainer)
        searchStationInput = findViewById(R.id.searchStationInput)

        val fromStation = findViewById<AutoCompleteTextView>(R.id.fromStation)
        val toStation = findViewById<AutoCompleteTextView>(R.id.toStation)
        val swapBtn = findViewById<Button>(R.id.swapBtn)
        val searchBtn = findViewById<Button>(R.id.searchBtn)

        val stopNames = GtfsRepository.stops.map { it.stopName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, stopNames)
        
        fromStation.setAdapter(adapter)
        toStation.setAdapter(adapter)

        val locationBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.locationBtn)
        locationBtn.setOnClickListener {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                findNearestStation()
            } else {
                locationPermissionRequest.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }

        val timeSelectorBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.timeSelectorBtn)
        val clearTimeBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.clearTimeBtn)
        
        timeSelectorBtn.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)

            val timePickerDialog = android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val timeStr = String.format("%02d:%02d:00", selectedHour, selectedMinute)
                selectedDepartureTime = timeStr
                timeSelectorBtn.text = GtfsRepository.formatTime(timeStr)
                clearTimeBtn.visibility = View.VISIBLE
            }, hour, minute, GtfsRepository.use24HourFormat)
            timePickerDialog.show()
        }

        clearTimeBtn.setOnClickListener {
            selectedDepartureTime = null
            timeSelectorBtn.text = "Now"
            clearTimeBtn.visibility = View.GONE
        }
        
        val attributionText = findViewById<TextView>(R.id.attributionText)
        attributionText.text = android.text.Html.fromHtml("Contains data provided by <a href=\"https://kochimetro.org/\">Kochi Metro Rail Limited</a>", android.text.Html.FROM_HTML_MODE_LEGACY)
        attributionText.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        swapBtn.setOnClickListener {
            val temp = fromStation.text.toString()
            fromStation.setText(toStation.text.toString(), false)
            toStation.setText(temp, false)
        }

        searchBtn.setOnClickListener {
            calculateRoutes(fromStation.text.toString(), toStation.text.toString())
        }

        setupMapTab()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_planner -> {
                    viewFlipper.displayedChild = 0
                    true
                }
                R.id.navigation_stations -> {
                    viewFlipper.displayedChild = 1
                    true
                }
                R.id.navigation_map -> {
                    viewFlipper.displayedChild = 2
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMapTab() {
        mapView = findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true)
        // Use Carto DarkMatter for a modern dark map UI
        val darkMatter = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoDarkMatter",
            0, 20, 256, ".png", arrayOf("https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/")
        )
        mapView.setTileSource(darkMatter)
        
        val mapController = mapView.controller
        mapController.setZoom(13.0)
        
        // Center on Kochi Metro approx middle
        val startPoint = org.osmdroid.util.GeoPoint(10.0246, 76.3075)
        mapController.setCenter(startPoint)

        val polyline = org.osmdroid.views.overlay.Polyline()
        polyline.outlinePaint.color = Color.parseColor("#BB86FC") // A brighter purple for dark mode
        polyline.outlinePaint.strokeWidth = 10f
        
        val sortedShapes = GtfsRepository.shapes.filter { it.shapeId == "R1_0" }.sortedBy { it.sequence }
        val geoPoints = sortedShapes.map { org.osmdroid.util.GeoPoint(it.lat, it.lon) }
        polyline.setPoints(geoPoints)
        
        mapView.overlays.add(polyline)

        // Draw station markers
        for (stop in GtfsRepository.stops) {
            val stationMarker = org.osmdroid.views.overlay.Marker(mapView)
            stationMarker.position = org.osmdroid.util.GeoPoint(stop.stopLat, stop.stopLon)
            stationMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            stationMarker.title = stop.stopName
            val stationIcon = getDrawable(R.drawable.ic_station)?.mutate()
            stationMarker.icon = stationIcon
            mapView.overlays.add(stationMarker)
        }

        startTrainSimulation()
    }

    private var focusedTripId: String? = null
    private var selectedDepartureTime: String? = null

        private var trainUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var trainUpdateRunnable: Runnable? = null
    
    private fun startTrainSimulation() {
        trainUpdateRunnable = object : Runnable {
            override fun run() {
                drawActiveTrains()
                trainUpdateHandler.postDelayed(this, 1000)
            }
        }
        trainUpdateHandler.post(trainUpdateRunnable!!)
    }

    private val cachedSortedShapes by lazy { GtfsRepository.shapes.filter { it.shapeId == "R1_0" }.sortedBy { it.sequence } }
    
    private val stopShapeIndices: Map<String, Int> by lazy {
        val map = mutableMapOf<String, Int>()
        for (stop in GtfsRepository.stops) {
            var minIndex = -1
            var minDistance = Float.MAX_VALUE
            val results = FloatArray(1)
            for (i in cachedSortedShapes.indices) {
                val shape = cachedSortedShapes[i]
                android.location.Location.distanceBetween(stop.stopLat, stop.stopLon, shape.lat, shape.lon, results)
                if (results[0] < minDistance) {
                    minDistance = results[0]
                    minIndex = i
                }
            }
            map[stop.stopId] = minIndex
        }
        map
    }

    private fun getTrainPosition(tripId: String, currentTimeStr: String): org.osmdroid.util.GeoPoint? {
        val stopTimes = GtfsRepository.stopTimes.filter { it.tripId == tripId }.sortedBy { it.stopSequence }
        if (stopTimes.isEmpty()) return null
        
        var prevStop: StopTime? = null
        var nextStop: StopTime? = null
        for (i in 0 until stopTimes.size - 1) {
            if (currentTimeStr >= stopTimes[i].departureTime && currentTimeStr <= stopTimes[i+1].arrivalTime) {
                prevStop = stopTimes[i]
                nextStop = stopTimes[i+1]
                break
            }
        }
        
        if (prevStop != null && nextStop != null) {
            val prevInfo = GtfsRepository.stops.find { it.stopId == prevStop.stopId }
            val nextInfo = GtfsRepository.stops.find { it.stopId == nextStop.stopId }
            if (prevInfo != null && nextInfo != null) {
                val prevSec = timeToSeconds(prevStop.departureTime)
                val nextSec = timeToSeconds(nextStop.arrivalTime)
                val currSec = timeToSeconds(currentTimeStr)
                
                val fraction = if (nextSec > prevSec) {
                    (currSec - prevSec).toFloat() / (nextSec - prevSec)
                } else 0f
                
                val iIdx = stopShapeIndices[prevInfo.stopId] ?: -1
                val jIdx = stopShapeIndices[nextInfo.stopId] ?: -1
                
                if (iIdx != -1 && jIdx != -1) {
                    val subShapes = if (iIdx <= jIdx) cachedSortedShapes.subList(iIdx, jIdx + 1) else cachedSortedShapes.subList(jIdx, iIdx + 1).reversed()
                    
                    var totalDist = 0f
                    val distances = FloatArray(subShapes.size - 1)
                    val results = FloatArray(1)
                    for (k in 0 until subShapes.size - 1) {
                        android.location.Location.distanceBetween(subShapes[k].lat, subShapes[k].lon, subShapes[k+1].lat, subShapes[k+1].lon, results)
                        distances[k] = results[0]
                        totalDist += results[0]
                    }
                    
                    val targetDist = fraction * totalDist
                    var currentDist = 0f
                    for (k in 0 until subShapes.size - 1) {
                        if (currentDist + distances[k] >= targetDist) {
                            val subFraction = if (distances[k] > 0) (targetDist - currentDist) / distances[k] else 0f
                            val lat = subShapes[k].lat + subFraction * (subShapes[k+1].lat - subShapes[k].lat)
                            val lon = subShapes[k].lon + subFraction * (subShapes[k+1].lon - subShapes[k].lon)
                            return org.osmdroid.util.GeoPoint(lat, lon)
                        }
                        currentDist += distances[k]
                    }
                    return org.osmdroid.util.GeoPoint(subShapes.last().lat, subShapes.last().lon)
                } else {
                    val lat = prevInfo.stopLat + fraction * (nextInfo.stopLat - prevInfo.stopLat)
                    val lon = prevInfo.stopLon + fraction * (nextInfo.stopLon - prevInfo.stopLon)
                    return org.osmdroid.util.GeoPoint(lat, lon)
                }
            }
        }
        
        val currentStop = stopTimes.find { currentTimeStr >= it.arrivalTime && currentTimeStr <= it.departureTime }
        if (currentStop != null) {
            val info = GtfsRepository.stops.find { it.stopId == currentStop.stopId }
            if (info != null) return org.osmdroid.util.GeoPoint(info.stopLat, info.stopLon)
        }
        
        // If arrived at last stop
        if (currentTimeStr >= stopTimes.last().arrivalTime) {
            val info = GtfsRepository.stops.find { it.stopId == stopTimes.last().stopId }
            if (info != null) return org.osmdroid.util.GeoPoint(info.stopLat, info.stopLon)
        }
        
        return null
    }

    private fun timeToSeconds(time: String): Int {
        val parts = time.split(":")
        return if (parts.size == 3) parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() else 0
    }

    fun clearFocus() {
        focusedTripId = null
    }

    private val activeTrainMarkers = mutableMapOf<String, org.osmdroid.views.overlay.Marker>()

    private fun drawActiveTrains() {
        val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val tripsByRoute = GtfsRepository.stopTimes.groupBy { it.tripId }
        
        val currentlyActiveTrips = mutableSetOf<String>()
        
        for ((tripId, stopTimes) in tripsByRoute) {
            val sorted = stopTimes.sortedBy { it.stopSequence }
            if (sorted.isEmpty()) continue
            if (sorted.first().departureTime > currentTimeStr || sorted.last().arrivalTime < currentTimeStr) continue
            
            if (focusedTripId != null && tripId != focusedTripId) {
                continue // Hide other trains while one is focused
            }
            
            currentlyActiveTrips.add(tripId)
            val geoPoint = getTrainPosition(tripId, currentTimeStr)
            if (geoPoint != null) {
                var marker = activeTrainMarkers[tripId]
                if (marker == null) {
                    marker = org.osmdroid.views.overlay.Marker(mapView)
                    marker.position = geoPoint
                    marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                    val icon = getDrawable(R.drawable.ic_directions_transit)?.mutate()
                    icon?.setTint(Color.parseColor("#00BFA5"))
                    val trip = GtfsRepository.trips.find { it.tripId == tripId }
                    marker.title = "Metro to ${trip?.headsign}"
                    marker.icon = icon
                    
                    marker.setOnMarkerClickListener { _, _ ->
                        focusedTripId = tripId
                        TrainTrackerBottomSheet(tripId).show(supportFragmentManager, "TrainTrackerBottomSheet")
                        true
                    }
                    
                    mapView.overlays.add(marker)
                    activeTrainMarkers[tripId] = marker
                } else {
                    val startPos = marker.position
                    val latDiff = geoPoint.latitude - startPos.latitude
                    val lonDiff = geoPoint.longitude - startPos.longitude
                    
                    if (latDiff != 0.0 || lonDiff != 0.0) {
                        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
                        animator.duration = 1000L
                        animator.addUpdateListener { animation ->
                            val fraction = animation.animatedFraction
                            marker!!.position = org.osmdroid.util.GeoPoint(startPos.latitude + fraction * latDiff, startPos.longitude + fraction * lonDiff)
                            mapView.invalidate()
                        }
                        animator.start()
                    }
                }

                if (tripId == focusedTripId) {
                    val offsetLat = geoPoint.latitude - 0.008 
                    mapView.controller.animateTo(org.osmdroid.util.GeoPoint(offsetLat, geoPoint.longitude), 16.0, 1000L)
                }
            }
        }
        
        val iterator = activeTrainMarkers.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentlyActiveTrips.contains(entry.key)) {
                mapView.overlays.remove(entry.value)
                iterator.remove()
            }
        }
        mapView.invalidate()
    }

    private fun setupStationsTab() {
        renderStationsList(GtfsRepository.stops)

        searchStationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase(Locale.getDefault())
                val filtered = if (query.isEmpty()) {
                    GtfsRepository.stops
                } else {
                    GtfsRepository.stops.filter { it.stopName.lowercase(Locale.getDefault()).contains(query) }
                }
                renderStationsList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun renderStationsList(stops: List<Stop>) {
        stationsListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        
        for (stop in stops) {
            val itemView = inflater.inflate(R.layout.station_list_item, stationsListContainer, false)
            val nameText = itemView.findViewById<TextView>(R.id.stationNameText)
            nameText.text = stop.stopName

            itemView.setOnClickListener {
                val intent = android.content.Intent(this, StationDetailActivity::class.java)
                intent.putExtra("stopId", stop.stopId)
                startActivity(intent)
            }
            stationsListContainer.addView(itemView)
        }
    }

    private fun showStationTimetable(stop: Stop) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        val stopTimesForStop = GtfsRepository.stopTimes
            .filter { it.stopId == stop.stopId && it.departureTime >= currentTime }
            .sortedBy { it.departureTime }
            .take(15) // Show next 15 trains

        val builderStr = StringBuilder()
        if (stopTimesForStop.isEmpty()) {
            builderStr.append("No more metros today.")
        } else {
            for (st in stopTimesForStop) {
                val trip = GtfsRepository.trips.find { it.tripId == st.tripId }
                val headsign = trip?.headsign ?: "Unknown Destination"
                builderStr.append("🕒 ${st.departureTime.substring(0, 5)} ➡️ $headsign\n\n")
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("${stop.stopName} Departures")
            .setMessage(builderStr.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    data class RouteMatch(val tripId: String, val fromSeq: Int, val toSeq: Int, val departureTime: String, val arrivalTime: String, val stopsCount: Int)

    private fun calculateRoutes(fromName: String, toName: String) {
        if (fromName.isEmpty() || toName.isEmpty() || fromName == toName) return

        val fromStop = GtfsRepository.stops.find { it.stopName == fromName } ?: return
        val toStop = GtfsRepository.stops.find { it.stopName == toName } ?: return

        val fromStopTimes = GtfsRepository.stopTimes.filter { it.stopId == fromStop.stopId }
        val toStopTimes = GtfsRepository.stopTimes.filter { it.stopId == toStop.stopId }.associateBy { it.tripId }

        val currentTime = selectedDepartureTime ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val matches = mutableListOf<RouteMatch>()

        for (fromST in fromStopTimes) {
            if (fromST.departureTime >= currentTime) {
                val toST = toStopTimes[fromST.tripId]
                if (toST != null && toST.stopSequence > fromST.stopSequence) {
                    val stopsCount = toST.stopSequence - fromST.stopSequence
                    matches.add(RouteMatch(fromST.tripId, fromST.stopSequence, toST.stopSequence, fromST.departureTime, toST.arrivalTime, stopsCount))
                }
            }
        }

        val uniqueMatches = matches.distinctBy { it.departureTime }.toMutableList()
        uniqueMatches.sortBy { it.departureTime }
        
        matches.clear()
        matches.addAll(uniqueMatches)

        val resultsContainer = findViewById<LinearLayout>(R.id.resultsContainer)
        val nextDeparture = findViewById<TextView>(R.id.nextDeparture)
        val nextArrival = findViewById<TextView>(R.id.nextArrival)
        val nextFareInfo = findViewById<TextView>(R.id.nextFareInfo)
        val nextDurationInfo = findViewById<TextView>(R.id.nextDurationInfo)
        val intermediateStopsText = findViewById<TextView>(R.id.intermediateStopsText)
        val upcomingContainer = findViewById<LinearLayout>(R.id.upcomingContainer)
        val mainResultCard = findViewById<View>(R.id.mainResultCard)

        if (matches.isNotEmpty()) {
            resultsContainer.visibility = View.VISIBLE
            val first = matches.first()
            nextDeparture.text = GtfsRepository.formatTime(first.departureTime)
            nextArrival.text = GtfsRepository.formatTime(first.arrivalTime)
            
            // Calculate fare
            val fareRule = GtfsRepository.fareRules.find { 
                it.originId == fromStop.stopId && it.destinationId == toStop.stopId 
            }
            if (fareRule != null) {
                val attr = GtfsRepository.fareAttributes.find { it.fareId == fareRule.fareId }
                if (attr != null) {
                    nextFareInfo.text = "₹${attr.price.toInt()}"
                    nextFareInfo.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ticket, 0, 0, 0)
                    nextFareInfo.compoundDrawablePadding = 16
                    nextFareInfo.visibility = View.VISIBLE
                } else {
                    nextFareInfo.visibility = View.GONE
                }
            } else {
                nextFareInfo.visibility = View.GONE
            }

            nextDurationInfo.text = "${first.stopsCount} stops"
            
            mainResultCard.setOnClickListener {
                if (selectedDepartureTime == null) {
                    focusOnTrainInMap(first.tripId)
                }
            }
            
            val intermediateStopNames = GtfsRepository.stopTimes
                .filter { it.tripId == first.tripId && it.stopSequence > first.fromSeq && it.stopSequence < first.toSeq }
                .sortedBy { it.stopSequence }
                .mapNotNull { st -> GtfsRepository.stops.find { it.stopId == st.stopId }?.stopName }
            
            if (intermediateStopNames.isNotEmpty()) {
                val viaText = if (intermediateStopNames.size <= 3) {
                    intermediateStopNames.joinToString(", ")
                } else {
                    "${intermediateStopNames.first()}, ${intermediateStopNames[intermediateStopNames.size / 2]}, ${intermediateStopNames.last()}"
                }
                intermediateStopsText.text = "Via: $viaText"
                intermediateStopsText.visibility = View.VISIBLE
            } else {
                intermediateStopsText.visibility = View.GONE
            }

            upcomingContainer.removeAllViews()
            val inflater = LayoutInflater.from(this)
            
            for (i in 1 until matches.size.coerceAtMost(5)) {
                val match = matches[i]
                val itemView = inflater.inflate(R.layout.upcoming_metro_item, upcomingContainer, false)
                val deptTv = itemView.findViewById<TextView>(R.id.departureTime)
                val arrTv = itemView.findViewById<TextView>(R.id.arrivalTime)
                
                deptTv.text = GtfsRepository.formatTime(match.departureTime)
                itemView.setOnClickListener {
                    if (selectedDepartureTime == null) {
                        focusOnTrainInMap(match.tripId)
                    }
                }
                arrTv.text = GtfsRepository.formatTime(match.arrivalTime)
                
                upcomingContainer.addView(itemView)
            }
        } else {
            resultsContainer.visibility = View.GONE
        }
    }

    private fun focusOnTrainInMap(tripId: String) {
        focusedTripId = tripId
        
        val viewFlipper = findViewById<android.widget.ViewFlipper>(R.id.viewFlipper)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        
        viewFlipper.displayedChild = 2
        bottomNav.selectedItemId = R.id.navigation_map
        
        val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val geoPoint = getTrainPosition(tripId, currentTimeStr)
        
        if (geoPoint != null) {
            val offsetLat = geoPoint.latitude - 0.008 
            mapView.controller.animateTo(org.osmdroid.util.GeoPoint(offsetLat, geoPoint.longitude), 16.0, 1000L)
        }
        
        TrainTrackerBottomSheet(tripId).show(supportFragmentManager, "tracker")
    }
}
