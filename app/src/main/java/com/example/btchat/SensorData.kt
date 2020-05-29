import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.btchat.BluetoothConnectionService
import com.example.btchat.SamplingPeriod
import java.nio.charset.Charset

/**
 * The Class that will be used to obtain the sensor data
 */
class SensorData(context: Context) : SensorEventListener {

    var mSensorManager: SensorManager? = null
    var mAccelerometer: Sensor? = null
    var mGyroscope: Sensor? = null

    var x : Float = 0.0F
    var y : Float = 0.0F
    var z : Float = 0.0F

    var Wx : Float = 0.0F
    var Wy : Float = 0.0F
    var Wz : Float = 0.0F

    var dataFlow : Boolean = false
    var localBluetoothConnectionService : BluetoothConnectionService? = null

    init {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun initListeners(){
        mSensorManager?.registerListener(this, mAccelerometer, SamplingPeriod) //100Hz sampling rate
        mSensorManager?.registerListener(this, mGyroscope, SamplingPeriod)
    }

    fun destListeners(){
        mSensorManager?.unregisterListener(this)
    }

    fun startDataFlow(mBluetoothConnection: BluetoothConnectionService?){
        localBluetoothConnectionService = mBluetoothConnection
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                x = event.values[0]
                y = event.values[1]
                z = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE ->     {
                Wx = event.values[0]
                Wy = event.values[1]
                Wz = event.values[2]
            }
        }
        if (dataFlow)
        {
            val data = "$x \n" //	 $y 	 $z 	 $Wx 	 $Wy 	 $Wz
            val byte : ByteArray = data.toByteArray(Charset.defaultCharset())
            localBluetoothConnectionService?.write(byte)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}