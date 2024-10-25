package com.example.myapplication.presentation.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.presentation.ApiService
import com.example.myapplication.presentation.const_val.SENSOR_INTERVAL
import com.example.myapplication.presentation.data.SensorRequestData
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTracker.TrackerError
import com.samsung.android.service.health.tracking.HealthTracker.TrackerEventListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Timer
import java.util.TimerTask


class SensorService : Service() {
    private val TAG: String = "SensorService"
    private var timer: Timer? = null
    private var TIMER_INTERVAL = SENSOR_INTERVAL

    private var healthTrackingService: HealthTrackingService? = null
    private var isHandlerRunning = false
    private val handler = Handler(Looper.myLooper()!!)
    private var spo2Tracker: HealthTracker? = null
    private var HRTracker: HealthTracker? = null
    private var skinTemperatureTracker: HealthTracker? = null
    private var prevStatus = -100

    private val NOTIFICATION_ID = 101
    private var heartRate = 0
    private var SpO2 = 0
    private var skinTemperature = 0f
    private var user_index: Int = -1

    private val MAX_RETRY_SPO2_COUNT : Int = 5
    private var retry_spo2_count: Int = 0
    private val MIN_VALID_SKIN_TEMPERATURE: Float = 0f
    private val MAX_VALID_SKIN_TEMPERATURE: Float = 45f
    private val MAX_RETRY_SKIN_TEMP_COUNT : Int = 10
    private var retry_skin_temp_count: Int = 0
    private var sendAPI: Boolean = false

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Intent에서 데이터 추출
        if(intent != null){
            user_index = intent.getIntExtra("user_index",-1)
            TIMER_INTERVAL = intent.getLongExtra("sensor_interval", TIMER_INTERVAL)
        }
        Log.d(TAG, "onStartCommand - user_index : $user_index")
        Log.d(TAG, "onStartCommand - TIMER_INTERVAL : $TIMER_INTERVAL")
        return START_STICKY
    }

    private val connectionListener: ConnectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "Connected to HSP")
            try {
                spo2Tracker = healthTrackingService!!.getHealthTracker(HealthTrackerType.SPO2)
                skinTemperatureTracker = healthTrackingService!!.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE)
                HRTracker = healthTrackingService!!.getHealthTracker(HealthTrackerType.HEART_RATE)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.message.toString())
            }
