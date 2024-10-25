package com.example.myapplication.presentation.data

data class LoginRequestData(
    val id: String,
    val password: String,
    val device_id: String,
    val battery: Int?,
)
