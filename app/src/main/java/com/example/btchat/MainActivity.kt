@file:Suppress("LocalVariableName", "UNUSED_PARAMETER", "unused",
    "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS"
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
import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.user.bluetooth_discoverdevices.DeviceListAdapter
import java.lang.StringBuilder
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList


private const val TAG = "MyActivity"
private val MY_UUID_INSECURE : UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")


class MainActivity : AppCompatActivity(), AdapterView.OnItemClickListener {

    private var mBluetoothAdapter   : BluetoothAdapter? = null
    private var mBluetoothConnection: BluetoothConnectionService? =null
    var mBTDevice: BluetoothDevice? = null
    var mBTDevices: ArrayList<BluetoothDevice?> = ArrayList()

    var mDeviceListAdapter: DeviceListAdapter? = null
    var lvNewDevices: ListView? = null

    var incomingMessages: TextView? =null   //This is for displaying the incoming messages on the screen
    var messages: StringBuilder? = null //This is for appending the incoming messages and posting them on the text view

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
    private val mReceiver: BroadcastReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent) {
            val text: String = intent.getStringExtra("theMessage")

            messages?.append("$text \n")
            incomingMessages?.text = messages
        }
    }
    /**
     * The OnCreate and OnDestroy methods are the methods that will be executed on the creation of
     * the activity and the destruction of the activity respectively
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_main)

        val btnONOFF = this.findViewById<Button>(R.id.btnONOFF)
        val btnStartConnection: Button = this.findViewById<Button>(R.id.btnStartConnection)
        val btnSend: Button = this.findViewById<Button>(R.id.btnSend)

        val etSend: EditText = this.findViewById(R.id.editText)

        lvNewDevices = findViewById(R.id.lvNewDevices)
        mBTDevices = ArrayList()

        incomingMessages = findViewById(R.id.incomingMessages)
        messages = StringBuilder()

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, IntentFilter("incomingMessage"))

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4,filter)

        this.mBluetoothAdapter = getDefaultAdapter()
        lvNewDevices!!.onItemClickListener = this@MainActivity

        btnONOFF.setOnClickListener()
        {
            this.enableDisableBT()
        }

        btnStartConnection.setOnClickListener()
        {
                startConnection()
        }

        btnSend.setOnClickListener()
        {
                val bytes: ByteArray = etSend.text.toString().toByteArray(Charset.defaultCharset())
                mBluetoothConnection?.write(bytes)
                etSend.setText("")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: called.")
        super.onDestroy()
        this.unregisterReceiver(this.mBroadcastReceiver1)
        this.unregisterReceiver(this.mBroadcastReceiver2)
        this.unregisterReceiver(this.mBroadcastReceiver3)
        this.unregisterReceiver(this.mBroadcastReceiver4)
        this.unregisterReceiver(this.mReceiver)
    }

    /**
     * The methods used when the respective buttons are pressed. The functions fare assigned to the
     * respective buttons in the XML files
     */
    //Used to Turn On/OFF the Bluetooth
    private fun enableDisableBT() {
        if(this.mBluetoothAdapter == null)
            Log.d(TAG, "enableDisableBT : Does not have Bluetooth capabililties. ")
        else if (!this.mBluetoothAdapter!!.isEnabled) {

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

    //Remember the connection will fail and app will crash if you haven't paired first
    fun startConnection()
    {
        startBTConnection(this.mBTDevice, MY_UUID_INSECURE)
    }
    //Starting chat service method
    private fun startBTConnection(device: BluetoothDevice?, uuid: UUID?) {
        Log.d(TAG,"startBTConnection: Initializing RFCOM Bluetooth Connection.")
        mBluetoothConnection?.startClient(device, uuid)
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

        //create the bond.
        Log.d(TAG, "Trying to pair with $deviceName")
        mBTDevice = mBTDevices[i]
        mBTDevice?.createBond()
        Log.d(TAG, "Bond Created")
        mBluetoothConnection = BluetoothConnectionService(this@MainActivity)
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
}