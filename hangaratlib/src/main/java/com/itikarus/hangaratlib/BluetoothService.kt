@file:Suppress("ConstantConditionIf")

package com.itikarus.hangaratlib

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

object BluetoothService {
    private val TAG = javaClass.simpleName

    private val D = true
    private var m_BluetoothAdapter: BluetoothAdapter? = null
    private var m_Handler: Handler? = null
    private var m_iState: Int = 0
    private var m_btReEnabled = false
    private var btGatt: BluetoothGatt? = null

    // Constants that indicate the current connection state
    val STATE_NONE = 0            // we're doing nothing
    val STATE_LISTEN = 1            // now listening for incoming connections
    val STATE_CONNECTING = 2        // now initiating an outgoing connection
    val STATE_CONNECTED = 3        // now connected to a remote device

    private var forceStopped: Boolean = false
    private var currentDevice: BluetoothDevice? = null
    private var bluetoothBondReceiver: BluetoothBondReceiver? = null
    private var pairCurrentDevice = false
    private var leScanCallback: BluetoothAdapter.LeScanCallback? = null
    private val readStack = Stack<BluetoothGattCharacteristic>()

    fun init(context: Context){
        if (D) Log.d(TAG, "BluetoothService")

        m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        m_iState = STATE_NONE

        forceStopped = false

        val filterScan = IntentFilter()
        filterScan.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        bluetoothBondReceiver = BluetoothBondReceiver()
        context.registerReceiver(bluetoothBondReceiver, filterScan)
    }

