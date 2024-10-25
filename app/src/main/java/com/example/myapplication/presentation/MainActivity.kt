/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.example.myapplication.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.myapplication.R
import com.example.myapplication.presentation.const_val.BLUETOOTH_SCAN_INTERVAL
import com.example.myapplication.presentation.const_val.FALL_DETECTION_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_LANDING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_LANDING_TIME
import com.example.myapplication.presentation.const_val.FALL_DETECTION_MAX_LYING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_MIN_LYING_GRAVITY
import com.example.myapplication.presentation.const_val.FALL_DETECTION_TIME
import com.example.myapplication.presentation.const_val.LOCATION_INTERVAL
import com.example.myapplication.presentation.const_val.SENSOR_INTERVAL
import com.example.myapplication.presentation.data.SOSRequestData
import com.example.myapplication.presentation.service.BLEScanTestService
import com.example.myapplication.presentation.service.FallDetectionService
import com.example.myapplication.presentation.service.LocationService
import com.example.myapplication.presentation.service.SensorService
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.format.TextStyle


class MainActivity : ComponentActivity() {
    private val TAG: String = "MainActivity"
    private var backPressCount = 0
    private var firstBackPressTime: Long = 0

    private lateinit var fallDetectionReceiver: BroadcastReceiver
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var audioManager: AudioManager

    private lateinit var fusedLocationManager: FusedLocationProviderClient
    var mainHandler: Handler = Handler(Looper.getMainLooper())

    private val fallDetectedState = mutableStateOf(false)

    private var user_name: String = "기본 유저"  // 클래스 변수로 선언
    private var user_index: Int = 1 // 클래스 변수로 선언
    private var device_id: String = ""
    private var beacon_name: String = ""
    // OTA Variables
    private var bluetooth_scan_interval: Long? = null
    private var bluetooth_sampling_interval: Long? = null
    private var sensor_interval: Long? = null
    private var fall_detection_time: Long? = null
    private var fall_detection_gravity: Float? = null
    private var location_interval: Long? = null
    private var fall_detection_landing_gravity: Float? = null
    private var fall_detection_landing_time: Float? = null
    private var fall_detection_min_lying_gravity: Float? = null
    private var fall_detection_max_lying_gravity: Float? = null

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    private val callback = object : OnBackPressedCallback(true){
        override fun handleOnBackPressed() {
                Log.d(TAG,"back pressed $backPressCount")
                // 현재 시간 기록
                val currentTime = System.currentTimeMillis()
                // 첫 번째 뒤로 가기 버튼 누름이거나 1초 이상 지났으면 상태 초기화
                if (firstBackPressTime == 0L || currentTime - firstBackPressTime > 500) {
                    Log.d(TAG,"초기화 back pressed $backPressCount")
                    firstBackPressTime = currentTime
                    backPressCount = 1
                }else{
                    Log.d(TAG,"Plus back pressed $backPressCount")
                    backPressCount++
                }
                if (backPressCount == 2) {
                    Log.d(TAG,"Send Location Signal back pressed $backPressCount")
                    backPressCount = 0
                    firstBackPressTime = 0
                    getCurrentLocation()
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면이 꺼지지 않도록 설정
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTheme(android.R.style.Theme_DeviceDefault)
        this.onBackPressedDispatcher.addCallback(this, callback) //위에서 생성한 콜백 인스턴스 붙여주기 }
        mediaPlayer = MediaPlayer.create(this, R.raw.emergency_alarm)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val intent : Intent = intent
        user_name = intent.getStringExtra("user_name") ?: "기본 유저"
        user_index = intent.getIntExtra("user_index", -1)
        device_id = intent.getStringExtra("device_id") ?: ""
        beacon_name = intent.getStringExtra("beacon_name") ?: ""

        bluetooth_scan_interval = intent.getLongExtra("bluetooth_scan_interval", BLUETOOTH_SCAN_INTERVAL)
        bluetooth_sampling_interval = intent.getLongExtra("bluetooth_sampling_interval", BLUETOOTH_SCAN_INTERVAL)
        sensor_interval = intent.getLongExtra("sensor_interval", SENSOR_INTERVAL)
        fall_detection_time = intent.getLongExtra("fall_detection_time", FALL_DETECTION_TIME)
        fall_detection_gravity = intent.getFloatExtra("fall_detection_gravity", FALL_DETECTION_GRAVITY)
        location_interval = intent.getLongExtra("location_interval", LOCATION_INTERVAL)
        fall_detection_landing_gravity = intent.getFloatExtra("fall_detection_landing_gravity", FALL_DETECTION_LANDING_GRAVITY)
        fall_detection_landing_time = intent.getFloatExtra("fall_detection_landing_time", FALL_DETECTION_LANDING_TIME)
        fall_detection_min_lying_gravity = intent.getFloatExtra("fall_detection_min_lying_gravity", FALL_DETECTION_MIN_LYING_GRAVITY)
        fall_detection_max_lying_gravity = intent.getFloatExtra("fall_detection_max_lying_gravity", FALL_DETECTION_MAX_LYING_GRAVITY)

        Log.d(TAG, "Intent\nUser Name : $user_name\nUser Index : $user_index\nDevice ID : $device_id\nBeacon Name : $beacon_name")
        Log.d(TAG, "OTA Values\nbluetooth_scan_interval : $bluetooth_scan_interval")
        Log.d(TAG, "bluetooth_sampling_interval : $bluetooth_sampling_interval")
        Log.d(TAG, "sensor_interval : $sensor_interval")
        Log.d(TAG, "fall_detection_time : $fall_detection_time")
        Log.d(TAG, "fall_detection_gravity : $fall_detection_gravity")
        Log.d(TAG, "location_interval : $location_interval")
        Log.d(TAG, "fall_detection_landing_gravity : $fall_detection_landing_gravity")
        Log.d(TAG, "fall_detection_landing_time : $fall_detection_landing_time")
        Log.d(TAG, "fall_detection_min_lying_gravity : $fall_detection_min_lying_gravity")
        Log.d(TAG, "fall_detection_max_lying_gravity : $fall_detection_max_lying_gravity")

        setContent {
            Main_pager(
                context = this,
                user_name = user_name
            )
        }
        initFallDetectionReceiver()
        init_locationManager()
        mainHandler.postDelayed({
            registerServices()
        }, 5000)
    }

    private fun initFallDetectionReceiver() {
        fallDetectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if(intent?.action == "FALL_DETECTED"){
                    Log.d(TAG,"Fall Detection Received")
                    fallDetectedState.value = true
                }
            }
        }
        val intentFilter = IntentFilter("FALL_DETECTED")
        registerReceiver(fallDetectionReceiver, intentFilter)
    }

