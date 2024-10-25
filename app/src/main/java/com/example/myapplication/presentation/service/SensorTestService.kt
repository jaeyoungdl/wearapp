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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.presentation.ApiService
import com.example.myapplication.presentation.data.SensorRequestData
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Timer
import java.util.TimerTask


class SensorTestService : Service(), SensorEventListener {
    private val TAG: String = "SensorTestService"
    private var timer: Timer? = null
    private val TIMER_INTERVAL = 15 * 60 * 1000L // 5분 (밀리초 단위)
    private val SAMPLE_DURATION = 20 * 1000L // 5초 (밀리초 단위)
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private val handler = Handler(Looper.myLooper()!!)
    private val MIN_VALID_HEARTRATE_COUNT = 5 // 연속된 0이 아닌 심박수 값의 최소 횟수
    private var validHeartRateList = mutableListOf<Double>() // 연속된 0이 아닌 심박수 값의 갯수를 저장할 변수
    private var avgHR: Double = 0.0
    private var user_index: Int = -1

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // 서비스를 포그라운드로 실행하기 위한 설정
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("심박수 측정 중")
            .setContentText("심박수 데이터를 수집하고 있습니다.")
            .setSmallIcon(R.drawable.splash_icon) // 적절한 아이콘으로 교체해야 합니다.
            .build()

        startForeground(1, notification)

        // 심박수 센서 리스너 등록
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                startDataCollection()
            }
        }, 0, TIMER_INTERVAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Intent에서 데이터 추출
        user_index = intent!!.getIntExtra("user_index",-1)
        // Log를 통해 받은 데이터 확인
//        Log.d("BLEScanService", "Received userID: $userID, OtherData: $someOtherData")

        // 서비스 작업을 계속 진행하도록 설정
        return START_STICKY
    }

    private fun startDataCollection() {
        validHeartRateList.clear()
        // 5초 동안 심박수 데이터 수집
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed({
            sensorManager.unregisterListener(this, heartRateSensor)
        }, SAMPLE_DURATION)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            // 여기에 심박수 데이터 처리 로직을 추가합니다.
//            Log.d("SensorService", "Heart Rate: ${event.values[0]}")
            if(event.values[0] > 0){
                validHeartRateList.add(event.values[0].toDouble())
                if(validHeartRateList.size >= MIN_VALID_HEARTRATE_COUNT){
                    avgHR = validHeartRateList.average()
//                    Log.d("SensorService", "5번 수집 완료, 평균 심박수 : $avgHR")
                    val runnable = Runnable {
                        apiRequest()
                    }
                    Thread(runnable).start()
                    sensorManager.unregisterListener(this, heartRateSensor)
                }
            }else {
                validHeartRateList.clear()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 정확도 변경 시 필요한 처리를 여기에 작성합니다.
    }

    fun apiRequest(){
//        Log.d("Button Test", "API Request")
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(
            GsonConverterFactory.create()).addCallAdapterFactory(CoroutineCallAdapterFactory()).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val sensorData = SensorRequestData(heartRate = avgHR.toInt(), userID = user_index, SpO2 = 11, skinTemperature = 1f)
//        Log.d("api Test", "heartRate : $avgHR, User Index : $user_index")

        customScope.launch {
            try {
                val response = apiService.sendSensorData(sensorData)
//        Log.d("Button Test", "Response Code : ${response.code()}")
                when(response.code()){
                    200 -> {
//                Log.d("sensor","200")
//                Log.d("Button Test", "API Response : ${response.body()?.heartRate}")
                    }
                    400 -> {
//                Log.d("sensor","200")
                    }
                    else -> {
//                Log.d("sensor","${response.code()}")
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
        sensorManager.unregisterListener(this, heartRateSensor)
        super.onDestroy()

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Monitoring",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
    }
}
