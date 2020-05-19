@file:Suppress("JoinDeclarationAndAssignment")

package com.example.user.bluetooth_discoverdevices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.btchat.R

@Suppress("NAME_SHADOWING")
class DeviceListAdapter(context: Context, tvResourceId: Int, private val mDevices: List<BluetoothDevice?>) : ArrayAdapter<BluetoothDevice?>(context, tvResourceId, mDevices) {
    private val mLayoutInflater: LayoutInflater
    private val mViewResourceId: Int

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val convertView = mLayoutInflater.inflate(mViewResourceId, null)
        val device : BluetoothDevice? = mDevices[position]

        if (device != null) {
            val deviceName : TextView?= convertView.findViewById<View>(R.id.tvDeviceName) as TextView
            val deviceAdress : TextView?= convertView.findViewById<View>(R.id.tvDeviceAddress) as TextView

            if (deviceName != null)
                deviceName.text = device.name
            if (deviceAdress != null)
                deviceAdress.text = device.address

        }
        return convertView
    }

    init {
        mLayoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mViewResourceId = tvResourceId
    }
}