    fun registerServices(){
        registerLocationService()
        registerBLEService()
        registerSensorService()
        registerFallDetectionService()
    }

    fun unRegisterServices(){
        unRegisterLocationService()
        unRegisterBLEService()
        unRegisterSensorService()
        unRegisterFallDetectionService()
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onRestart() {
        super.onRestart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        unregisterReceiver(fallDetectionReceiver)
        unRegisterServices()
        super.onDestroy()
    }

    fun registerFallDetectionService(){
        Log.d(TAG, "Register Fall Detection Service")
        val serviceIntent = Intent(this, FallDetectionService::class.java).apply {
            putExtra("user_index", user_index)
            putExtra("fall_detection_gravity", fall_detection_gravity)
            putExtra("fall_detection_time",fall_detection_time)
            putExtra("fall_detection_landing_gravity", fall_detection_landing_gravity)
            putExtra("fall_detection_landing_time",fall_detection_landing_time)
            putExtra("fall_detection_min_lying_gravity", fall_detection_min_lying_gravity)
            putExtra("fall_detection_max_lying_gravity",fall_detection_max_lying_gravity)
        }
        startForegroundService(serviceIntent)
    }

    fun unRegisterFallDetectionService(){
        val serviceIntent = Intent(this, FallDetectionService::class.java)
        stopService(serviceIntent)
    }

    fun registerLocationService(){
        Log.d(TAG, "Register Location Service")
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            putExtra("user_index", user_index) // 사용자 ID를 파라미터로 전달
            putExtra("user_name", user_name)
            putExtra("location_interval", location_interval)
        }
        startForegroundService(serviceIntent)
    }

    fun unRegisterLocationService(){
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }

    fun registerSensorService(){
        Log.d(TAG, "Register Sensor Service")
        val serviceIntent = Intent(this, SensorService::class.java).apply {
            putExtra("user_index", user_index) // 사용자 ID를 파라미터로 전달
            putExtra("sensor_interval", sensor_interval)
        }
//        startForegroundService(serviceIntent)
    }

    fun unRegisterSensorService(){
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
    }

    fun registerBLEService(){
        Log.d(TAG, "Register BLE Service")
        val serviceIntent = Intent(this, BLEScanTestService::class.java).apply {
            putExtra("user_index", user_index) // 사용자 ID를 파라미터로 전달
            putExtra("beacon_name", beacon_name)
            putExtra("bluetooth_scan_interval", bluetooth_scan_interval)
            putExtra("bluetooth_sampling_interval", bluetooth_sampling_interval)
        }
        startForegroundService(serviceIntent)
    }

