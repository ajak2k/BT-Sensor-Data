@file:Suppress("LocalVariableName", "UNUSED_PARAMETER", "unused",
    "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "PrivatePropertyName"
)

package com.example.btchat

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList


private const val TAG = "MyActivity"
private val MY_UUID_INSECURE_1 : UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
private val MY_UUID_INSECURE_2 : UUID = UUID.fromString("a2eb3fca-4fe1-489a-935a-43599dd0eb59")
private val MY_UUID_INSECURE_3 : UUID = UUID.fromString("03bf81b7-a613-459f-b76a-310265378bcf")

const val  SamplingPeriod = 10000 //in Micro Seconds


class MainActivity : AppCompatActivity() , AdapterView.OnItemClickListener, SensorEventListener {

    //Variables required to establish Bluetooth Communication
    private var mBluetoothAdapter   : BluetoothAdapter? = null

    private var mBluetoothConnectionDevice1: BluetoothConnectionService? =null
    private var mBluetoothConnectionDevice2: BluetoothConnectionService? =null
    private var mBluetoothConnectionDevice3: BluetoothConnectionService? =null

    private var mBTDevice: BluetoothDevice? = null
    private var mBTDevices: ArrayList<BluetoothDevice?> = ArrayList()

    private var mDeviceListAdapter: DeviceListAdapter? = null
    private var lvNewDevices: ListView? = null
    //This is for displaying the incoming messages on the screen
    private var incomingMessagesDevice1: TextView? =null
    private var incomingMessagesDevice2: TextView? =null
    private var incomingMessagesDevice3: TextView? =null

    private var deviceID: Int = -2

    // Variables required for the Sensor data acquisition
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var mGyroscope: Sensor? = null

    private var x : Float = 0.0F
    private var y : Float = 0.0F
    private var z : Float = 0.0F

    private var Wx : Float = 0.0F
    private var Wy : Float = 0.0F
    private var Wz : Float = 0.0F

    private var dataFlow : Boolean = false

    private val CSV_HEADER = "X,Y,Z,Wx,Wy,Wz,\n"
    private var out: FileOutputStream? = null
    private val sensData1 = Environment.getExternalStorageDirectory().absolutePath +"/sensDataDevice1.csv"
    private val sensData2 = Environment.getExternalStorageDirectory().absolutePath +"/sensDataDevice2.csv"
  //  private val sensData3 = Environment.getExternalStorageDirectory().absolutePath +"/sensDataDevice3.csv"

    var k = 0

