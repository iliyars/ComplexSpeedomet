package com.example.xiaomi.complexspeedometr;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener {

    private static final int REQ_ENABLE_BT = 10;
    private static final int BT_BOUNDED = 20;
    private static final int BT_SEARCH = 21;
    private static final int REQUEST_CODE_LOC = 1;
    private static final int LED_RED = 30;
    private static final int LED_GREEN = 31;

    private FrameLayout frameText;
    private LinearLayout frameBtn;

    private Switch swichBT;
    private Button btnSearch;
    private ProgressBar pbProgress;
    private ListView lvDevices;

    private BluetoothAdapter bluetoothAdapter;
    private BtListAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;


    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Switch switchRedLed;
    private Switch switchGreenLed;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private EditText etConsole;
    private ProgressDialog progressDialog;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameText = findViewById(R.id.frame_text);
        frameBtn = findViewById(R.id.frame_btn);

        swichBT = findViewById(R.id.switch_bt_on_off);
        btnSearch = findViewById(R.id.btn_search);
        pbProgress = findViewById(R.id.pb_progress);
        lvDevices = findViewById(R.id.lv_btn_device);

        frameLedControls = findViewById(R.id.frameLedControls);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        switchRedLed = findViewById(R.id.switch_led_red);
        switchGreenLed = findViewById(R.id.switch_led_green);
        etConsole = findViewById(R.id.et_console);



        swichBT.setOnCheckedChangeListener(this);
        btnSearch.setOnClickListener(this);
        lvDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        switchRedLed.setOnCheckedChangeListener(this);
        switchGreenLed.setOnCheckedChangeListener(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle("Соединение");
        progressDialog.setMessage("Подождите");


        bluetoothDevices = new ArrayList<>();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживаеться", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (bluetoothAdapter.isEnabled()) {
            showFrameBtn();
            swichBT.setChecked(true);
            setListAdapter(BT_BOUNDED);
        }

    }

    private void showFrameText() {
        frameText.setVisibility(View.VISIBLE);
        frameBtn.setVisibility(View.GONE);
        frameLedControls.setVisibility(View.GONE);
    }

    private void showFrameBtn() {
        frameText.setVisibility(View.GONE);
        frameBtn.setVisibility(View.VISIBLE);
        frameLedControls.setVisibility(View.GONE);

    }
    private void showFrameLed() {
        frameText.setVisibility(View.GONE);
        frameBtn.setVisibility(View.GONE);
        frameLedControls.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
        if (connectThread != null) {
            connectThread.cancel();
        }

        if (connectedThread != null) {
            connectedThread.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(btnSearch)) {
            enableSearch();
        }else if (v.equals(btnDisconnect)){
            if(connectThread != null) {
                connectThread.cancel();
            }
            if(connectedThread != null) {
                connectedThread.cancel();
            }

            showFrameBtn();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.equals(lvDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start();
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(swichBT)) {
            enableBT(isChecked);
            if (!isChecked) {
                showFrameText();
            }
        }else if (buttonView.equals(switchRedLed)) {
            enableLed(LED_RED, isChecked);
        }else if( buttonView.equals(switchGreenLed)){
            enableLed(LED_GREEN,isChecked);
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameBtn();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                swichBT.setChecked(false);
            }
        }
    }

    private void enableBT(boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void setListAdapter(int type) {
        bluetoothDevices.clear();
        int iconType = R.drawable.ic_bluetooth_bounded;

        switch (type) {
            case BT_BOUNDED:
                bluetoothDevices = getBoundedDevices();
                iconType = R.drawable.ic_bluetooth_bounded;
                break;
            case BT_SEARCH:
                iconType = R.drawable.ic_bluetooth_search;
                break;
        }
        listAdapter = new BtListAdapter(this, bluetoothDevices, iconType);
        lvDevices.setAdapter(listAdapter);
    }

    private ArrayList<BluetoothDevice> getBoundedDevices() {
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();
        if (deviceSet.size() > 0) {
            for (BluetoothDevice device : deviceSet) {
                tmpArrayList.add(device);
            }
        }
        return tmpArrayList;
    }

    private void enableSearch() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }

    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    btnSearch.setText("Остановить поиск");
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    btnSearch.setText("Начать Поиск");
                    pbProgress.setVisibility(View.GONE);
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        bluetoothDevices.add(device);
                        listAdapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    /**
     * Запрос на разрешение данных о местоположении (для Marshmallow 6.0)
     */
    private void accessLocationPermission() {
        int accessCoarseLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int accessFineLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:

                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                    //TODO - Add your code here to start Discovery
                }
                break;
            default:
                return;
        }
    }

    private class ConnectThread extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;

        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);

                progressDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;

                progressDialog.dismiss();
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLed();
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Не могу соединиться!",Toast.LENGTH_SHORT).show();
                    }
                });

                cancel();
            }

            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLed();
                    }
                });
            }
        }

        public boolean isConnect() {
            return bluetoothSocket.isConnected();
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread  extends  Thread {

        private final InputStream inputStream;
        private final OutputStream outputStream;

        private boolean isConnected = false;

        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.inputStream = inputStream;
            this.outputStream = outputStream;
            isConnected = true;
        }

        @Override
        public void run() {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            StringBuffer buffer = new StringBuffer();
            final StringBuffer sbConsole = new StringBuffer();
            final ScrollingMovementMethod movementMethod = new ScrollingMovementMethod();

            while (isConnected){
                try{
                    int bytes = bis.read();
                    buffer.append((char) bytes);
                    int eof = buffer.indexOf("\r\n");
                    if (eof > 0) {
                        sbConsole.append(buffer.toString());
                        buffer.delete(0, buffer.length());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                etConsole.setText(sbConsole.toString());
                                etConsole.setMovementMethod(movementMethod);

                            }
                        });
                    }
                }catch (IOException e) {
                    e.printStackTrace();

                }
            }
            try {
                bis.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        public void write(String command) {
            byte[] bytes = command.getBytes();
            if (outputStream != null) {
                try {
                    outputStream.write(bytes);
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                isConnected = false;
                inputStream.close();
                outputStream.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
        private void enableLed(int led, boolean state) {
        if (connectedThread != null && connectThread.isConnect()) {
            String command = "";

            switch (led) {
                case LED_RED:
                    command = (state) ? "red on#" : "red of#";
                    break;
                case  LED_GREEN:
                    command = (state) ? "green on#" : "green off#";
                    break;
            }
            connectedThread.write(command);
        }
    }

}
