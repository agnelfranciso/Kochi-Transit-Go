package app.agneldev.kochitransitgo

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

data class Stop(val stopId: String, val stopName: String, val stopLat: Double, val stopLon: Double)
data class StopTime(val tripId: String, val arrivalTime: String, val departureTime: String, val stopId: String, val stopSequence: Int)
data class Trip(val tripId: String, val routeId: String, val serviceId: String, val headsign: String)
data class FareRule(val fareId: String, val routeId: String, val originId: String, val destinationId: String)
data class FareAttribute(val fareId: String, val price: Double, val currencyType: String)
data class Shape(val shapeId: String, val sequence: Int, val lat: Double, val lon: Double)

object GtfsRepository {
    var use24HourFormat: Boolean = false

    fun formatTime(timeStr: String): String {
        val base = if (timeStr.length >= 5) timeStr.substring(0, 5) else timeStr
        if (use24HourFormat) return base
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf24.parse(base)
            if (date != null) sdf12.format(date) else base
        } catch (e: Exception) {
            base
        }
    }
    val stops = mutableListOf<Stop>()
    val stopTimes = mutableListOf<StopTime>()
    val trips = mutableListOf<Trip>()
    val fareRules = mutableListOf<FareRule>()
    val fareAttributes = mutableListOf<FareAttribute>()
    val shapes = mutableListOf<Shape>()
    
    var isLoaded = false

    fun loadData(context: Context) {
        if (isLoaded) return
        val am = context.assets

        BufferedReader(InputStreamReader(am.open("stops.txt"))).use { reader ->
            reader.readLine() // Header
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 4) {
                    stops.add(Stop(parts[0], parts[3], parts[1].toDouble(), parts[2].toDouble()))
                }
            }
        }

        BufferedReader(InputStreamReader(am.open("stop_times.txt"))).use { reader ->
            reader.readLine()
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 5) {
                    stopTimes.add(StopTime(parts[0], parts[3], parts[4], parts[2], parts[1].toInt()))
                }
            }
        }

        BufferedReader(InputStreamReader(am.open("trips.txt"))).use { reader ->
            reader.readLine()
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 4) {
                    val headsign = if (parts[3].trim() == "0") "Tripunithura" else "Aluva"
                    trips.add(Trip(parts[2], parts[0], parts[1], headsign))
                }
            }
        }

        BufferedReader(InputStreamReader(am.open("fare_rules.txt"))).use { reader ->
            reader.readLine()
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 3) {
                    // origin_id, destination_id, fare_id
                    fareRules.add(FareRule(parts[2].trim(), "", parts[0].trim(), parts[1].trim()))
                }
            }
        }

        BufferedReader(InputStreamReader(am.open("fare_attributes.txt"))).use { reader ->
            reader.readLine()
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 3) {
                    fareAttributes.add(FareAttribute(parts[0], parts[1].toDouble(), parts[2]))
                }
            }
        }

        BufferedReader(InputStreamReader(am.open("shapes.txt"))).use { reader ->
            reader.readLine()
            reader.forEachLine { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 4) {
                    shapes.add(Shape(parts[0], parts[1].toInt(), parts[2].toDouble(), parts[3].toDouble()))
                }
            }
        }
        
        isLoaded = true
    }
}

