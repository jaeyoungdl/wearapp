package com.example.myapplication.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.Button
import com.example.myapplication.presentation.data.LoginRequestData
import com.example.myapplication.presentation.data.SOSRequestData
import com.example.myapplication.presentation.service.FallDetectionService
import com.example.myapplication.presentation.ui.theme.MyApplicationTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class LoginActivity : ComponentActivity() {
    private val TAG = "LoginActivity"
    private val APP_VERSION = "1.0"

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
    )

    private var deviceId: String = ""
    private var battery: Int? = -1
    private var phoneNumber: String = ""
    private val callback = object : OnBackPressedCallback(true){
        override fun handleOnBackPressed() {
            Log.d(TAG,"back pressed")
        }
    }

    // For BLE TEST
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var settings: ScanSettings
    private var rssi by mutableFloatStateOf(-100f)
    private var mac_address by mutableStateOf("")
    private var scanStr by mutableStateOf("start")
    private var scanning : Boolean = false
    private var rssi_list: MutableList<Float> = mutableListOf()
    var filters = mutableListOf<ScanFilter>()
    private fun startScan(){
        try{
            bluetoothLeScanner.startScan(filters,settings, scanCallback)
        }catch (e: SecurityException){
            e.printStackTrace()
        }
    }
    private fun stopScan(){
        try{
            bluetoothLeScanner.stopScan(scanCallback)
            scanning = false
        }catch (e: SecurityException){
            e.printStackTrace()
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            val temp_rssi = result?.rssi?.toFloat() ?: -100f
            if(scanning && temp_rssi > -99f){
                rssi_list.add(temp_rssi)
                rssi = rssi_list.average().toFloat()
            }
            mac_address = result?.device?.address ?: "Error"
            Log.d(TAG,"[Device Found] $mac_address (RSSI:$temp_rssi)")
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For BLE TEST
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        var filter: ScanFilter = ScanFilter.Builder().setDeviceName("BF98FF7D58").setDeviceAddress("DC:0D:30:14:C0:D1").build()
        settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        filters.add(filter)

        this.onBackPressedDispatcher.addCallback(this, callback) //위에서 생성한 콜백 인스턴스 붙여주기 }
        this.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT){
            Log.e(TAG,"")
        }
        // Screen Always on Code
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MyApplicationTheme {
                    Main_pager()
            }
        }
        getDeviceID()
        getDevicePhoneNumber()
        getBatteryStatus()

//        registerSensor()
    }

    @SuppressLint("HardwareIds")
    fun getDeviceID(){
        deviceId = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun getBatteryStatus(){
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, ifilter)
        battery = batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale
        }
    }

    @SuppressLint("HardwareIds")
    fun getDevicePhoneNumber(){
        val telManager: TelephonyManager? = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
        }
        else {
            phoneNumber = telManager!!.getLine1Number()
        }
    }

    fun registerSensor(){
        val serviceIntent = Intent(this, FallDetectionService::class.java).apply {
            putExtra("user_index", 2) // 사용자 ID를 파라미터로 전달
        }
        startForegroundService(serviceIntent)
    }

    @OptIn(ExperimentalPagerApi::class)
    @Composable
    fun Main_pager(
    ) {
        val pagerState = rememberPagerState()
        HorizontalPager(
            count = 3,
            state = pagerState
        ){
                page ->
            when(page) {
                0 -> {
//                    BluetoothTestScreen()
                    LoginScreen()
                }
                1 -> {
                    DeviceInfoScreen()
                }
            }
        }
    }

    @Composable
    fun BluetoothTestScreen(){
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MAC : $mac_address",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                fontSize = 18.sp)
            Text("RSSI : $rssi" ,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                fontSize = 18.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    scanning = when(scanning){
                        true -> {
                            stopScan()
                            false
                        }
                        false -> {
                            rssi_list.clear()
                            startScan()
                            true
                        }
                    }
                }) {
                    Text(text = when(scanning){
                        true -> {
                            "stop"
                        }
                        false -> {
                            "start"
                        }
                    })
                }
            }
        }
    }

    @Composable
    fun DeviceInfoScreen(){
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "[Device ID]\n$deviceId\n[Battery]\n$battery%\n[전화번호]\n$phoneNumber\nVERSION\n$APP_VERSION",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                fontSize = 12.sp
            )
        }
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    @Composable
    fun LoginScreen() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
