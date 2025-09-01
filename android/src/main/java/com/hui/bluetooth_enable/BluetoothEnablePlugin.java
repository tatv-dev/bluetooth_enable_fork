package com.hui.bluetooth_enable;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.content.BroadcastReceiver;

import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

/** FlutterBluePlugin */
public class BluetoothEnablePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener, RequestPermissionsResultListener {
    private static final String TAG = "BluetoothEnablePlugin";
    private Activity activity;
    private MethodChannel channel;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Result pendingResult;

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_CODE_SCAN_ACTIVITY = 2777;

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        this.channel = new MethodChannel(binding.getBinaryMessenger(), "bluetooth_enable");
        this.channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
        this.channel.setMethodCallHandler(null);
        this.channel = null;
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
        this.mBluetoothManager = (BluetoothManager) this.activity.getSystemService(Context.BLUETOOTH_SERVICE);
        this.mBluetoothAdapter = mBluetoothManager.getAdapter();

        activityPluginBinding.addActivityResultListener(this);
        activityPluginBinding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        this.releaseResources();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        this.onAttachedToActivity(activityPluginBinding);
    }

    @Override
    public void onDetachedFromActivity() {
        this.releaseResources();
    }

    private void releaseResources() {
        this.activity = null;
        this.mBluetoothManager = null;
        this.mBluetoothAdapter = null;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "the device does not have bluetooth", null);
            return;
        }

        ActivityCompat.requestPermissions(this.activity,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_ENABLE_BLUETOOTH);

        switch (call.method) {
            case "enableBluetooth":
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
                Log.d(TAG, "rdddesult: " + result);
                pendingResult = result;
                break;
            case "customEnable":
                try {
                    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (!mBluetoothAdapter.isEnabled()) {
                        mBluetoothAdapter.disable();
                        Thread.sleep(500); //code for dealing with InterruptedException not shown
                        mBluetoothAdapter.enable();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "customEnable", e);
                }
                result.success("true");
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (pendingResult == null) {
                Log.d(TAG, "onActivityResult: problem: pendingResult is null");
            } else {
                try {
                    if (resultCode == Activity.RESULT_OK) {
                        Log.d(TAG, "onActivityResult: User enabled Bluetooth");
                        pendingResult.success("true");
                    } else {
                        Log.d(TAG, "onActivityResult: User did NOT enabled Bluetooth");
                        pendingResult.success("false");
                    }
                } catch (IllegalStateException | NullPointerException e) {
                    Log.d(TAG, "onActivityResult REQUEST_ENABLE_BLUETOOTH", e);
                }
            }
        }
        return false;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult, TWO");
        return false;
    }
}