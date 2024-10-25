package com.example.myapplication.presentation.data

data class SOSRequestData(
    val user_index : Int,
    val currentLat : Double,
    val currentLng : Double,
    val emergency_code : String,
)