//            handler.postDelayed({
//                startDataCollection("skin_temperature")
//            }, 3000)
        }

        override fun onConnectionEnded() {
            Log.d(TAG, "onConnectionEnded Called")
        }
        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Unable to Connect to HSP")
        }
    }

    private val trackerHREventListener: TrackerEventListener = object : TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (list.isNotEmpty()) {
                Log.i(TAG, "List Size : " + list.size)
                for (dataPoint in list) {
//                    Log.i(TAG, "Timestamp : " + dataPoint.timestamp)
                    val hr = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                    val hrIbi = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_IBI)
                    val status = dataPoint.getValue(ValueKey.HeartRateSet.STATUS)
                    Log.i(TAG, "HR : $hr HR_IBI : $hrIbi Status : $status")
                    heartRate = hr
                    sendAPI = true
                    stop("HR")
//                    val hrIbiList = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)
//                    val hrIbiAccuracy = dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)
//                    val ibiCount = hrIbiList?.size ?: 0
//                    Log.i(TAG, "IBI Count: $ibiCount")
                }
            } else {
                Log.i(TAG, "onDataReceived List is zero")
            }
        }

        override fun onError(p0: TrackerError?) {
            Log.e(TAG, "onError Called")
        }

        override fun onFlushCompleted() {
            Log.d(TAG,"onFlushCompleted Called")
        }
    }

    private val trackerSpO2EventListener: TrackerEventListener = object : TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (list.isNotEmpty()) {
//                Log.i(TAG, "List Size : " + list.size)
                for (dataPoint in list) {
                    val status = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
//                    Log.i(TAG, "Status OutSide : $status")
                    if (prevStatus != status) {
                        prevStatus = status
                        Log.i(TAG, "Status Inside : $status")
                        Log.d(TAG,"SpO2 : ${dataPoint.getValue<Int>(ValueKey.SpO2Set.SPO2)}")
                        Log.d(TAG,"HR : ${dataPoint.getValue<Int>(ValueKey.SpO2Set.HEART_RATE)}")
                        if (status == 2) {
                            SpO2 = dataPoint.getValue<Int>(ValueKey.SpO2Set.SPO2)
                            heartRate = dataPoint.getValue<Int>(ValueKey.SpO2Set.HEART_RATE)
                            retry_spo2_count = 0
                            sendAPI = true
                            stop("SpO2")
                        } else if (status == 0) {

                        } else if (status == -4) {
                            Log.d(TAG,"Moving : $status")
                        } else if (status == -5) {
                            Log.d(TAG, "Low Signal : $status")
                        } else {
                            Log.d(TAG, "Timeout : $status")
                            if(retry_spo2_count == MAX_RETRY_SPO2_COUNT){
                                retry_spo2_count = 0
                                stop("SpO2")
                                sendAPI = true
                            }else if(retry_spo2_count < MAX_RETRY_SPO2_COUNT){
                                stop("SpO2")
                                handler.postDelayed({
                                    Log.d(TAG, "Data Collection Failed, Retry... (${retry_spo2_count+1}/$MAX_RETRY_SPO2_COUNT)")
                                    retry_spo2_count++
                                    startDataCollection("SpO2")
                                }, 1000)
                            }
                        }

                    }
                }
            }
        }

        override fun onFlushCompleted() {
            Log.d(TAG,"onFlushCompleted Called")
        }

        override fun onError(trackerError: TrackerError) {
            Log.e(TAG, "onError Called")
            if (trackerError == TrackerError.PERMISSION_ERROR) {
                Log.e(TAG,"Permission Check Failed")
            }
            if (trackerError == TrackerError.SDK_POLICY_ERROR) {
                Log.e(TAG, "SDK Policy denied")
            }
            isHandlerRunning = false
        }
    }

    private val trackerSkinTempEventListener: TrackerEventListener = object : TrackerEventListener {
        override fun onDataReceived(list: List<DataPoint>) {
            if (list.isNotEmpty()) {
                for (dataPoint in list) {
                    val objectTemperature =
                        dataPoint.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    val ambientTemperature =
                        dataPoint.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                    val status = dataPoint.getValue(ValueKey.SkinTemperatureSet.STATUS)
                    Log.i(
                        TAG,
                        "Skin Temperature on-demand value / object: $objectTemperature, ambient: $ambientTemperature, status: $status"
                    )
                    if (MIN_VALID_SKIN_TEMPERATURE < objectTemperature && MAX_VALID_SKIN_TEMPERATURE > objectTemperature){
                        Log.d(TAG,"Valid Temperature Collected, Stop and start Collecting SpO2")
                        skinTemperature = objectTemperature
                        stop("skin_temperature")
                        retry_skin_temp_count = 0
                        handler.postDelayed({
                            startDataCollection("SpO2")
                        }, 3000)
                    }else if(MAX_RETRY_SKIN_TEMP_COUNT == retry_skin_temp_count){
                        Log.d(TAG,"Skin Temperature Collection Failed, Stop and start Collecting SpO2")
                        retry_skin_temp_count = 0
                        stop("skin_temperature")
                        handler.postDelayed({
                            startDataCollection("SpO2")
                        }, 3000)
                    }
                    else{
                        Log.d(TAG, "InValid Skin Temperature, Retry... ${retry_skin_temp_count+1} / ${MAX_RETRY_SKIN_TEMP_COUNT}")
                        retry_skin_temp_count++
                    }
                }
            }
        }

        override fun onFlushCompleted() {
            Log.d(TAG,"onFlushCompleted Called")
        }

        override fun onError(trackerError: TrackerError) {
            Log.e(TAG, "onError Called")
            if (trackerError == TrackerError.PERMISSION_ERROR) {
                Log.e(TAG,"Permission Check Failed")
            }
            if (trackerError == TrackerError.SDK_POLICY_ERROR) {
                Log.e(TAG, "SDK Policy denied")
            }
            isHandlerRunning = false
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        // 서비스를 포그라운드로 실행하기 위한 설정
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("센서 데이터 측정 중")
            .setContentText("센서 데이터를 수집하고 있습니다.")
            .setSmallIcon(R.drawable.splash_icon) // 적절한 아이콘으로 교체해야 합니다.
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Waiting for sensor booted... ( 5 seconds )")
        setUp()
        handler.postDelayed({
            Log.d(TAG, "Waiting for sensor booted Complete")
            // 심박수 센서 리스너 등록
            timer = Timer()
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    startDataCollection("skin_temperature")
                }
            }, 0, TIMER_INTERVAL)
        }, 5000)


    }

    fun setUp() {
        healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
        healthTrackingService!!.connectService()
    }

    private fun startDataCollection(dataType: String) {
        Log.d(TAG, "startDataCollection")
        if (!isHandlerRunning) {
            prevStatus = -100
            handler.post {
                when(dataType){
                    "skin_temperature" -> {
                        skinTemperatureTracker!!.setEventListener(trackerSkinTempEventListener)
                    }
                    "SpO2" -> {
                        spo2Tracker!!.setEventListener(trackerSpO2EventListener)
                    }
                    "HR" -> {
                        HRTracker!!.setEventListener(trackerHREventListener)
                    }
                }
                isHandlerRunning = true
            }
        }
    }

    fun stop(dataType: String) {
        when(dataType){
            "skin_temperature" -> {
                if(skinTemperatureTracker != null){
                    skinTemperatureTracker!!.unsetEventListener()
                }
            }
            "SpO2" -> {
                if (spo2Tracker != null) {
                    spo2Tracker!!.unsetEventListener()
                }
                if(sendAPI){
                    apiRequest()
                    sendAPI = false
                }
            }
            "HR" -> {
                if (HRTracker != null) {
                    HRTracker!!.unsetEventListener()
                }
                if(sendAPI){
                    apiRequest()
                    sendAPI = false
                }
            }
        }
        handler.removeCallbacksAndMessages(null)
        isHandlerRunning = false
    }

    fun apiRequest(){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(
            GsonConverterFactory.create()).addCallAdapterFactory(CoroutineCallAdapterFactory()).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val sensorData = SensorRequestData(heartRate = heartRate, userID = user_index, SpO2 = SpO2, skinTemperature = skinTemperature)

        customScope.launch {
            try {
                val response = apiService.sendSensorData(sensorData)
                when(response.code()){
                    200 -> {
                        Log.d(TAG, "Sensor Send Success")
                    }
                    400 -> {
                        Log.d(TAG, "Sensor Send Failed with code 400")
                    }
                    else -> {
                        Log.d(TAG, "Sensor Send Failed with code ${response.code()}")
                    }
                }
            }catch(e: Exception){
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    override fun onDestroy() {
        // 서비스가 종료될 때 리스너 해제
        timer?.cancel()

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitoring",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        const val CHANNEL_ID = "SensorTestServiceChannel"
    }
}
