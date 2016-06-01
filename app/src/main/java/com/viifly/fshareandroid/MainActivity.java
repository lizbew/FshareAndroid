package com.viifly.fshareandroid;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.android.FixedAndroidLogHandler;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

import java.io.InputStream;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener
, View.OnClickListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    private ArrayAdapter<DeviceDisplay> listAdapter;
    private BrowseRegistryListener registryListener = new BrowseRegistryListener();
    private AndroidUpnpService upnpService;

    private SharedPreferences prefs;

    private Uri mReceivedImgUri;

    private TextView tv1;
    private ListView mListView;

    private String prefUploadUrl;

    Button buttonSend;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG,  "onServiceConnected, " + className.getClassName());
            upnpService = (AndroidUpnpService) service;

            // Clear the list
            listAdapter.clear();

            // Get ready for future device advertisements
            upnpService.getRegistry().addListener(registryListener);

            // Now add all devices to the list we already know about
            for (Device device : upnpService.getRegistry().getDevices()) {
                registryListener.deviceAdded(device);
            }

            // Search asynchronously for all devices, they will respond soon
            upnpService.getControlPoint().search(new STAllHeader());
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fix the logging integration between java.util.logging and Android internal logging
        org.seamless.util.logging.LoggingUtil.resetRootHandler(
                new FixedAndroidLogHandler()
        );
        // Now you can enable logging as needed for various categories of Cling:
        // Logger.getLogger("org.fourthline.cling").setLevel(Level.FINEST);

        mListView = (ListView)findViewById(R.id.list_view1);
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mListView.setAdapter(listAdapter);

        tv1 = (TextView)findViewById(R.id.textView2);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        prefUploadUrl = prefs.getString("upload_url", "");

        getApplicationContext().bindService(
                new Intent(this, AndroidUpnpServiceImpl.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        Button buttonSearch = (Button)findViewById(R.id.button_search);
        if (buttonSearch != null) {
            buttonSearch.setOnClickListener(this);
        }

        buttonSend = (Button)findViewById(R.id.button_send);
        if (buttonSend != null) {
            buttonSend.setOnClickListener(this);
            buttonSend.setEnabled(false);
        }
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                handleSendImage(intent); // Handle single image being sent
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
        }
        // This will stop the UPnP service if nobody else is bound to it
        getApplicationContext().unbindService(serviceConnection);
    }

    void handleSendImage(Intent intent) {
        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            mReceivedImgUri = imageUri;
            buttonSend.setEnabled(true);

            // Update UI to reflect image being shared
            Log.d(TAG, "Received file share " + imageUri.toString());
            tv1.setText(imageUri.toString());
        }
    }

    public void uploadToFshare(Uri imgUri) {
        //String url = "http://192.168.1.102:8036/upload";
        String url = prefUploadUrl;
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "No UploadUrl in Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        final ProgressDialog loading = ProgressDialog.show(this, "Uploading", "Please waiting..",
                false, false);

        //ParcelFileDescriptor inputPFD = getContentResolver().openFileDescriptor(returnUri, "r");
        //FileDescriptor fd = inputPFD.getFileDescriptor();
        //MediaStore.Images.Media.getBitmap()
        Cursor infoCursor = null;
        InputStream inputStream;
        String name;
        long size;

        try {
            inputStream = getContentResolver().openInputStream(imgUri);

            infoCursor = getContentResolver().query(imgUri, null, null, null, null);
            int nameIndex = infoCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndex  = infoCursor.getColumnIndex(OpenableColumns.SIZE);
            infoCursor.moveToFirst();
            name = infoCursor.getString(nameIndex);
            size = infoCursor.getLong(sizeIndex);
        } catch (Exception ex) {
            Log.e(TAG, "openInputStream of " +imgUri.toString(), ex);
            return;
        } finally {
            if (infoCursor != null) {
                infoCursor.close();
            }
        }

        // Request a string response from the provided URL.
        MultipartRequest uploadRequest = new MultipartRequest(url, inputStream, name, size,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        loading.dismiss();
                        // Display the first 500 characters of the response string.
                        //mTextView.setText("Response is: "+ response.substring(0,500));
                        Log.d(TAG, "Volley callback,  Response is: " + response);
                        Toast.makeText(MainActivity.this,"Upload Completed.", Toast.LENGTH_LONG).show();

                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        loading.dismiss();
                        //mTextView.setText("That didn't work!");
                        Log.d(TAG, "Volley callback,  error " + error);
                        Toast.makeText(MainActivity.this,"Upload Error.", Toast.LENGTH_LONG).show();
                    }
        }) ;

        VolleyQueueSingleton.getInstance(MainActivity.this).addToRequestQueue(uploadRequest);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        prefUploadUrl = prefs.getString("upload_url", "");
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.button_search:
                Log.d(TAG, "Search button clicked");
                if (this.upnpService != null) {
                    Log.d(TAG, " - Triggered UPnP search.");
                    this.upnpService.getControlPoint().search(new STAllHeader());
                }
                break;
            case R.id.button_send:
                if (mReceivedImgUri != null) {
                    this.uploadToFshare(mReceivedImgUri);
                } else {
                    Toast.makeText(this,
                            "NO Image received",
                            Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    protected class BrowseRegistryListener extends DefaultRegistryListener {

        /* Discovery performance optimization for very slow Android devices! */
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) {
            Log.d(TAG, "Couldn't retrieve device/service descriptors");
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(
                            MainActivity.this,
                            "Discovery failed of '" + device.getDisplayString() + "': "
                                    + (ex != null ? ex.toString() : "Couldn't retrieve device/service descriptors"),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
            deviceRemoved(device);
        }
        /* End of optimization, you can remove the whole block if your Android handset is fast (>= 600 Mhz) */

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        public void deviceAdded(final Device device) {
            Log.d(TAG, "Device added " + device.toString());
            runOnUiThread(new Runnable() {
                public void run() {
                    DeviceDisplay d = new DeviceDisplay(device);
                    int position = listAdapter.getPosition(d);
                    if (position >= 0) {
                        // Device already in the list, re-set new value at same position
                        listAdapter.remove(d);
                        listAdapter.insert(d, position);
                    } else {
                        listAdapter.add(d);
                    }
                }
            });
        }

        public void deviceRemoved(final Device device) {
            runOnUiThread(new Runnable() {
                public void run() {
                    listAdapter.remove(new DeviceDisplay(device));
                }
            });
        }
    }

    protected class DeviceDisplay {

        Device device;

        public DeviceDisplay(Device device) {
            this.device = device;
        }

        public Device getDevice() {
            return device;
        }

        // DOC:DETAILS
        public String getDetailsMessage() {
            StringBuilder sb = new StringBuilder();
            if (getDevice().isFullyHydrated()) {
                sb.append(getDevice().getDisplayString());
                sb.append("\n\n");
                for (Service service : getDevice().getServices()) {
                    sb.append(service.getServiceType()).append("\n");
                }
            } else {
                sb.append(getString(R.string.deviceDetailsNotYetAvailable));
            }
            return sb.toString();
        }
        // DOC:DETAILS

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DeviceDisplay that = (DeviceDisplay) o;
            return device.equals(that.device);
        }

        @Override
        public int hashCode() {
            return device.hashCode();
        }

        @Override
        public String toString() {
            String name =
                    getDevice().getDetails() != null && getDevice().getDetails().getFriendlyName() != null
                            ? getDevice().getDetails().getFriendlyName()
                            : getDevice().getDisplayString();
            // Display a little star while the device is being loaded (see performance optimization earlier)
            return device.isFullyHydrated() ? name : name + " *";
        }
    }

}
