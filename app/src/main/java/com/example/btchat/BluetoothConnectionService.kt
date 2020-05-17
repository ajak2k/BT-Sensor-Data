package com.example.btchat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.util.*

private const val TAG = "BluetoothConnectionServ"
private const val appName = "MYAPP"
private val MY_UUID_INSECURE : UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

class BluetoothConnectionService (context: Context){

    private var mBluetoothAdapter : BluetoothAdapter? = null
    var mContext: Context? = null
    //Constructor
    init {
        mContext = context
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        start()
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled). This runs in a separate thread to prevent performance issues
     */
    private inner class AcceptThread : Thread(){
        var mmServerSocket: BluetoothServerSocket? = null

        init {
            var tmp : BluetoothServerSocket? = null
            //create a new listening server socket for other devices to connect to
            tmp = mBluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE)
            Log.d(TAG, "AcceptThread: Setting up the Server using $MY_UUID_INSECURE")
            mmServerSocket = tmp
        }
        public override fun run() {
            Log.d(TAG,"run: AcceptThread Running.")
            var socket: BluetoothSocket? = null
            // This .accept() a blocking call and will only return on a
            // successful connection or an exception
            Log.d(TAG, "run: RFCOM server socket start.....")
            socket = mmServerSocket?.accept()
            Log.d(TAG,"RFCOM Server socket accepted connection")
        }
        public fun cancel(){
            Log.d(TAG,"cancel: Cancelling AcceptThread")
            mmServerSocket?.close()
        }
    }

    private fun start() {
        TODO("Not yet implemented")
    }
}