    /**
     * The BroadCastReceivers are used to listen to the various state changes that happen
     */
    //Listens for the Turning ON/OFF of the Bluetooth Adapter
    private val mBroadcastReceiver1: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED)
            {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR))
                {
                    BluetoothAdapter.STATE_OFF          -> Log.d(TAG, "onReceive: STATE OFF")
                    BluetoothAdapter.STATE_TURNING_OFF  -> Log.d(TAG,"mBroadcastReceiver1: STATE TURNING OFF")
                    BluetoothAdapter.STATE_ON           -> Log.d(TAG,"mBroadcastReceiver1: STATE ON")
                    BluetoothAdapter.STATE_TURNING_ON   -> Log.d(TAG,"mBroadcastReceiver1: STATE TURNING ON")
                }
            }
        }
    }
    //Listens for the Discoverability Status of the Bluetooth Adapter
    private val mBroadcastReceiver2: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent)
        {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR))
                {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> Log.d(TAG,"mBroadcastReceiver2: Discoverability Enabled.")
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> Log.d(TAG,"mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.")
                    BluetoothAdapter.SCAN_MODE_NONE -> Log.d(TAG,"mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.")
                    BluetoothAdapter.STATE_CONNECTING -> Log.d(TAG,"mBroadcastReceiver2: Connecting....")
                    BluetoothAdapter.STATE_CONNECTED -> Log.d(TAG,"mBroadcastReceiver2: Connected.")
                }
            }
        }
    }
    //Listens for the available Devices name and address
    private val mBroadcastReceiver3: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive: ACTION FOUND.")
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device =  intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                mBTDevices.add(device)
                Log.d(TAG,"onReceive: " + device?.name + ": " + device?.address)
                mDeviceListAdapter = DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices)
                lvNewDevices!!.adapter = mDeviceListAdapter
            }
        }
    }
    //Listens for the Successful Connection between the two devices
    private val mBroadcastReceiver4: BroadcastReceiver = object  : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent!!.action

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                val mDevice: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                //3 Cases:

                //Case1: bonded already
                if (mDevice?.bondState == BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.")
                    mBTDevice = mDevice
                }
                //Case2: creating a bond
                if(mDevice?.bondState == BluetoothDevice.BOND_BONDING)
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.")
                //Case3: breaking a bond
                if(mDevice?.bondState == BluetoothDevice.BOND_NONE)
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.")
            }
        }
    }

    //Listens for the incomingMessage and prints it in the textView
    private val mReceiverDevice1: BroadcastReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent) {
            val text: String = intent.getStringExtra("Device1Data")
            incomingMessagesDevice1?.text = text
            try {
                out = openFileOutput(sensData1, Context.MODE_APPEND)
                out?.write(text.toByteArray())
                out?.write("\n".toByteArray())
                out?.close()
                Log.d(TAG,"Device1 data written to sensData.csv successfully")
            }catch (e: Exception) {
                Log.d(TAG, "error in writing Accel to sensData.csv")
            }
        }
    }

    private val mReceiverDevice2: BroadcastReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent) {
            val text: String = intent.getStringExtra("Device2Data")
            incomingMessagesDevice2?.text = text
            try {
                out = openFileOutput(sensData2, Context.MODE_APPEND)
                out?.write(text.toByteArray())
                out?.write("\n".toByteArray())
                out?.close()
                Log.d(TAG,"Device 2 data written to sensData.csv successfully")
            }catch (e: Exception) {
                Log.d(TAG, "error in writing Gyro to sensData.csv")
            }
        }
    }
