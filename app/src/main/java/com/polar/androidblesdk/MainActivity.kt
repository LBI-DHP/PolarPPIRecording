package com.polar.androidblesdk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.snackbar.Snackbar
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
//
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.TextView
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.text.InputFilter
import android.text.InputFilter.AllCaps


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val API_LOGGER_TAG = "API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1
        private const val STORAGE_PERMISSION_CODE = 2;
    }

    // ATTENTION! Replace with the device ID from your device.
    private var deviceId = ""

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
            )
        )
    }
    private lateinit var broadcastDisposable: Disposable
    private var scanDisposable: Disposable? = null
    private var autoConnectDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var sdkModeEnableDisposable: Disposable? = null
    private var recordingStartStopDisposable: Disposable? = null
    private var recordingStatusReadDisposable: Disposable? = null
    private var listExercisesDisposable: Disposable? = null
    private var fetchExerciseDisposable: Disposable? = null
    private var removeExerciseDisposable: Disposable? = null

    private var sdkModeEnabledStatus = false
    private var deviceConnected = false
    private var bluetoothEnabled = false
    private var exerciseEntries: MutableList<PolarExerciseEntry> = mutableListOf()

    private lateinit var deviceIdEditText: AutoCompleteTextView
    private lateinit var connectButton: Button
    private lateinit var getTimeButton: Button
    private lateinit var getDiskSpaceButton: Button

    //Verity Sense offline recording use
    private lateinit var listRecordingsButton: Button
    private lateinit var statusRecordingsButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    private lateinit var downloadRecordingButton: Button
    private lateinit var deleteRecordingButton: Button
    private lateinit var statusLabel: TextView
    private lateinit var sharedPreferences: SharedPreferences


    private val entryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo())
        deviceIdEditText = findViewById(R.id.device_id)
        deviceIdEditText.filters = arrayOf<InputFilter>(AllCaps());
        connectButton = findViewById(R.id.connect_button)
        getTimeButton = findViewById(R.id.get_time)
        getDiskSpaceButton = findViewById(R.id.get_disk_space)

        //Verity Sense recording buttons
        listRecordingsButton = findViewById(R.id.list_recordings)
        statusRecordingsButton = findViewById(R.id.status_recordings)
        startRecordingButton = findViewById(R.id.start_recording)
        stopRecordingButton = findViewById(R.id.stop_recording)
        downloadRecordingButton = findViewById(R.id.download_recording)
        deleteRecordingButton = findViewById(R.id.delete_recording)
        statusLabel = findViewById(R.id.statusLabel)
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val savedPolarDeviceIds = loadPolarDeviceIds()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, savedPolarDeviceIds)
        deviceIdEditText.setAdapter(adapter)

        deviceIdEditText.setOnClickListener {
            deviceIdEditText.showDropDown()
        }

        api.setPolarFilter(false)
        disableAllButtons()
        // If there is need to log what is happening inside the SDK, it can be enabled like this:
        val enableSdkLogs = false
        if(enableSdkLogs) {
            api.setApiLogger { s: String -> Log.d(API_LOGGER_TAG, s) }
        }

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                bluetoothEnabled = powered
                if (powered && deviceConnected) {
                    enableAllButtons()
                    showToast("Phone Bluetooth on")
                } else {
                    disableAllButtons()
                    showToast("Phone Bluetooth off")
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                deviceId = polarDeviceInfo.deviceId
                deviceConnected = true
                val buttonText = getString(R.string.disconnect_from_device, deviceId)
                toggleButtonDown(connectButton, buttonText)
                savePolarDeviceIds(deviceId, adapter)
                enableAllButtons()
                setDownloadDeleteButtonsEnable(false)
                runWithDelay(2000) {
                    fetchOfflineRecordingStatus()
                }
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                deviceConnected = false
                val buttonText = getString(R.string.connect_to_device, deviceId)
                toggleButtonUp(connectButton, buttonText)
//                toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
                disableAllButtons()
                fetchOfflineRecordingStatus()
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
                showToast("BATTERY LEVEL: $level")
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                // deprecated
            }
        })

        connectButton.text = getString(R.string.connect_to_device, deviceIdEditText.text)
        connectButton.setOnClickListener {
            try {
                deviceId = deviceIdEditText.text.trim().toString()
                if (deviceConnected) {
                    api.disconnectFromDevice(deviceId)
                } else {
                    api.connectToDevice(deviceId)
                }
            } catch (polarInvalidArgument: PolarInvalidArgument) {
                val attempt = if (deviceConnected) {
                    "disconnect"
                } else {
                    "connect"
                }
                Log.e(TAG, "Failed to $attempt. Reason $polarInvalidArgument ")
            }
        }

        getTimeButton.setOnClickListener {
            api.getLocalTime(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { calendar ->
                        val timeGetString = "${calendar.time} read from the device"
                        Log.d(TAG, timeGetString)
                        showToast(timeGetString)

                    },
                    { error: Throwable -> Log.e(TAG, "get time failed: $error") }
                )
        }

        statusRecordingsButton.setOnClickListener {
            fetchOfflineRecordingStatus()
        }

        listRecordingsButton.setOnClickListener {
            listRecordings()
        }

        startRecordingButton.setOnClickListener {
            //Example of starting PPI offline recording
            Log.d(TAG, "Starts PPI recording")
                api.startOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPI)
                .subscribe(
                    { Log.d(TAG, "start offline recording completed") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                )
            showToast("Starts PPI recording")
            fetchOfflineRecordingStatus()
        }

        stopRecordingButton.setOnClickListener {
            //Example of stopping PPI offline recording
            Log.d(TAG, "Stops PPI recording")
            api.stopOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPI)
                .subscribe(
                    { Log.d(TAG, "stop offline recording completed") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                )
            showToast("Stops PPI recording")
            fetchOfflineRecordingStatus()
        }

        downloadRecordingButton.setOnClickListener {
            Log.d(TAG, "Searching to recording to download... ")
            //Get first entry for testing download
            val datetimeFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")
            val offlineRecEntry = entryCache[deviceId]?.lastOrNull()
            if(offlineRecEntry == null)
                showSnackbar("Please tap on list Verity Sense Recordings to have files entry up to date")
            offlineRecEntry?.let { offlineEntry ->
                try {
                       api.getOfflineRecord(deviceId, offlineEntry)
                           .observeOn(AndroidSchedulers.mainThread())
                           .subscribe(
                            {
                                Log.d(TAG, "Recording ${offlineEntry.path} downloaded. Size: ${offlineEntry.size}")
                                when (it) {
                                    is PolarOfflineRecordingData.PpiOfflineRecording -> {
                                        Log.d(TAG, "PPI Recording started at ${it.startTime}")//
                                        val fileName = "Polar_Device_ID_${deviceId}_PPI_Recording_${datetimeFormat.format(it.startTime.time)}.txt"
                                        saveRecordingToFile(it.data.samples, fileName, datetimeFormat.format(it.startTime.time))
                                        showSnackbar("File saved to: ${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)}/${fileName}.\n Tap here to open the folder", true)
                                    }
                                    else -> {
                                        Log.d(TAG, "Recording type is not yet implemented")
                                    }
                                }
                            },
                            { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) }
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Get offline recording fetch failed on entry ...", e)
                }
            }

        }

        deleteRecordingButton.setOnClickListener {
            val offlineRecEntry = entryCache[deviceId]?.lastOrNull()
            if(offlineRecEntry == null) {
                showSnackbar("Please tap on list Verity Sense Recording to have files entry up to date")
            }
            else
            {
                showConfirmationDialog(offlineRecEntry)
                runWithDelay(2000) {
                    listRecordings()
                }
            }
        }

        getDiskSpaceButton.setOnClickListener {
            api.getDiskSpace(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { diskSpace ->
                        Log.d(TAG, "disk space: $diskSpace")
                        showToast("Disk space left: ${diskSpace.freeSpace/1024}/${diskSpace.totalSpace/1024} KB")
                    },

                    { error: Throwable -> Log.e(TAG, "get disk space failed: $error") }
                )
        }

        var enableSdkModelLedAnimation = false
        var enablePpiModeLedAnimation = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
        }

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            // Android 11 and above: No need to request WRITE_EXTERNAL_STORAGE, use SAF or MediaStore
//            if (!Environment.isExternalStorageManager()) {
//                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
//                intent.data = Uri.parse("package:${applicationContext.packageName}")
//                startActivityForResult(intent, STORAGE_PERMISSION_CODE)
//            }
//        } else {
//            // Android 10 and below
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(
//                    this,
//                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
//                    STORAGE_PERMISSION_CODE
//                )
//            }
//        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (index in 0..grantResults.lastIndex) {
                if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                    disableAllButtons()
                    Log.w(TAG, "No sufficient permissions")
                    showToast("No sufficient permissions")
                    return
                }
            }
            Log.d(TAG, "Needed permissions are granted")
