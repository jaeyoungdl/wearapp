package com.example.myapplication.presentation.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.presentation.ApiService
import com.example.myapplication.presentation.const_val.FALL_DETECTION_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_LANDING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_LANDING_TIME
import com.example.myapplication.presentation.const_val.FALL_DETECTION_MAX_LYING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_MIN_LYING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_TIME
import com.example.myapplication.presentation.data.SOSRequestData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.sqrt


class FallDetectionService : Service(), SensorEventListener {
    private val TAG: String = "FallDetectionService"
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var gyroScopeSensor: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var startTime = 0L
    private var freeFallFlag = false
    private var landingFlag = false
    private var running = true
    private val timerHandler = Handler(Looper.getMainLooper())
    private var fallDetectionTime: Long = 0
    private var acceleration_g: Float = 0f

    private var ACC_THRESHOLD: Float = FALL_DETECTION_GRAVITY
    private var TIME_THRESHOLD: Float = FALL_DETECTION_TIME.toFloat()
    private var ACC_LANDING_THRESHOLD : Float = FALL_DETECTION_LANDING_GRAVITY
    private var TIME_LANDING_THRESHOLD: Float = FALL_DETECTION_LANDING_TIME
    private var MIN_LYING_ACC_THRESHOLD: Float = FALL_DETECTION_MIN_LYING_GRAVITY
    private var MAX_LYING_ACC_THRESHOLD: Float = FALL_DETECTION_MAX_LYING_GRAVITY


    private val TIME_THRESHOLD_AFTER_DETECTION: Int = 10

    private lateinit var fusedLocationManager: FusedLocationProviderClient

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    private val fallDetectionTimer = object : Runnable {
        override fun run() {
            if (!running) return
            val millis = System.currentTimeMillis() - startTime
            val seconds = (millis.toFloat() / 1000)
//            Log.d(TAG, "ACC : $acceleration_g | freeFallFlag : $freeFallFlag | landingFlag : $landingFlag | seconds(ms) : ${seconds}")
//            Log.d(TAG, "TIME LANDING_THRESHOLD : $TIME_LANDING_THRESHOLD | TIME_THRESHOLD : $TIME_THRESHOLD")
            // Check for landing condition within 1 second after a free fall
            if (seconds <= 0.2f && acceleration_g > ACC_LANDING_THRESHOLD) {
                Log.d(TAG, "Check for landing condition within 1 second after a free fall")
                landingFlag = true
            }else if (seconds in TIME_LANDING_THRESHOLD .. TIME_THRESHOLD && landingFlag){
                if (acceleration_g !in MIN_LYING_ACC_THRESHOLD..MAX_LYING_ACC_THRESHOLD){
                    Log.d(TAG, "reset Detection")
                    resetDetection()
                }
            }else if(seconds > TIME_THRESHOLD && landingFlag){
                Log.d(TAG, "conclude Fall")
                concludeFall()
            }else if(seconds > TIME_THRESHOLD){
                resetDetection()
            } else{
                Log.d(TAG, "Waiting for Fall Detection...")
            }
            timerHandler.postDelayed(this, 50)
        }
    }

    private var avgHR: Double = 0.0
    private var user_index: Int = -1

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        fusedLocationManager = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroScopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        // 서비스를 포그라운드로 실행하기 위한 설정
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("심박수 측정 중")
            .setContentText("심박수 데이터를 수집하고 있습니다.")
            .setSmallIcon(R.drawable.splash_icon) // 적절한 아이콘으로 교체해야 합니다.
            .build()

        startForeground(1, notification)

        startDataCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Intent에서 데이터 추출
        if(intent != null){
            user_index = intent.getIntExtra("user_index",-1)
            ACC_THRESHOLD = intent.getFloatExtra("fall_detection_gravity", ACC_THRESHOLD)
            TIME_THRESHOLD = intent.getLongExtra("fall_detection_time", TIME_THRESHOLD.toLong()).toFloat()
            MAX_LYING_ACC_THRESHOLD = intent.getFloatExtra("fall_detection_max_lying_gravity",MAX_LYING_ACC_THRESHOLD )
            MIN_LYING_ACC_THRESHOLD = intent.getFloatExtra("fall_detection_min_lying_gravity", MIN_LYING_ACC_THRESHOLD)
            TIME_LANDING_THRESHOLD = intent.getFloatExtra("fall_detection_landing_time",TIME_LANDING_THRESHOLD)
            ACC_LANDING_THRESHOLD = intent.getFloatExtra("fall_detection_landing_gravity",ACC_LANDING_THRESHOLD)
        }
        Log.d(TAG, "onStartCommand - user_index : $user_index")
        Log.d(TAG, "onStartCommand - ACC_THRESHOLD : $ACC_THRESHOLD")
        Log.d(TAG, "onStartCommand - TIME_THRESHOLD : $TIME_THRESHOLD")
        Log.d(TAG, "onStartCommand - MAX_LYING_ACC_THRESHOLD : $MAX_LYING_ACC_THRESHOLD")
        Log.d(TAG, "onStartCommand - MIN_LYING_ACC_THRESHOLD : $MIN_LYING_ACC_THRESHOLD")
        Log.d(TAG, "onStartCommand - TIME_LANDING_THRESHOLD : $TIME_LANDING_THRESHOLD")
        Log.d(TAG, "onStartCommand - ACC_LANDING_THRESHOLD : $ACC_LANDING_THRESHOLD")
        // 서비스 작업을 계속 진행하도록 설정
        return START_STICKY
    }

    private fun startDataCollection() {
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
//                Log.d(TAG, "[Acc] X: ${event.values[0]} Y: ${event.values[1]} Z: ${event.values[2]}")
                handleAccelerometerData(event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
//                Log.d(TAG, "[Gyroscope] X: ${event.values[0]} Y: ${event.values[1]} Z: ${event.values[2]}")
            }
        }
    }

    private fun handleAccelerometerData(values: FloatArray) {
        val ax = values[0]
        val ay = values[1]
        val az = values[2]

        val acceleration = sqrt(ax * ax + ay * ay + az * az)
        acceleration_g = acceleration / SensorManager.GRAVITY_EARTH
//        Log.d(TAG, "Acc : $acceleration_g")
        val timeAfterDetection = ((System.currentTimeMillis() - fallDetectionTime)/1000).toInt()
        if (acceleration_g > ACC_THRESHOLD && timeAfterDetection > TIME_THRESHOLD_AFTER_DETECTION) {
            freeFallFlag = true
            if(!running){
                running = true
            }
            if(!landingFlag){
                startTime = System.currentTimeMillis()
            }
            timerHandler.post(fallDetectionTimer)
        }
    }

    private fun resetDetection() {
//        Log.d(TAG, "Reset Detection")
        timerHandler.removeCallbacks(fallDetectionTimer)
        freeFallFlag = false
        landingFlag = false
        running = false
    }

    private fun sendFallDetectedBroadcast() {
        val intent = Intent("FALL_DETECTED")
        sendBroadcast(intent)
        Log.d(TAG, "Fall detected broadcast sent")
    }

    private fun concludeFall() {
//        Log.d(TAG,"Fall Detected !! FUCK!!!")
        resetDetection()
        fallDetectionTime = System.currentTimeMillis()
        sendFallDetectedBroadcast()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 정확도 변경 시 필요한 처리를 여기에 작성합니다.
    }





    override fun onDestroy() {
        // 서비스가 종료될 때 리스너 해제
        sensorManager.unregisterListener(this, accelerometerSensor)
//        sensorManager.unregisterListener(this, gyroScopeSensor)
//        sensorManager.unregisterListener(this, gravitySensor)
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
        const val CHANNEL_ID = "SensorServiceChannel"
    }
}
