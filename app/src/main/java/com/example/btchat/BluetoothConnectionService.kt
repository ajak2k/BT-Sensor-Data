@file:Suppress("DEPRECATION", "UNUSED_PARAMETER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "UNUSED_VARIABLE", "unused"
)

package com.example.btchat

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

private const val TAG = "BluetoothConnectionServ"
private const val appName = "MYAPP"
private val MY_UUID_INSECURE : UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")



class BluetoothConnectionService (context: Context){

    private var mBluetoothAdapter : BluetoothAdapter? = null
    private var mContext: Context? = null
    private var mmDevice: BluetoothDevice? = null
    private var deviceUUID: UUID? = null
    var mProgressDialog: ProgressDialog? = null

    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

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

    /**
     * The ConnectedThread is responsible for maintaining the BTConnection, Sending the data, and
     * receiving incoming data through input/output streams respectively.
     */
    private inner class ConnectedThread(socket: BluetoothSocket?): Thread(){

        private var mmSocket : BluetoothSocket? = null
        private var mmInStream: InputStream? =null
        private var mmOutStream: OutputStream? =null

        init{
            Log.d(TAG,"ConnectedThread: Starting")
            //At the point of execution of this code, the connection is definitely established
            //So we don't need the dialogue box anymore
            mProgressDialog?.dismiss()

            mmSocket=socket
            mmInStream = mmSocket?.inputStream
            mmOutStream = mmSocket?.outputStream
        }

        override fun run() {
            val buffer = ByteArray(1024)//buffer captures the data sent over
            var bytes : Int                 //It holds the number of bytes received

            //Keep Listening to the InputStream until an exception occurs
            while (true){
                try {
                    bytes = mmInStream!!.read(buffer)
                    val incomingMessage = String(buffer,0,bytes)
                    Log.d(TAG,"InputStream: $incomingMessage")
                } catch (e: IOException){
                    Log.e(TAG,"write: Error reading Input Stream. " + e.message)
                    break
                }
            }
        }

        //Call this from the MainActivity to send data to the remote device
        fun write(bytes: ByteArray){
            val text = String(bytes, Charset.defaultCharset())
            Log.d(TAG,"write: Writing to Output Stream: $text ")
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException){
                Log.e(TAG,"write: Error writing to Output Stream. " + e.message)
            }
        }

        //Call this from the MainActivity to shutdown the connection
        fun cancel(){
            mmSocket?.close()
        }
    }

    //Methods for the class BluetoothConnectionService

    //Private methods for internal usage
    /**
     * Starts the Connected Thread to manage the connection and the transmission
     */
    private fun connected(mmSocket: BluetoothSocket?, mmDevice: BluetoothDevice?) {
        Log.d(TAG, "connected: Starting.")
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(mmSocket)
        mConnectedThread!!.start()
    }

    //Public Method for external usage
    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
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

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * Parameters: 'out' - The bytes to write
     * See ConnectedThread.write for more details
     */
    fun write(out: ByteArray?) {

        var r: ConnectedThread
        // Synchronize a copy of the ConnectedThread
        Log.d(TAG, "write: Write Called.")
        mConnectedThread!!.write(out!!)
    }

}