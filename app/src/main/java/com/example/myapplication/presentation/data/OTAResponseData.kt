package com.example.myapplication.presentation.data

data class OTAVariablesResponse(
    val data: OTAResponseData
)

data class OTAResponseData (
    val bluetooth_scan_interval: Long?,
    val bluetooth_sampling_interval: Long?,
    val sensor_interval: Long?,
    val fall_detection_time: Long?,
    val fall_detection_gravity: Float?,
    val location_interval: Long?,
    val fall_detection_landing_gravity: Float?,
    val fall_detection_landing_time: Float?,
    val fall_detection_min_lying_gravity: Float?,
    val fall_detection_max_lying_gravity: Float?
)