//            modifier = Modifer.
//            modifier = Modifier.padding(29.dp)
        ) {
            // 이메일 입력 필드
            var email = remember { mutableStateOf("") }
            OutlinedTextField(
                value = email.value,
                onValueChange = { email.value = it },
                label = { androidx.wear.compose.material.Text("사번 입력", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .width(140.dp)
                    .height(55.dp),//.padding(6.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White, // 텍스트 색상 설정
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    backgroundColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White), // 텍스트 스타일에 색상 추가
                singleLine = true
            )
            // 요소 간의 간격 추가
            Spacer(modifier = Modifier.height(5.dp))

            // 패스워드 입력 필드
            var password = remember { mutableStateOf("") }
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                visualTransformation = PasswordVisualTransformation(),
                label = { androidx.wear.compose.material.Text("비밀번호 입력", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .width(140.dp)
                    .height(55.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White, // 텍스트 색상 설정
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    backgroundColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White), // 텍스트 스타일에 색상 추가
                singleLine = true
            )

            // 요소 간의 간격 추가
            Spacer(modifier = Modifier.height(10.dp))

            // 로그인 버튼
            Button(
                onClick = {
//                    Log.d("login test", "Email : ${email.value}")
//                    Log.d("login test", "Password : ${password.value}")
                    loginRequest(email.value, password.value)
                },
                modifier = Modifier
                    .width(100.dp)
                    .height(40.dp) //.padding(8.dp)
            ) {
                Text("로그인", fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchMainActivityAfterLogin(
        user_name: String, user_index: Int, beacon_name: String,
        bluetooth_scan_interval: Long?, bluetooth_sampling_interval: Long?,
        sensor_interval: Long?, fall_detection_time: Long?, fall_detection_gravity: Float?,
        location_interval: Long?, fall_detection_landing_gravity: Float?, fall_detection_landing_time: Float?,
        fall_detection_min_lying_gravity: Float?, fall_detection_max_lying_gravity: Float?){
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("user_name", user_name)
        intent.putExtra("user_index", user_index)
        intent.putExtra("device_id", deviceId)
        intent.putExtra("beacon_name", beacon_name)

        intent.putExtra("bluetooth_scan_interval", bluetooth_scan_interval)
        intent.putExtra("bluetooth_sampling_interval", bluetooth_sampling_interval)
        intent.putExtra("sensor_interval", sensor_interval)
        intent.putExtra("fall_detection_time", fall_detection_time)
        intent.putExtra("fall_detection_gravity", fall_detection_gravity)
        intent.putExtra("location_interval", location_interval)
        intent.putExtra("fall_detection_landing_gravity", fall_detection_landing_gravity)
        intent.putExtra("fall_detection_landing_time", fall_detection_landing_time)
        intent.putExtra("fall_detection_min_lying_gravity", fall_detection_min_lying_gravity)
        intent.putExtra("fall_detection_max_lying_gravity", fall_detection_max_lying_gravity)
        startActivity(intent)
        finish()
    }

    fun loginRequest(id: String, password: String){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(
            GsonConverterFactory.create()).addCallAdapterFactory(CoroutineCallAdapterFactory()).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        val loginItem = LoginRequestData(id = id, password = password, device_id = deviceId,
            battery = battery)
        customScope.launch {
            try {
                val response = apiService.login(loginItem)
                val user_name: String? = response.body()?.name
                val user_index: Int? = response.body()?.id
                val beacon_name: String? = response.body()?.beacon_name
                val ship_id: Int? = response.body()?.ship_id
                Log.d(TAG,"user_name : $user_name | user_index: $user_index | beacon_name : $beacon_name")
                when (response.code()) {
                    200 -> {
                        if(user_name == null || user_index == null || beacon_name == null || ship_id == null){
                            showToast("로그인 오류가 발생하였습니다")
                        }else {
                            loadOTAVariable(ship_id = ship_id, user_name = user_name, user_index = user_index, beacon_name= beacon_name)
                        }
                    }

                    400 -> {
                        val jsonResponse = response.errorBody()?.string()
                        val jsonObject = jsonResponse?.let { JSONObject(it) }
                        val status_str: String? = jsonObject?.getString("detail")
                        when (status_str) {
                            "Unregistered Device" -> {
                                showToast("등록되지 않은 기기입니다")
                            }

                            "Registered on other ship" -> {
                                showToast("다른 배에 등록된 기기입니다")
                            }

                            else -> {
                                showToast("로그인에 실패하였습니다")
                            }
                        }
                    }

                    else -> {
                        showToast("로그인에 실패하였습니다")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                showToast("로그인에 실패하였습니다")
            }
        }
    }

    fun loadOTAVariable(ship_id: Int, user_name: String, user_index: Int, beacon_name: String){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(
                CoroutineCallAdapterFactory()
            ).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        customScope.launch {
            try {
                val response = apiService.getOTAVariables(ship_id = ship_id)
                when(response.code()){
                    200 -> {
                        val ota_variables = response.body()?.data
                        Log.d(TAG, "${ota_variables?.bluetooth_scan_interval}")
                        if(ota_variables != null){
                            val bluetooth_scan_interval: Long? = ota_variables.bluetooth_scan_interval
                            val bluetooth_sampling_interval: Long? = ota_variables.bluetooth_sampling_interval
                            val sensor_interval: Long? = ota_variables.sensor_interval
                            val fall_detection_time: Long? = ota_variables.fall_detection_time
                            val fall_detection_gravity: Float? = ota_variables.fall_detection_gravity
                            val location_interval: Long? = ota_variables.location_interval
                            val fall_detection_landing_gravity: Float? = ota_variables.fall_detection_landing_gravity
                            val fall_detection_landing_time: Float? = ota_variables.fall_detection_landing_time
                            val fall_detection_min_lying_gravity: Float? = ota_variables.fall_detection_min_lying_gravity
                            val fall_detection_max_lying_gravity: Float? = ota_variables.fall_detection_max_lying_gravity

                            launchMainActivityAfterLogin(
                                user_name = user_name,
                                user_index = user_index,
                                beacon_name = beacon_name,
                                bluetooth_scan_interval = bluetooth_scan_interval,
                                bluetooth_sampling_interval = bluetooth_sampling_interval,
                                sensor_interval = sensor_interval,
                                fall_detection_time = fall_detection_time,
                                fall_detection_gravity = fall_detection_gravity,
                                location_interval = location_interval,
                                fall_detection_landing_gravity = fall_detection_landing_gravity,
                                fall_detection_landing_time = fall_detection_landing_time,
                                fall_detection_min_lying_gravity = fall_detection_min_lying_gravity,
                                fall_detection_max_lying_gravity = fall_detection_max_lying_gravity
                            )
                        }
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
}