//            enableAllButtons()
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        api.foregroundEntered()
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    private fun toggleButtonDown(button: Button, text: String? = null) {
        toggleButton(button, true, text)
    }

    private fun toggleButtonDown(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, true, getString(resourceId))
    }

    private fun toggleButtonUp(button: Button, text: String? = null) {
        toggleButton(button, false, text)
    }

    private fun toggleButtonUp(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, false, getString(resourceId))
    }

    private fun toggleButton(button: Button, isDown: Boolean, text: String? = null) {
        if (text != null) button.text = text

        var buttonDrawable = button.background
        buttonDrawable = DrawableCompat.wrap(buttonDrawable!!)
        if (isDown) {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryDarkColor))
        } else {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryColor))
        }
        button.background = buttonDrawable
    }

    private fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
        val availableSettings = api.requestStreamSettings(identifier, feature)
        val allSettings = api.requestFullStreamSettings(identifier, feature)
            .onErrorReturn { error: Throwable ->
                Log.w(TAG, "Full stream settings are not available for feature $feature. REASON: $error")
                PolarSensorSetting(emptyMap())
            }
        return Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
            if (available.settings.isEmpty()) {
                throw Throwable("Settings are not available")
            } else {
                Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
                Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
                return@zip android.util.Pair(available, all)
            }
        }
            .observeOn(AndroidSchedulers.mainThread())
            .toFlowable()
            .flatMap { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
                DialogUtility.showAllSettingsDialog(
                    this@MainActivity,
                    sensorSettings.first.settings,
                    sensorSettings.second.settings
                ).toFlowable()
            }
    }

    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }

    private fun showSnackbar(message: String, isOpenDocumentFolder: Boolean = false) {
        val contextView = findViewById<View>(R.id.buttons_container)
        val snackbar = Snackbar.make(contextView, message, Snackbar.LENGTH_LONG)
        if(isOpenDocumentFolder)
            snackbar.setAction("Open") {
                openDocumentsFolder()
            }
        snackbar.show()
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                // Respond to positive button press
            }
            .show()
    }

    private fun disableAllButtons() {
        getTimeButton.isEnabled = false
        getDiskSpaceButton.isEnabled = false
        //Verity Sense recording buttons
        listRecordingsButton.isEnabled = false
        statusRecordingsButton.isEnabled = false
        startRecordingButton.isEnabled = false
        stopRecordingButton.isEnabled = false
        downloadRecordingButton.isEnabled = false
        deleteRecordingButton.isEnabled = false
    }

    private fun enableAllButtons() {
        getTimeButton.isEnabled = true
        getDiskSpaceButton.isEnabled = true
        //Verity Sense recording buttons
        listRecordingsButton.isEnabled = true
        statusRecordingsButton.isEnabled = true
        startRecordingButton.isEnabled = true
        stopRecordingButton.isEnabled = true
        downloadRecordingButton.isEnabled = true
        deleteRecordingButton.isEnabled = true
    }

    private fun disposeAllStreams() {
        ecgDisposable?.dispose()
        accDisposable?.dispose()
        gyrDisposable?.dispose()
        magDisposable?.dispose()
        ppgDisposable?.dispose()
        ppgDisposable?.dispose()
    }

    private fun saveRecordingToFile(samples: List<PolarPpiData.PolarPpiSample>, fileName: String, startTime: String) {
        try {
            val download_folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/Polar_PPI_Recording");
            createFolder(this, "Polar_PPI_Recording")
            val outputFile = File(download_folder, fileName)
            FileOutputStream(outputFile).use { outputStream ->
                outputStream.write("Polar Device ID: ${deviceId}\n".toByteArray())
                val header1 = "PPI Recording started at: ${startTime}\n"
                outputStream.write(header1.toByteArray())
                val header2 = "hr,PPI,blockerBit,skinContactStatus,skinContactSupported,errorEstimate\n"
                outputStream.write(header2.toByteArray())
                samples.forEach { sample ->
                    val line = "${sample.hr},${sample.ppi},${sample.blockerBit},${sample.skinContactStatus},${sample.skinContactSupported},${sample.errorEstimate}\n"
                    outputStream.write(line.toByteArray())
                }
            }
            Log.d(TAG, "File saved to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file", e)
        }
    }

    private fun updateStatus(message: String,  color: Int = Color.WHITE) {
        statusLabel.text = message
        statusLabel.setTextColor(color)
    }

    private fun listRecordings() {
        api.listOfflineRecordings(deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                entryCache[deviceId] = mutableListOf()
            }
            .map {
                entryCache[deviceId]?.add(it)
                it
            }
            .subscribe(
                { polarOfflineRecordingEntry: PolarOfflineRecordingEntry ->
                    Log.d(
                        TAG,
                        "next: ${polarOfflineRecordingEntry.date} path: ${polarOfflineRecordingEntry.path} size: ${polarOfflineRecordingEntry.size}"
                    )
                },
                { error: Throwable -> showToast("Failed to list recordings: $error") },
                {
                    Log.d(TAG, "list recordings complete")
                    showToast("list recordings complete")
                    val isEnable = entryCache[deviceId]?.any() == true
                    setDownloadDeleteButtonsEnable(isEnable)
                }
            )
    }

    private fun fetchOfflineRecordingStatus() {
        api.getOfflineRecordingStatus(deviceId)
            .observeOn(AndroidSchedulers.mainThread()) // Observe results on main thread
            .subscribe(
                { statusList ->
                    updateStatus("")
                    val status = statusList.firstOrNull()
                    if(status == PolarBleApi.PolarDeviceDataType.PPI)
                    {
                        updateStatus("The PPI offline recording has been started!", Color.GREEN)
                        startRecordingButton.isEnabled = false
                        stopRecordingButton.isEnabled = true
                    }
                    else
                    {
                        updateStatus("The PPI offline recording has been stopped!",Color.RED)
                        stopRecordingButton.isEnabled = false
                        startRecordingButton.isEnabled = true
                    }
                },
                { error -> Log.e(TAG, "${error}")
                }
            )
    }

    private fun showConfirmationDialog(offlineRecEntry: PolarOfflineRecordingEntry?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm Delete")
        builder.setMessage("Are you sure you want to delete last recorded item?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            // Perform the delete action
            deleteLastRecordedItem(offlineRecEntry)
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            // Dismiss the dialog
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun deleteLastRecordedItem(offlineRecEntry: PolarOfflineRecordingEntry?) {
        //Example of one offline recording deletion
        //NOTE: For this example you need to click on listRecordingsButton to have files entry (entryCache) up to date
        Log.d(TAG, "Searching to recording to delete... ")
        //Get first entry for testing deletion
        offlineRecEntry?.let { offlineEntry ->
            try {
                api.removeOfflineRecord(deviceId, offlineEntry)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            Log.d(TAG, "Recording file deleted")
                            showToast("Recording file deleted")

                        },
                        { error ->
                            val errorString = "Recording file deletion failed: $error"
                            showToast(errorString)
                            Log.e(TAG, errorString)
                        }
                    )

            } catch (e: Exception) {
                Log.e(TAG, "Delete offline recording failed on entry ...", e)
            }
        }
    }

    private fun openDocumentsFolder() {
        // Intent to open the Documents folder
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents"))
        }
        startActivity(intent)
    }

    private fun setDownloadDeleteButtonsEnable(enable:Boolean)
    {
        downloadRecordingButton.isEnabled = enable
        deleteRecordingButton.isEnabled = enable
    }

    private fun loadPolarDeviceIds(): MutableList<String> {
        return sharedPreferences.getStringSet("polarDeviceIds", emptySet())?.toMutableList() ?: mutableListOf()
    }

    private fun savePolarDeviceIds(suggestion: String, adapter:ArrayAdapter<String>) {
        val ids = loadPolarDeviceIds()
        if (!ids.contains(suggestion)) {
            ids.add(suggestion)
            sharedPreferences.edit().putStringSet("polarDeviceIds", ids.toSet()).apply()
            adapter.add(deviceId) // Add the new text to the adapter
            adapter.notifyDataSetChanged()
        }
    }

    private fun createFolder(context: Context, folderName: String) {

        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
    }

    private fun runWithDelay(delayMillis: Long, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            action()
        }, delayMillis)
    }

}