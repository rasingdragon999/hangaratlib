package com.itikarus.hangaratlib

import java.util.*

object Devicetransfer{
    // Message types sent from the BluetoothChatService Handler
    val MESSAGE_STATE_CHANGE = 1
    val MESSAGE_READ = 2
    val MESSAGE_WRITE = 3
    val MESSAGE_DEVICE_NAME = 4
    val MESSAGE_TOAST = 5
    val MESSAGE_BLUETOOTH_ERROR = 6
    val MESSAGE_DISTOERROR = 7
    val MESSAGE_KEYBOARD = 8
    val MESSAGE_RESULT = 9
    val MESSAGE_UNABLE_TO_CONNECT = 10
    val MESSAGE_MODEL_NAME = 11
    val MESSAGE_CHARACTERISTIC_READ = 12

    // Key names received from the BluetoothChatService Handler
    val DEVICE_NAME = "device_name"
    val DEVICE_ADDRESS = "device_address"
    val DEVICE_ISBLE = "device_isble"
    val TOAST = "toast"


    val UUID_RAT_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")    //har
    val UUID_RAT_CHARACTERISTIC_VALUE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb") // har
    val UUID_RAT_DESCRIPTOR2 = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}