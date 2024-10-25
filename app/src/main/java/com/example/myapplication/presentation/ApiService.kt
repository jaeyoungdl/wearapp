package com.example.myapplication.presentation

import com.example.myapplication.presentation.data.BLELocationRequestData
import com.example.myapplication.presentation.data.HealthResponseData
import com.example.myapplication.presentation.data.LocationRequestData
import com.example.myapplication.presentation.data.LoginRequestData
import com.example.myapplication.presentation.data.LoginResponseData
import com.example.myapplication.presentation.data.OTAResponseData
import com.example.myapplication.presentation.data.OTAVariablesResponse
import com.example.myapplication.presentation.data.SOSRequestData
import com.example.myapplication.presentation.data.SensorRequestData
import com.example.myapplication.presentation.data.SensorResponseData
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/user/getOTAVariables")
    suspend fun getOTAVariables(
        @Query("ship_id") ship_id: Int,
    ) : Response<OTAVariablesResponse>

    @POST("api/user/sos_request")
    suspend fun send_sos_request(
        @Body requestData: SOSRequestData
    ) : Response<HealthResponseData>

    @POST("api/user/sendSensorData")
    suspend fun sendSensorData(
        @Body requestData:SensorRequestData
    ): Response<SensorResponseData>

    @POST("api/user/sendLocationData")
    suspend fun sendLocationData(
        @Body requestData:LocationRequestData
    ): Response<SensorResponseData>

    @POST("api/user/sendBLELocationData")
    suspend fun sendBLELocationData(
        @Body requestData: List<BLELocationRequestData>
    ): Response<SensorResponseData>

    @POST("api/user/login")
    suspend fun login(
        @Body requestData: LoginRequestData
    ): Response<LoginResponseData>
}