    fun unRegisterBLEService(){
        val serviceIntent = Intent(this, BLEScanTestService::class.java)
        stopService(serviceIntent)
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
                            apiRequest(latitude = location.latitude, longitude = location.longitude, user_index)
                    } else {
                        Log.d(TAG, "No location retrieved")
                        fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                            if(location != null){
                                Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                                    apiRequest(latitude = location.latitude, longitude = location.longitude, user_index)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                        if(location != null){
                            Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                                apiRequest(latitude = location.latitude, longitude = location.longitude, user_index)
                        }
                    }
                }
        }catch (e: SecurityException) {
            Log.e(TAG, "FUck!! Error Occured While get Location")
            e.printStackTrace()
        }
    }

    private fun getCurrentLocationForFallDetection()  {
        val cancellationTokenSource = CancellationTokenSource()
        try{
            fusedLocationManager.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Current location: (${location.latitude}, ${location.longitude})")
                        apiRequestForFallDetection(latitude = location.latitude, longitude = location.longitude, user_index)
                    } else {
                        Log.d(TAG, "No location retrieved")
                        fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                            if(location != null){
                                Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                                apiRequestForFallDetection(latitude = location.latitude, longitude = location.longitude, user_index)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get location", e)
                    fusedLocationManager.lastLocation.addOnSuccessListener {location ->
                        if(location != null){
                            Log.d(TAG, "Current location With Last Location: (${location.latitude}, ${location.longitude})")
                            apiRequestForFallDetection(latitude = location.latitude, longitude = location.longitude, user_index)
                        }
                    }
                }
        }catch (e: SecurityException) {
            Log.e(TAG, "FUck!! Error Occured While get Location")
            e.printStackTrace()
        }
    }


    fun apiRequest(latitude: Double, longitude: Double, user_index: Int){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(
            CoroutineCallAdapterFactory()
        ).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val SOSRequestItem : SOSRequestData = SOSRequestData(currentLat = latitude, currentLng = longitude, user_index = user_index, emergency_code = "SOS")
            customScope.launch {
                try {
                    val response = apiService.send_sos_request(SOSRequestItem)
                when(response.code()){
                    200 -> {

                    }
                    400 -> {

                    }
                    else -> {

                    }
                }
            }catch(e: Exception){
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    fun apiRequestForFallDetection(latitude: Double, longitude: Double, user_index: Int){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(
                CoroutineCallAdapterFactory()
            ).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val SOSRequestItem : SOSRequestData = SOSRequestData(currentLat = latitude, currentLng = longitude, user_index = user_index, emergency_code = "낙상")
        customScope.launch {
            try {
                val response = apiService.send_sos_request(SOSRequestItem)
                when(response.code()){
                    200 -> {

                    }
                    400 -> {

                    }
                    else -> {

                    }
                }
            }catch(e: Exception){
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalPagerApi::class)
    @Composable
    fun Main_pager(
        context: Context,
        user_name: String?,
    ){
        val pagerState = rememberPagerState()
        val countDownTime : Int = 20
        var countdownTime by remember { mutableIntStateOf(countDownTime) } // 10초 카운트다운 초기화
        if (fallDetectedState.value) {
            LaunchedEffect(Unit) {
                countdownTime = countDownTime // 카운트다운 초기화
                // 진동 울리기
                val pattern = longArrayOf(0, 500, 50, 500, 50, 500,50, 500,50, 500, 50, 500, 50,500) // 0ms 대기, 2초 진동, 1초 멈춤, 2초 진동, 1초 멈춤, 2초 진동
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                while (countdownTime > 0) {
                    delay(1000)
                    countdownTime--
                }
                if(fallDetectedState.value){
                     Log.d(TAG, "구조 취소 없음. 신호 보냄")
                    concludeFall()
                }
                fallDetectedState.value = false
            }
        }
        HorizontalPager(
            count = 1,
            state = pagerState
        ){
                page ->
            when(page){
                0 -> {
                    if (fallDetectedState.value) {
                        // 낙상 감지 시 하얀색 원을 꽉 채워 그립니다.
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "낙상 감지됨",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "도움이 필요하세요?",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(15.dp))
                            Text(
                                text = "$countdownTime 초",
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            // 두 버튼을 수평으로 배치
                            Row(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = {
                                        fallDetectedState.value = false
                                        Log.d(TAG, "구조 취소")
                                    },
                                    modifier = Modifier.padding(8.dp).background(Color.Green)
                                ) {
                                    Text("취소")
                                }
                                Button(
                                    onClick = {
                                        fallDetectedState.value = false
                                        Log.d(TAG, "구조 신호 보냄")
                                    },
                                    modifier = Modifier.padding(8.dp).background(Color.Red)
                                ) {
                                    Text("요청")
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colors.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "[승조원 관리 시스템]",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    "${user_name}님 환영합니다",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(15.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(context, LoginActivity::class.java)
                                        context.startActivity(intent)
                                        (context as? ComponentActivity)?.finish()
                                    },
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(40.dp) //.padding(8.dp)
                                ) {
                                    Text("로그아웃", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                else -> {

                }
            }
        }
    }

    private fun concludeFall() {
        getCurrentLocationForFallDetection()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
        if(!mediaPlayer.isPlaying){
            mediaPlayer.start()
            mainHandler.postDelayed({
                if(mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                    mediaPlayer.prepare()
                }
            }, 5000)
        }
    }

}
