package com.example.myapplication.presentation.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BLELocationRequestData(
    val beacon_uuid: String,
    val userID : Int,
    val rssi: Int,
    val scan_time: String
){
    companion object {
        fun create(rssi: Int, userID: Int, beacon_uuid: String): BLELocationRequestData {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul") // KST 설정
            val timeStamp = dateFormat.format(Date())
            return BLELocationRequestData(rssi = rssi, userID = userID, beacon_uuid = beacon_uuid, scan_time = timeStamp)
        }
    }
}
