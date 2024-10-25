package com.example.myapplication.presentation.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.presentation.ApiService
import com.example.myapplication.presentation.const_val.LOCATION_INTERVAL
import com.example.myapplication.presentation.data.LocationRequestData
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
import java.util.*


class LocationService : Service() {
    private val TAG:String = "Location Service"
    private val handler = Handler(Looper.myLooper()!!)
    private var TIMER_INTERVAL: Long = LOCATION_INTERVAL
    private val NOTIFICATION_CHANNEL_ID = "LocationService"
    private val NOTIFICATION_ID = 101
    private var timer: Timer? = null
    private lateinit var fusedLocationManager: FusedLocationProviderClient
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0
    private val LOCATION_UPDATE_TIME_MS: Long = 5 * 60 * 1000 // 5분 * 60초 * 1000ms
    private var user_index: Int = -1
    private var user_name: String = ""

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        // 서비스를 포그라운드로 실행하기 위한 설정
        createNotificationChannel()
        val notification = Notification.Builder(this, SensorService.CHANNEL_ID)
            .setContentTitle("위치 데이터 수집중")
            .setContentText("위치 데이터를 수집하고 있습니다.")
            .setSmallIcon(R.drawable.splash_icon) // 적절한 아이콘으로 교체해야 합니다.
            .build()
        startForeground(NOTIFICATION_ID, notification)

        // 심박수 센서 리스너 등록
        Log.d(TAG,"Waiting for Location Manager Initialized... (5 Seconds)")
        init_locationManager()
        handler.postDelayed({
            timer = Timer()
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    getCurrentLocation()
                }
            }, 0, TIMER_INTERVAL)
//            }, 0, TIMER_INTERVAL)
        }, 5000)
    }

    private fun init_locationManager(){
        fusedLocationManager = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun getCurrentLocation()  {
        val cancellationTokenSource = CancellationTokenSource()
        try{
            fusedLocationManager.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Current location: (${location.latitude}, ${location.longitude})")
                        currentLat = location.latitude
                        currentLng = location.longitude
                        apiRequest()
                    } else {
                        Log.d(TAG, "No location retrieved")
                        fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                            if(location != null){
                                Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                                apiRequest()
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                        if(location != null){
                            Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                            apiRequest()
                        }
                    }
                }
        }catch (e: SecurityException) {
            Log.e(TAG, "FUck!! Error Occured While get Location")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Intent에서 데이터 추출
        if(intent != null){
            user_index = intent.getIntExtra("user_index",-1)
            user_name = intent.getStringExtra("user_name") ?: ""
            TIMER_INTERVAL = intent.getLongExtra("location_interval", TIMER_INTERVAL)
        }
        Log.d(TAG, "onStartCommand - user_index : $user_index")
        Log.d(TAG, "onStartCommand - user_name : $user_name")
        Log.d(TAG, "onStartCommand - TIMER_INTERVAL : $TIMER_INTERVAL")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        // 서비스가 종료될 때 리스너 해제
        timer?.cancel()
        super.onDestroy()
    }

    fun getBatteryStatus() : Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, ifilter)
        return batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale
        } ?: -1
    }

    fun apiRequest(){
        val battery = getBatteryStatus()
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(
            GsonConverterFactory.create()).addCallAdapterFactory(CoroutineCallAdapterFactory()).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val locationData = LocationRequestData(latitude = currentLat, longitude = currentLng, battery = battery, userID = user_index, userName = user_name)
        customScope.launch {
            try {
                val response = apiService.sendLocationData(locationData)
                when(response.code()){
                    200 -> {
                        Log.d(TAG, "Location Send Success")
                    }
                    400 -> {
                        Log.d(TAG, "Location Send Failed with code 400")
                    }
                    else -> {
                        Log.d(TAG, "Location Send Failed with code ${response.code()}")
                    }
                }
            }catch(e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    private fun createNotificationChannel() {
        val channelName = "Location Service"
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}