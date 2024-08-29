package com.iambhargavnath.arduinobluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 101;

    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectThread mConnectThread;

    ArrayList<String> bluetoothList, bluetoothMAC;

    ActivityResultLauncher<Intent> bluetoothActivityResultLauncher;

    private CSVWriter writer;

    Button btBtn, disconnectBtn;
    TextView timeText, dataText;

    ScrollView scrollView;

    private DialogFragment progressDialog;

    private int time = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btBtn = (Button) findViewById(R.id.btBtn);
        disconnectBtn = (Button) findViewById(R.id.disconnectBtn);
        timeText = (TextView) findViewById(R.id.timeText);
        dataText = (TextView) findViewById(R.id.dataText);
        scrollView = (ScrollView) findViewById(R.id.scrollView);

        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device does not Support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        }

        btBtn.setOnClickListener(view -> checkPermission());

        disconnectBtn.setOnClickListener(view -> mConnectThread.cancel());

        bluetoothActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        Toast.makeText(this, "User denied request to turn on Bluetooth", Toast.LENGTH_SHORT).show();
                    }
                    else
                    {
                        getDeviceList();
                    }
                });

    }

    private void showProgressDialog() {
        progressDialog = ProgressDialogFragment.newInstance("Connecting", "Connecting to Bluetooth Device.\nPlease wait...");
        progressDialog.show(getSupportFragmentManager(), "progressDialog");
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    private void enableBluetooth()
    {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothActivityResultLauncher.launch(enableBtIntent);
        }
        else
        {
            getDeviceList();
        }
    }
    @SuppressLint("MissingPermission")
    public void getDeviceList()
    {
        bluetoothList = new ArrayList<>();
        bluetoothMAC = new ArrayList<>();
        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            bluetoothList.add(device.getName());
            bluetoothMAC.add(device.getAddress());
        }
        showBluetoothDevices();
    }

    private void showBluetoothDevices()
    {
        ViewGroup viewGroup = findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.bluetooth_devices, viewGroup, false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        ListView bluetoothDeviceList = (ListView) dialogView.findViewById(R.id.bluetoothDeviceList);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, bluetoothList);
        bluetoothDeviceList.setAdapter(adapter);
        bluetoothDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                alertDialog.dismiss();
                showProgressDialog();
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(bluetoothMAC.get(position).trim()); // replace with your device's address
                mConnectThread = new ConnectThread(device);
                mConnectThread.start();
            }
        });



    }

    private void checkPermission()
    {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BLUETOOTH_PERMISSION);
        } else {
            enableBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth();
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private final StringBuilder dataBuffer = new StringBuilder();
    private final Handler dataHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            time = time+1;
            String receivedData = (String) message.obj;
            dataBuffer.append(receivedData);
            try {
                JSONObject jsonObject = new JSONObject(receivedData);
                String data = jsonObject.getString("frequency");
                saveData(String.valueOf(time), data);
                timeText.setText(timeText.getText().toString()+time+"\n");
                dataText.setText(dataText.getText().toString()+data+"\n");
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            } catch (JSONException e)
            {
                e.printStackTrace();
            }
        }
    };


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                tmp = device.createRfcommSocketToServiceRecord(PORT_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmSocket = tmp;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            time = 0;
            try {
                mmSocket.connect();
                dismissProgressDialog();
                createCSV();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Device Connected", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException connectException) {
                dismissProgressDialog();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to Connect Device", Toast.LENGTH_SHORT).show();
                });
                btBtn.setEnabled(true);
                btBtn.setVisibility(View.VISIBLE);
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Message message = Message.obtain();
                    message.obj = line;
                    dataHandler.sendMessage(message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            stopSaving();
            dataText.setText(null);
            timeText.setText(null);
            time = 0;
            try {
                if(mmSocket.isConnected()) {
                    mmSocket.close();
                }
                Toast.makeText(MainActivity.this, "Device Disconnected.\nData Saved!", Toast.LENGTH_SHORT).show();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

    }

    private void createCSV()
    {
        String fileName = System.currentTimeMillis()+".csv";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS+"/ArduinoBluetooth");
        Uri contentUri = MediaStore.Files.getContentUri("external");
        Uri itemUri = getContentResolver().insert(contentUri, contentValues);
        if (itemUri != null) {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(itemUri, "wa");
                if (outputStream != null) {
                    writer = new CSVWriter(new OutputStreamWriter(outputStream));
                    String[] data = {"Time(s)", "Frequency(Hz)"};
                    writer.writeNext(data);
                    writer.flush();
                }
            } catch (IOException e) {
            }
        }

    }

    private void saveData(String time, String frequency) {
        String[] data = {time, frequency};
        if (writer != null) {
            writer.writeNext(data);
            try {
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopSaving() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}