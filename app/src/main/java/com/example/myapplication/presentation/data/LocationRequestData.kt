package com.example.myapplication.presentation.data

data class LocationRequestData(
    val userID : Int,
    val userName : String,
    val latitude: Double,
    val longitude: Double,
    val battery: Int?
)
