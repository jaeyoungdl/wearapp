package com.example.myapplication.presentation.data

import java.util.Date

data class LoginResponseData(
    val id: Int,
    val user_id: String,
    val name: String,
    val email: String,
    val phone: String,
    val birth: String,
    val age: Int,
    val gender: String,
    val zip_code: Int,
    val road_name: String,
    val address: String,
    val join_date: Date?,
    val company_name: String,
    val ship_name: String,
    val crew_level: String,
    val group_name: String,
    val beacon_name: String,
    val ship_id: Int,
)