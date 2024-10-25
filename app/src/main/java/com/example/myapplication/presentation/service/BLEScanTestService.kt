package com.example.myapplication.presentation.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.presentation.ApiService
import com.example.myapplication.presentation.const_val.BLUETOOTH_SAMPLING_INTERVAL
import com.example.myapplication.presentation.const_val.BLUETOOTH_SCAN_INTERVAL
import com.example.myapplication.presentation.data.BLELocationRequestData
import com.example.myapplication.presentation.data.BLEScanDevice
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Timer
import java.util.TimerTask


class BLEScanTestService : Service() {
    private val TAG: String = "BLEScanService"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val handler = Handler(Looper.myLooper()!!)
    private var TIMER_INTERVAL: Long = BLUETOOTH_SCAN_INTERVAL
    private var SAMPLE_DURATION: Long = BLUETOOTH_SAMPLING_INTERVAL
    private val NOTIFICATION_CHANNEL_ID = "BLEScanServiceChannel"
    private val NOTIFICATION_ID = 101
    private var timer: Timer? = null
    private var user_index: Int = -1
    private var beacon_name: String = ""
    private var device_post_list: MutableList<BLELocationRequestData> = mutableListOf()
    private lateinit var settings: ScanSettings
    var filters = mutableListOf<ScanFilter>()

    private val customScope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // 서비스를 포그라운드로 실행하기 위한 설정
        createNotificationChannel()
        val notification = Notification.Builder(this, SensorService.CHANNEL_ID)
            .setContentTitle("BLE 측정 중")
            .setContentText("BLE 데이터를 수집하고 있습니다.")
            .setSmallIcon(R.drawable.splash_icon) // 적절한 아이콘으로 교체해야 합니다.
            .build()
        startForeground(NOTIFICATION_ID, notification)

        // 심박수 센서 리스너 등록
        handler.postDelayed({
            Log.d(TAG, "Waiting for Bluetooth booted Complete")
            timer = Timer()
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    device_post_list.clear()
                    startScan(filters = filters, settings = settings)

                }
            }, 0, TIMER_INTERVAL)
        }, 10000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Intent에서 데이터 추출
        if(intent != null){
            user_index = intent.getIntExtra("user_index",-1)
            beacon_name = intent.getStringExtra("beacon_name") ?: ""
            TIMER_INTERVAL = intent.getLongExtra("bluetooth_scan_interval", TIMER_INTERVAL)
            SAMPLE_DURATION = intent.getLongExtra("bluetooth_sampling_interval", SAMPLE_DURATION)
        }
        Log.d(TAG, "onStartCommand - user_index : $user_index")
        Log.d(TAG, "onStartCommand - beacon_name : $beacon_name")
        Log.d(TAG, "onStartCommand - TIMER_INTERVAL : $TIMER_INTERVAL")
        Log.d(TAG, "onStartCommand - SAMPLE_DURATION : $SAMPLE_DURATION")

        var filter: ScanFilter = ScanFilter.Builder().setDeviceName(beacon_name).build()
        settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        filters.add(filter)

        // 서비스 작업을 계속 진행하도록 설정
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        // 서비스가 종료될 때 리스너 해제
//        timer?.cancel()
        try{
            bluetoothLeScanner.stopScan(scanCallback)
        }catch (e: SecurityException){
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun startScan( filters: List<ScanFilter>?, settings: ScanSettings? ) {
        try{
            bluetoothLeScanner.startScan(filters,settings, scanCallback)
            handler.postDelayed({
                try {
                    bluetoothLeScanner.stopScan(scanCallback)
                    // TODO
                    // Must be Activated
                    apiRequest()
                    // TODO
                    // Must be deleted
//                    Log.d(TAG, "Bluetooth Scan 개수 : ${device_post_list.size}")
//                    device_post_list.clear()
//                    startScan(filters = filters, settings = settings)
                }catch (e:SecurityException){
                    Log.e(TAG,e.stackTraceToString())
                }
            }, SAMPLE_DURATION)
        }catch (e: SecurityException){
            e.printStackTrace()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            val rssi = result?.rssi
            val mac_address = result?.device?.address
            if (rssi != null && mac_address != null) {
                val locationData = BLELocationRequestData.create(rssi = rssi, userID = user_index, beacon_uuid = mac_address)
                device_post_list.add(locationData)
            }
        }
    }

    fun apiRequest(){
        val retrofit: Retrofit = Retrofit.Builder().baseUrl("http://jdi-global.com:27777/").addConverterFactory(
            GsonConverterFactory.create()).addCallAdapterFactory(CoroutineCallAdapterFactory()).build()
        val apiService: ApiService = retrofit.create(ApiService::class.java)
        customScope.launch {
            try {
                val response = apiService.sendBLELocationData(device_post_list)
                when(response.code()){
                    200 -> {
                        device_post_list.clear()
//                        startScan(filters = filters, settings = settings)
                    }
                    400 -> {

                    }
                    else -> {

                    }
                }
            } catch(e: Exception){
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    private fun createNotificationChannel() {
        val channelName = "BLE Scan Service"
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}