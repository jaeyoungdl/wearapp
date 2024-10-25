package com.example.myapplication.presentation.data

data class BLEScanDevice(
    val rssi_list: MutableList<Int>,
    val mac_address: String
)