    fun setup(handler: Handler, context: Context) {
        if (m_Handler != null && leScanCallback != null) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val mBluetoothAdapter = bluetoothManager.adapter
            if (m_BluetoothAdapter != null)
                mBluetoothAdapter.stopLeScan(leScanCallback)
            m_Handler!!.removeCallbacksAndMessages(null)
        }
        m_Handler = handler
    }

    fun disableBluetooth() {
        BluetoothAdapter.getDefaultAdapter().disable()
    }

    fun resetService() {
        m_Handler = null
        btGatt = null
        currentDevice = null
        leScanCallback = null
    }

    fun setForceStopped(stopped: Boolean) {
        this.forceStopped = stopped
    }

    fun getCharacteristic(characteristic: UUID): BluetoothGattCharacteristic? {
        val gatt = btGatt
        //        JQuery.i("STATE: " + getState() + " GATT:" + gatt + " SERV: " + gatt.getService(Devicetransfer.DISTO_SERVICE));
        return if (gatt != null && getState() == STATE_CONNECTED) {
            return gatt.getService(Devicetransfer.UUID_RAT_SERVICE)?.getCharacteristic(characteristic)
        } else
            null
    }

    fun readCharacteristic(characteristic: UUID) {
        if (btGatt != null && getCharacteristic(characteristic) != null) {
            if (readStack.isEmpty()) {
                btGatt?.readCharacteristic(getCharacteristic(characteristic))
            }
            readStack.push(getCharacteristic(characteristic))
        }
    }

    private class BluetoothBondReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val deviceIn = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            if (deviceIn != null) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                if (currentDevice != null && deviceIn == currentDevice) {
                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                        if (bondState == BluetoothDevice.BOND_BONDED && btGatt != null) {
                            //now start reading
                            if (getState() == STATE_CONNECTED || getState() == STATE_CONNECTING/* && notificationStack == null*/) {
                                //JQuery.i("discovering services: " + btGatt.discoverServices())
                            }
                        }
                    }
                }
            }
        }
    }

    fun connectDevice(device: BluetoothDevice, context: Context) {

        this.forceStopped = false
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            m_Handler?.post { connectBle(device, false, context) }
        }
    }

    private fun connectBle(device: BluetoothDevice, stopOnFail: Boolean, context: Context) {

        //JQuery.w("BTGATT: " + (btGatt != null) + " STATE: " + getState() + " FS: " + forceStopped)

        if (forceStopped)
            return

        pairCurrentDevice = false

        m_Handler?.removeCallbacksAndMessages(null)
        this.currentDevice = device
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        m_BluetoothAdapter = bluetoothManager.adapter


        m_Handler?.post {
            btGatt = device.connectGatt(context, false, gattCallback)
        }


        if (stopOnFail) {
            m_Handler?.postDelayed({
                if (getState() != STATE_CONNECTED) {

                    setState(STATE_LISTEN)

                    // Send a failure message back to the Activity
                    val msg = m_Handler?.obtainMessage(Devicetransfer.MESSAGE_UNABLE_TO_CONNECT)
                    val bundle = Bundle()
                    bundle.putString(Devicetransfer.TOAST, context.getString(R.string.bluetooth_unable_to_conntect))
                    msg?.data = bundle

                    m_Handler?.sendMessage(msg)

                }
            }, 6000)
        } else {
            //everlonging reconnect
            m_Handler?.postDelayed({
                if (getState() != STATE_CONNECTED) {
                    //                        connectBle(device, false);
                    findDeviceAndConnect(device.address, context)
                }
            }, 6000)
        }
    }

    private fun findDeviceAndConnect(address: String?, context: Context) {

        if (address == null || address.isEmpty() || forceStopped)
            return
        leScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
            if (device.address == address && getState() != STATE_CONNECTED) {
                m_BluetoothAdapter?.stopLeScan(leScanCallback)
                connectBle(device, true, context)
            }
        }

        m_BluetoothAdapter?.startLeScan(leScanCallback)

        for (dev in m_BluetoothAdapter?.bondedDevices!!) {
            if (dev.address == address && getState() != STATE_CONNECTED) {
                m_BluetoothAdapter?.stopLeScan(leScanCallback)
                m_Handler?.removeCallbacksAndMessages(null)
                connectBle(dev, true, context)
                break
            }
        }


        Handler().postDelayed({
            m_BluetoothAdapter?.stopLeScan(leScanCallback)
            if (getState() != STATE_CONNECTED && !forceStopped && getState() != STATE_CONNECTING) {
                m_Handler?.postDelayed({ findDeviceAndConnect(address, context) }, 2000)

            }
        }, 15000)
    }

    /**
     * Set the current state of the connection
     *
     * @param state An integer defining the current connection state
     */
    @Synchronized
    private fun setState(state: Int) {
        if (D) Log.d(TAG, "setState() $m_iState -> $state")

        m_iState = state

        // Give the new state to the Handler so the UI Activity can update
        m_Handler?.obtainMessage(Devicetransfer.MESSAGE_STATE_CHANGE, state, -1)?.sendToTarget()
    }

    /**
     * Return the current connection state.
     */
    @Synchronized
    fun getState(): Int {
        return m_iState
    }

    var dataReceived : ((value : String) -> Unit)? = null

    private var gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            //JQuery.i("Connection State Change: $newState --- $status reEnable: $m_btReEnabled")
            //LogUtils.saveLog("Connection State Change: " + newState + " --- " + status + " reEnable: " + m_btReEnabled);

            when {
                newState > 110 -> {
                    setState(STATE_LISTEN)

                    // Send a failure message back to the Activity
                    val msg = m_Handler?.obtainMessage(Devicetransfer.MESSAGE_TOAST)
                    val bundle = Bundle()
                    bundle.putString(Devicetransfer.TOAST, "Device was in Listen state")
                    msg?.data = bundle

                    m_Handler?.sendMessage(msg)
                }
                newState == BluetoothGatt.STATE_CONNECTED -> {


                    m_btReEnabled = false

                    setState(STATE_CONNECTING)

                    m_Handler?.postDelayed({ btGatt?.discoverServices() }, 1000)


                }
                newState == BluetoothGatt.STATE_DISCONNECTED -> {
                    setState(STATE_NONE)
                    val msg = m_Handler?.obtainMessage(Devicetransfer.MESSAGE_TOAST)
                    val bundle = Bundle()
                    bundle.putString(Devicetransfer.TOAST, "Bluetooth Connection Lost")
                    msg?.data = bundle

                    m_Handler?.sendMessage(msg)

                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {


            try {

                val list = gatt.services
                var serviceFound = false
                for (service in list) {
                    //                    JQuery.w("ONSERVICESDISCOVERED 2");
                    if (service.uuid == Devicetransfer.UUID_RAT_SERVICE) {
                        serviceFound = true
                        val enabled = enableNotification(service.getCharacteristic(Devicetransfer.UUID_RAT_CHARACTERISTIC_VALUE))

                        if (!enabled!!) {
                            gatt.disconnect()
                            gatt.close()
                            return
                        }

                    }
                }

                if (!serviceFound) {
                    gatt.discoverServices()
                }

                /**
                 *
                 * ERKENNTNISSE:
                 * - wenn beide Geräte gekoppelt sind in Android - Wechsel einwandfrei
                 * - wenn ein Gerät entkoppelt wird: Verbindung innerhalb der App direkt nicht möglich
                 * - erst wieder, wenn auch Android BT Manager das Gerät finden kann (Advertising?)
                 */

            } catch (e: Exception) {
                //JQuery.e(e)
            }

        }

        private fun enableNotification(characteristic: BluetoothGattCharacteristic?): Boolean? {
            if (btGatt == null || characteristic == null)
                return false
            val enableNotification = btGatt?.setCharacteristicNotification(characteristic, true)
            if (!enableNotification!!)
                return false

            if (characteristic.uuid == Devicetransfer.UUID_RAT_CHARACTERISTIC_VALUE) {
                setState(STATE_CONNECTED)
                val msg = m_Handler?.obtainMessage(Devicetransfer.MESSAGE_DEVICE_NAME)
                val bundle = Bundle()
                bundle.putBoolean(Devicetransfer.DEVICE_ISBLE, true)
                bundle.putString(Devicetransfer.DEVICE_NAME, btGatt?.device?.name)
                bundle.putString(Devicetransfer.DEVICE_ADDRESS, btGatt?.device?.address)
                msg?.data = bundle
                m_Handler?.sendMessage(msg)

                pairCurrentDevice = currentDevice?.bondState == BluetoothDevice.BOND_BONDED
            }

            /**
             * Should be changed
             * */

            var clientConfig: BluetoothGattDescriptor? = null

            characteristic.descriptors.forEach {
                if(it.uuid == Devicetransfer.UUID_RAT_DESCRIPTOR2){
                    clientConfig = it
                    return@forEach
                }

            }


            if (clientConfig == null) {
                return false
            }

            if (true) {
                Log.i(TAG, "enable notification")
                clientConfig!!.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                Log.i(TAG, "disable notification")
                clientConfig!!.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            /**
             * Should be changed
             * */
            return btGatt?.writeDescriptor(clientConfig)

        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            //JQuery.i("ONDESCRIPTORWRITE: " + status + " EMPTY: " + notificationStack.empty())
            //LogUtils.saveLog("ONDESCRIPTORWRITE: " + status + " EMPTY: " + notificationStack.empty());
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            //JQuery.w("Characteristic READ: " + characteristic.getUuid());

            if (status != BluetoothGatt.GATT_SUCCESS){
                return
            }

            if (characteristic.uuid == Devicetransfer.UUID_RAT_CHARACTERISTIC_VALUE) {
                /**
                 * getting value part
                 * */

                val rawValue = characteristic.value

                val value = String(rawValue, Charsets.UTF_8).split(" ")[0]
                dataReceived?.invoke(value)

            }
        }


        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {

            onCharacteristicRead(gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        }

    }
}