@file:Suppress("DEPRECATION", "UNUSED_PARAMETER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")

package com.example.btchat

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    var mProgressDialog: ProgressDialog? = null

    //Constructor for the BluetoothConnectionService class
    init {
        mContext = context
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        start()
    }

    //Inner Classes for the
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled). This runs in a separate thread to prevent performance issues
     */
    private inner class AcceptThread : Thread(){
        var mmServerSocket: BluetoothServerSocket? = null

        init {
            //create a new listening server socket for other devices to connect to
            val tmp = mBluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE)
            Log.d(TAG, "AcceptThread: Setting up the Server using $MY_UUID_INSECURE")
            mmServerSocket = tmp
        }
        override fun run() {
            Log.d(TAG,"run: AcceptThread Running.")
            // This .accept() a blocking call and will only return on a
            // successful connection or an exception
            Log.d(TAG, "run: RFCOM server socket start.....")
            var socket = mmServerSocket?.accept()
            Log.d(TAG,"RFCOM Server socket accepted connection")
        }
        fun cancel(){
            Log.d(TAG,"cancel: Cancelling AcceptThread")
            mmServerSocket?.close()
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(device: BluetoothDevice?, uuid: UUID?) : Thread(){
        private var mmSocket : BluetoothSocket? = null

        init {
                Log.d(TAG,"ConnectThread: Started")
                mmDevice= device
                deviceUUID = uuid
        }

        override fun run(){
            Log.i(TAG,"RUN ConnectThread")
            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            Log.d(TAG,"ConnectThread: Trying to create InsecureRfcommSocket using UUID: $deviceUUID")
            val tmp = mmDevice?.createInsecureRfcommSocketToServiceRecord(deviceUUID)
            //Have received the socket info from the other device
            mmSocket = tmp
            //Cancel Discovery mode as we don't need it anymore
            mBluetoothAdapter?.cancelDiscovery()
            //Now try to connect to the received socket
            //This is a blocking call; It'll return only on a successful connection or exception
            mmSocket?.connect()
            Log.d(TAG,"ConnectThread: Connected")

            connected(mmSocket,mmDevice)
        }

        fun cancel(){
            Log.d(TAG,"cancel: Closing Client Socket.")
            mmSocket?.close()
        }
    }


    //Methods for the class BluetoothConnectionService

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    private fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread?.cancel(); mConnectThread = null}
        if (mInsecureAcceptThread == null) {mInsecureAcceptThread = AcceptThread(); mInsecureAcceptThread!!.start()}
    }
    /**
     * AcceptThread starts and sits waiting for a connection.
     * Then ConnectThread starts and attempts to make a connection with the other devices AcceptThread.
     */
    fun startClient(device: BluetoothDevice?, uuid: UUID?) {
        Log.d(TAG, "startClient: Started.")

        //Initiate progress dialog
        mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth", "Please Wait...", true)
        //Start the ConnectThread
        mConnectThread = ConnectThread(device,uuid)
        mConnectThread?.start()
    }

    private fun connected(mmSocket: BluetoothSocket?,mmDevice: BluetoothDevice?) {
        //TODO("Not yet implemented")
    }
}