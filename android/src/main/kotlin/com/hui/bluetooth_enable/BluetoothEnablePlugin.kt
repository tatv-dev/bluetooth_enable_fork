package com.hui.bluetooth_enable

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener

/** BluetoothEnablePlugin */
class BluetoothEnablePlugin : FlutterPlugin, ActivityAware, MethodCallHandler,
    ActivityResultListener, RequestPermissionsResultListener {

    companion object {
        private const val TAG = "BluetoothEnablePlugin"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val REQUEST_PERMISSIONS = 2

        // ✅ THÊM: Modern Bluetooth permissions cho Android 12+ (SDK 31+)
        private val BLUETOOTH_PERMISSIONS_31_AND_ABOVE = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )

        // ✅ Legacy permissions for Android 11 and below
        private val BLUETOOTH_PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private var activity: Activity? = null
    private var channel: MethodChannel? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pendingResult: Result? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "bluetooth_enable")
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        bluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        binding.addActivityResultListener(this)
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        releaseResources()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        releaseResources()
    }

    private fun releaseResources() {
        activity = null
        bluetoothManager = null
        bluetoothAdapter = null
        pendingResult = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val currentActivity = activity
        if (currentActivity == null) {
            result.error("no_activity", "Plugin not attached to an activity", null)
            return
        }

        when (call.method) {
            "isAvailable" -> {
                result.success(bluetoothAdapter != null)
            }
            "isEnabled" -> {
                if (bluetoothAdapter == null) {
                    result.error("bluetooth_unavailable", "Device does not have Bluetooth", null)
                    return
                }
                // ✅ THÊM: Check permissions before checking if enabled
                if (!hasBluetoothPermissions(currentActivity)) {
                    result.error("no_permissions", "Bluetooth permissions not granted", null)
                    return
                }
                try {
                    result.success(bluetoothAdapter?.isEnabled == true)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception checking Bluetooth state", e)
                    result.error("security_exception", "Missing Bluetooth permissions", null)
                }
            }
            "enableBluetooth" -> {
                enableBluetooth(result)
            }
            "customEnable" -> {
                customEnableBluetooth(result)
            }
            "requestPermissions" -> {
                requestBluetoothPermissions(result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    // ✅ THÊM: Check if has required Bluetooth permissions
    private fun hasBluetoothPermissions(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_PERMISSIONS_31_AND_ABOVE
        } else {
            BLUETOOTH_PERMISSIONS_LEGACY
        }

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ✅ ENHANCE: Modern permission request
    private fun requestBluetoothPermissions(result: Result) {
        val currentActivity = activity ?: run {
            result.error("no_activity", "No activity available", null)
            return
        }

        if (hasBluetoothPermissions(currentActivity)) {
            result.success(true)
            return
        }

        pendingResult = result
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_PERMISSIONS_31_AND_ABOVE
        } else {
            BLUETOOTH_PERMISSIONS_LEGACY
        }

        ActivityCompat.requestPermissions(
            currentActivity,
            permissions,
            REQUEST_PERMISSIONS
        )
    }

    private fun enableBluetooth(result: Result) {
        val currentActivity = activity ?: run {
            result.error("no_activity", "No activity available", null)
            return
        }

        if (bluetoothAdapter == null) {
            result.error("bluetooth_unavailable", "Device does not have Bluetooth", null)
            return
        }

        // ✅ ENHANCE: Check permissions first
        if (!hasBluetoothPermissions(currentActivity)) {
            pendingResult = result
            requestBluetoothPermissions(object : Result {
                override fun success(result: Any?) {
                    if (result == true) {
                        enableBluetoothAfterPermissions()
                    } else {
                        pendingResult?.error("no_permissions", "Bluetooth permissions denied", null)
                        pendingResult = null
                    }
                }

                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    pendingResult?.error(errorCode, errorMessage, errorDetails)
                    pendingResult = null
                }

                override fun notImplemented() {
                    pendingResult?.notImplemented()
                    pendingResult = null
                }
            })
            return
        }

        enableBluetoothAfterPermissions()
        pendingResult = result
    }

    // ✅ ENHANCE: Enable Bluetooth after permissions granted
    private fun enableBluetoothAfterPermissions() {
        val currentActivity = activity ?: return

        try {
            if (bluetoothAdapter?.isEnabled == true) {
                pendingResult?.success(true)
                pendingResult = null
                return
            }

            // ✅ FIX: For Android 13+ (SDK 33+), need different approach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use system dialog for Android 13+
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            } else {
                // Legacy approach for older Android versions
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                currentActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception enabling Bluetooth", e)
            pendingResult?.error("security_exception", "Missing Bluetooth permissions", null)
            pendingResult = null
        }
    }

    // ✅ WARNING: customEnable method is deprecated and may not work on Android 10+
    private fun customEnableBluetooth(result: Result) {
        val currentActivity = activity ?: run {
            result.error("no_activity", "No activity available", null)
            return
        }

        if (!hasBluetoothPermissions(currentActivity)) {
            result.error("no_permissions", "Bluetooth permissions not granted", null)
            return
        }

        try {
            // ⚠️ WARNING: This approach is deprecated and may not work on modern Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result.error("not_supported", "Custom enable not supported on Android 10+", null)
                return
            }

            @Suppress("DEPRECATION")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled != true) {
                @Suppress("DEPRECATION")
                adapter?.enable()
                result.success(true)
            } else {
                result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in customEnable", e)
            result.error("enable_failed", "Failed to enable Bluetooth", e.message)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            val result = pendingResult
            if (result == null) {
                Log.d(TAG, "onActivityResult: pendingResult is null")
                return false
            }

            try {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "onActivityResult: User enabled Bluetooth")
                    result.success(true)
                } else {
                    Log.d(TAG, "onActivityResult: User did NOT enable Bluetooth")
                    result.success(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onActivityResult", e)
                result.error("result_error", "Error processing result", e.message)
            } finally {
                pendingResult = null
            }
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_PERMISSIONS) {
            val result = pendingResult
            if (result == null) {
                Log.d(TAG, "onRequestPermissionsResult: pendingResult is null")
                return false
            }

            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            try {
                if (allGranted) {
                    Log.d(TAG, "All Bluetooth permissions granted")
                    result.success(true)
                } else {
                    Log.d(TAG, "Some Bluetooth permissions denied")
                    result.success(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in permission result", e)
                result.error("permission_error", "Error processing permissions", e.message)
            } finally {
                pendingResult = null
            }
            return true
        }
        return false
    }
}