/*
    private val mReceiverDevice3: BroadcastReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent) {
            val text: String = intent.getStringExtra("Device3Data")
            incomingMessagesDevice3?.text = text
            try {
                out = openFileOutput(sensData2, Context.MODE_APPEND)
                out?.write(text.toByteArray())
                out?.write("\n".toByteArray())
                out?.close()
                Log.d(TAG,"Device 2 data written to sensData.csv successfully")
            }catch (e: Exception) {
                Log.d(TAG, "error in writing Gyro to sensData.csv")
            }
        }
    }
*/
    /**
     * The OnCreate and OnDestroy methods are the methods that will be executed on the creation of
     * the activity and the destruction of the activity respectively
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_main)

        val btnONOFF : Button = this.findViewById(R.id.btnONOFF)
        val btnStartConnection: Button = this.findViewById(R.id.btnStartConnection)

        val btnStartSensors: Button = this.findViewById(R.id.btnStartSensors)
        val btnStopSensors: Button = this.findViewById(R.id.btnStopSensors)


        lvNewDevices = findViewById(R.id.lvNewDevices)
        mBTDevices = ArrayList()

        incomingMessagesDevice1 = findViewById(R.id.incomingMessagesDevice1)
        incomingMessagesDevice2 = findViewById(R.id.incomingMessagesDevice2)
    //    incomingMessagesDevice3 = findViewById(R.id.incomingMessagesDevice3)

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiverDevice1, IntentFilter("incomingMessageDevice1"))
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiverDevice2, IntentFilter("incomingMessageDevice2"))
       // LocalBroadcastManager.getInstance(this).registerReceiver(mReceiverDevice3, IntentFilter("incomingMessageDevice3"))

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4,filter)

        this.mBluetoothAdapter = getDefaultAdapter()
        lvNewDevices!!.onItemClickListener = this@MainActivity

        mSensorManager = this@MainActivity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)



        try {
            out = openFileOutput(sensData1, Context.MODE_APPEND)
            out?.write(CSV_HEADER.toByteArray())
            out?.close()
            Log.d(TAG,"OnCreate: sensDataDevice1.csv created successfully")
            /*
            out = openFileOutput(sensData2, Context.MODE_APPEND)
            out?.write(CSV_HEADER.toByteArray())
            out?.close()
            Log.d(TAG,"OnCreate: sensDataDevice2.csv created successfully")

            out = openFileOutput(sensData3, Context.MODE_APPEND)
            out?.write(CSV_HEADER.toByteArray())
            out?.close()
            Log.d(TAG,"OnCreate: sensDataDevice3.csv created successfully")
            */
        }catch (e: Exception) {
            Log.d(TAG, "OnCreate: error in creating sensData.csv")
        }


        btnONOFF.setOnClickListener(){
            this.enableDisableBT()
        }

        btnStartConnection.setOnClickListener(){
                startConnection()
        }

        btnStartSensors.setOnClickListener(){
            initListeners()
            Log.d(TAG, "StartSensor: Initialized Listeners")
            dataFlow = true
            Log.d(TAG, "StartSensor: Data Flow Initialized")
        }

        btnStopSensors.setOnClickListener(){
            destListeners()
            Log.d(TAG, "StartSensor: Listeners Destroyed")
            dataFlow = false
            Log.d(TAG, "StartSensor: Data Flow Destroyed")

        }

    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: called.")
        super.onDestroy()
        this.unregisterReceiver(this.mBroadcastReceiver1)
        this.unregisterReceiver(this.mBroadcastReceiver2)
        this.unregisterReceiver(this.mBroadcastReceiver3)
        this.unregisterReceiver(this.mBroadcastReceiver4)
        this.unregisterReceiver(this.mReceiverDevice1)
        this.unregisterReceiver(this.mReceiverDevice2)
     //   this.unregisterReceiver(this.mReceiverDevice3)
        destListeners()
    }

    override fun onStop() {
        super.onStop()
        // unregister sensor listeners to prevent the activity from draining the device's battery.
        destListeners()
    }

    override fun onPause() {
        super.onPause()
        // unregister sensor listeners to prevent the activity from draining the device's battery.
        destListeners()
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "onSensorChanged: Started")
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                x = event.values[0]
                y = event.values[1]
                z = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                Wx = event.values[0]
                Wy = event.values[1]
                Wz = event.values[2]
            }
        }
        if (dataFlow)
        {
            val data = "$x,$y,$z,$Wx,$Wy,$Wz,\n"
            Log.d(TAG, "onSensorChanged: Data: $data")
            val byte : ByteArray = data.toByteArray(Charset.defaultCharset())
            if (deviceID == 1)
                mBluetoothConnectionDevice1?.write(byte)
           // if (deviceID == 2)
           //     mBluetoothConnectionDevice2?.write(byte)
         //   if (deviceID == 3)
          //      mBluetoothConnectionDevice3?.write(byte)

            Log.d(TAG, "onSensorChanged: Data Transmitted")

            try {
                out = openFileOutput(sensData1, Context.MODE_APPEND)
                out?.write(byte)
                out?.close()
                Log.d(TAG,"OnSensorChanged: Data written to file")
            }catch (e: Exception) {
                Log.d(TAG, "OnSensorChanged: error in opening sensData.csv")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuracy Changed")
    }

    fun onRadioButtonClicked(view: View) {
        if (view is RadioButton) {
            // Is the button now checked?
            val checked = view.isChecked

            // Check which radio button was clicked
            when (view.getId()) {
                R.id.radio_receiver ->      if (checked) {
                    deviceID = 0        //Receiver
                }
                R.id.radio_transmitter1 ->  if (checked) {
                    deviceID = 1        //Tx1
                }
                R.id.radio_transmitter2 ->  if (checked) {
                    deviceID = 2        //Tx2
                }
                R.id.radio_transmitter3 ->  if (checked) {
                    deviceID = 3        //Tx3
                }
            }
        }
    }

    /**
     * The methods used when the respective buttons are pressed. The functions fare assigned to the
     * respective buttons in the XML files
     */
    //Used to Turn On/OFF the Bluetooth
    private fun enableDisableBT() {
        if(this.mBluetoothAdapter == null)
            Log.d(TAG, "enableDisableBT : Does not have Bluetooth capabililties. ")
        if (!this.mBluetoothAdapter!!.isEnabled) {

            Log.d(TAG, "enableDisableBT: enabling BT.")
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            this.startActivity(enableBTIntent)

            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            this.registerReceiver(this.mBroadcastReceiver1, BTIntent)
        }
        if(this.mBluetoothAdapter!!.isEnabled)
        {
            Log.d(TAG, "enableDisableBT: disabling BT.")
            this.mBluetoothAdapter!!.disable()

            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            this.registerReceiver(this.mBroadcastReceiver1, BTIntent)
        }
    }
    //Used to make the device discoverable to other devices for 300 seconds
    fun btnEnableDisableDiscoverable(view: View) {
        Log.d(TAG,"btnEnableDisableDiscoverable: Making device discoverable for 300 seconds.")
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(mBroadcastReceiver2, intentFilter)
    }
    //Used to Scan and Discover other discoverable devices
    fun btnDiscover(view: View) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.")
        if (mBluetoothAdapter!!.isDiscovering)
        {
            mBluetoothAdapter!!.cancelDiscovery()
            Log.d(TAG, "btnDiscover: Canceling discovery.")
            //check BT permissions in manifest
            if (Build.VERSION.SDK_INT >= M)
                checkBTPermissions()

            mBluetoothAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
        if (!mBluetoothAdapter!!.isDiscovering)
        {
            //check BT permissions in manifest
            if (Build.VERSION.SDK_INT >= M)
                checkBTPermissions()

            mBluetoothAdapter!!.startDiscovery()

            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
    }
    //Remember, the connection will fail and app will crash if you haven't paired first
    private fun startConnection(){
        startBTConnection(this.mBTDevice, MY_UUID_INSECURE_1, MY_UUID_INSECURE_2, MY_UUID_INSECURE_3)
    }
    //Starting chat service method and initializing the Listeners for Sensors
    private fun startBTConnection(device: BluetoothDevice?, uuid1: UUID?, uuid2: UUID?, uuid3: UUID?){
        Log.d(TAG,"startBTConnection: Initializing RFCOM Bluetooth Connection.")
        mBluetoothConnectionDevice1?.startClient(device, uuid1)
       // mBluetoothConnectionDevice2?.startClient(device, uuid2)
       // mBluetoothConnectionDevice3?.startClient(device, uuid3)
    }

    //Used to initiate a bond with the device that has been selected in the DeviceListAdapter
    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter!!.cancelDiscovery()


        Log.d(TAG, "onItemClick: You Clicked on a device.")
        val deviceName = mBTDevices[i]!!.name
        val deviceAddress = mBTDevices[i]!!.address

        Log.d(TAG, "onItemClick: deviceName = $deviceName")
        Log.d(TAG, "onItemClick: deviceAddress = $deviceAddress")

        //create the bond i.e. pair the devices.
        Log.d(TAG, "Trying to pair with $deviceName")
        mBTDevice = mBTDevices[i]
        mBTDevice?.createBond()
        Log.d(TAG, "Bond Created")
        when (deviceID) {
            0 -> {
                //K is used to count the number of times onItemClick is run on a receiver to
                // ensure the correct object is created with the correct uuid for the selected device
                if (k == 0)
                    mBluetoothConnectionDevice1 =
                        BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_1)
                //if (k == 1)
                //    mBluetoothConnectionDevice2 =
                //        BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_2)
                //  if(k == 2)
                //    mBluetoothConnectionDevice3 =
                //        BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_3)
                k += 1
            }
            1 -> mBluetoothConnectionDevice1 =
                BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_1)
            2 -> mBluetoothConnectionDevice2 =
                BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_2)
            3 -> mBluetoothConnectionDevice3 =
                BluetoothConnectionService(this@MainActivity, deviceID, MY_UUID_INSECURE_3)
        }
    }

    //Used to check if all the Permissions needed for the application to run smoothly is available
    @RequiresApi(M)
    private fun checkBTPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
        {
            var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
            permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
            if (permissionCheck != 0)
                requestPermissions(arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001) //Any number
        } else {
            Log.d(TAG,"checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.")
        }
    }

    /**
     * Sensor Methods
     */
     private fun initListeners(){
        //SamplingPeriod defines the rate at which the sensor is polled
        mSensorManager?.registerListener(this, mAccelerometer, SamplingPeriod)
        mSensorManager?.registerListener(this, mGyroscope, SamplingPeriod)
    }

     private fun destListeners(){
        mSensorManager?.unregisterListener(this)
    }

}