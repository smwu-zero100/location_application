package com.example.bindingtest;

import android.os.Bundle;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.Manifest;
import android.location.Location;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.URI;
import java.net.URISyntaxException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.android.gms.location.Priority;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final long UPDATE_INTERVAL = 1000;  // 1 seconds
    private static final long FASTEST_INTERVAL = 500;

    private FusedLocationProviderClient mFusedLocationClient;
    private WebSocketClient mWebsocketClient;
    private LocationRequest mLocationRequest;

    private static final String TAG = "MainActivity";

    private TextView uriInput;
    private TextView logBox;
    private Button connectButton;
    private Button disconnectButton;

    Location lastLocation = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        uriInput = findViewById(R.id.editTextWebsocketIP);
        logBox = findViewById(R.id.textViewLogBox);
        connectButton = findViewById(R.id.button_connect);
        disconnectButton = findViewById(R.id.button_disconnect);

        connectButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                if(mWebsocketClient == null || !mWebsocketClient.isOpen()){
                    connectWebSocket(uriInput.getText().toString());
                }
                else{
                    Log.i(TAG, "websocket already open!");
                }
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mWebsocketClient != null || mWebsocketClient.isOpen()){
                    mWebsocketClient.close();
                }
                else{
                    Log.i(TAG, "websocket closed!");
                }
            }
        });

        logBox.setMovementMethod(new ScrollingMovementMethod());
        Log.i(TAG, "before reqeust Location permission");

        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            if (mWebsocketClient != null && mWebsocketClient.isOpen()) {
                startLocationUpdates();
            }
            else{
                Log.i(TAG, "Websocket is not ready!");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Log.w(TAG, "Location permission denied");
            }
        }
    }

    public int getHeading(Location from, Location to){
        double lat1 = Math.toRadians(from.getLatitude());
        double lon1 = Math.toRadians(from.getLongitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double lon2 = Math.toRadians(to.getLongitude());

        double x = Math.cos(lat2) * Math.sin(lon2 - lon1);
        double y = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);


        int heading = (int)Math.toDegrees(Math.atan2(x, y));

//        if (heading < 0) {
//            heading += 360;
//        }
        // Log.i("TAG", "from : " + lat1 + lon1 + "\nto : " + lat2 + lon2 + "\n heading : " + heading);
        return heading;
    }

    public void startLocationUpdates() {

        mLocationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(UPDATE_INTERVAL)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult.getLastLocation() != null && mWebsocketClient != null && mWebsocketClient.isOpen()) {
                    Location currentLocation = locationResult.getLastLocation();
                    Log.i(TAG, "Location Update : " + currentLocation.toString());

                    double latitude = currentLocation.getLatitude();
                    double longitude = currentLocation.getLongitude();
                    int heading = 0; //getBearingAccuracyDegrees();

                    if(lastLocation != null){
                        heading = getHeading(lastLocation, currentLocation);
                    }

                    lastLocation = currentLocation;
                    String message = "location, " + latitude + "," + longitude + "," + heading ;
                    logBox.setText(latitude + ", " + longitude + ", " + heading + "\n" + logBox.getText().toString());
                    Log.i(TAG, message);
                    mWebsocketClient.send(message);

                } else{
                    Log.e(TAG, "Location/Websocket Error!");
                }
            }
        };

        try {
            // 권한이 필요한 코드
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            requestLocationPermission();
        }
    }

    public void connectWebSocket(String uriInput){
        URI uri;
        Log.i(TAG, uriInput);

        try{
            uri = new URI("ws://"+uriInput+":9090");
        } catch(URISyntaxException e){
            Log.e(TAG, "empty URI Input!");
            e.printStackTrace();
            return;
        }
        Log.i(TAG, uri.toString());
        mWebsocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.i(TAG, "Websocket Connection!");
                logBox.setText(logBox.getText().toString() + "connected!\n");
            }

            @Override
            public void onMessage(String message) {
                // Log.i(TAG, "Websocket Msg Received! : " + message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.i(TAG, "Websocket Connection Closed!");
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "Websocket Error! : " + ex.getMessage());
            }
        };
        mWebsocketClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWebsocketClient != null && mWebsocketClient.isOpen()) {
            mWebsocketClient.close();
        }
    }
}