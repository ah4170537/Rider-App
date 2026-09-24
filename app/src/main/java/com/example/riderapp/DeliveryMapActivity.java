package com.example.riderapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeliveryMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private String customerAddressStr;
    private String documentPath;
    private Button btnStartJourney;

    private Marker riderMarker;
    private LocationCallback locationCallback;

    // Navigation tracking variables (starts false so it shows 2D view first)
    private boolean isJourneyStarted = false;
    private LatLng customerLatLng;
    private Polyline currentRoutePolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_map);

        // Retrieve customer address and document path passed from the previous activity
        customerAddressStr = getIntent().getStringExtra("customerAddress");
        documentPath = getIntent().getStringExtra("documentPath");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        double lat = getIntent().getDoubleExtra("customerLat", 0.0);
        double lng = getIntent().getDoubleExtra("customerLng", 0.0);
        if (lat != 0.0 && lng != 0.0) {
            customerLatLng = new LatLng(lat, lng);
        }

        // Initialize the Start Journey Button for switching to 3D navigation mode
        btnStartJourney = findViewById(R.id.btnStartJourney);
        if (btnStartJourney != null) {
            btnStartJourney.setOnClickListener(v -> {
                if (customerLatLng == null) {
                    Toast.makeText(this, "Destination coordinates not available", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Toggle journey state to active navigation (No database status update here anymore)
                isJourneyStarted = true;
                btnStartJourney.setText("Navigating...");
                btnStartJourney.setEnabled(false);
                Toast.makeText(this, "3D Navigation Active!", Toast.LENGTH_SHORT).show();

                if (riderMarker != null) {
                    fetchRoadRouteFromOSRM(riderMarker.getPosition(), customerLatLng);
                    startNavigationCamera(riderMarker.getPosition(), 0);
                }
            });
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setTiltGesturesEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(true);

        getRouteAndShowPins();
        startLocationUpdates();
    }

    private void getRouteAndShowPins() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        double intentLat = getIntent().getDoubleExtra("customerLat", 0.0);
        double intentLng = getIntent().getDoubleExtra("customerLng", 0.0);

        if (intentLat != 0.0 && intentLng != 0.0) {
            customerLatLng = new LatLng(intentLat, intentLng);

            mMap.addMarker(new MarkerOptions()
                    .position(customerLatLng)
                    .title("Customer Destination (GPS)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            proceedWithRiderLocationAndRoute();

        } else {
            Toast.makeText(this, "GPS coordinates missing. Using text address fallback...", Toast.LENGTH_SHORT).show();

            if (customerAddressStr != null && !customerAddressStr.isEmpty()) {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addressList = geocoder.getFromLocationName(customerAddressStr, 1);
                    if (addressList != null && !addressList.isEmpty()) {
                        Address address = addressList.get(0);
                        customerLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                        mMap.addMarker(new MarkerOptions()
                                .position(customerLatLng)
                                .title("Customer Destination (Address Fallback)")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                    }
                } catch (IOException e) {
                    Log.e("GeocodeError", e.getMessage());
                }
            }

            proceedWithRiderLocationAndRoute();
        }
    }

    private void proceedWithRiderLocationAndRoute() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location == null) return;

            LatLng riderLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            BitmapDescriptor riderIcon = createUberDotIcon();

            riderMarker = mMap.addMarker(new MarkerOptions()
                    .position(riderLatLng)
                    .title("Your Location (Rider)")
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .icon(riderIcon));

            if (customerLatLng != null) {
                fetchRoadRouteFromOSRM(riderLatLng, customerLatLng);

                // Show both rider and customer in a 2D overview zoom bounds initially
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(riderLatLng);
                builder.include(customerLatLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
            }
        });
    }

    private BitmapDescriptor createUberDotIcon() {
        int height = 48;
        int width = 48;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(true);
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, borderPaint);

        Paint innerPaint = new Paint();
        innerPaint.setColor(Color.parseColor("#2196F3"));
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setAntiAlias(true);
        canvas.drawCircle(width / 2f, height / 2f, width / 2.6f, innerPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (android.location.Location location : locationResult.getLocations()) {
                    LatLng newPos = new LatLng(location.getLatitude(), location.getLongitude());
                    float bearing = location.hasBearing() ? location.getBearing() : 0;

                    if (riderMarker != null) {
                        riderMarker.setPosition(newPos);
                    }

                    // Only activate 3D navigation camera if the user explicitly clicked the Start Journey button
                    if (isJourneyStarted && customerLatLng != null) {
                        fetchRoadRouteFromOSRM(newPos, customerLatLng);
                        startNavigationCamera(newPos, bearing);
                    }

                    // Push live GPS coordinates to Firestore tracking path
                    if (documentPath != null && !documentPath.isEmpty()) {
                        Map<String, Object> liveLocation = new HashMap<>();
                        liveLocation.put("latitude", location.getLatitude());
                        liveLocation.put("longitude", location.getLongitude());
                        liveLocation.put("updatedAt", FieldValue.serverTimestamp());

                        FirebaseFirestore.getInstance().document(documentPath)
                                .collection("tracking").document("liveLocation")
                                .set(liveLocation)
                                .addOnFailureListener(e -> Log.e("FirestoreTracking", "Failed to update location", e));
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void startNavigationCamera(LatLng currentPos, float bearing) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(currentPos)
                .zoom(18f)
                .tilt(50f)
                .bearing(bearing)
                .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
    }

    private void fetchRoadRouteFromOSRM(LatLng start, LatLng end) {
        String url = "https://router.project-osrm.org/route/v1/driving/" +
                start.longitude + "," + start.latitude + ";" +
                end.longitude + "," + end.latitude +
                "?overview=full&geometries=geojson";

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray routes = response.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            JSONObject geometry = route.getJSONObject("geometry");
                            JSONArray coordinates = geometry.getJSONArray("coordinates");

                            List<LatLng> polylinePoints = new ArrayList<>();
                            for (int i = 0; i < coordinates.length(); i++) {
                                JSONArray coord = coordinates.getJSONArray(i);
                                double lon = coord.getDouble(0);
                                double lat = coord.getDouble(1);
                                polylinePoints.add(new LatLng(lat, lon));
                            }

                            runOnUiThread(() -> {
                                if (currentRoutePolyline != null) {
                                    currentRoutePolyline.remove();
                                }

                                currentRoutePolyline = mMap.addPolyline(new PolylineOptions()
                                        .addAll(polylinePoints)
                                        .width(14f)
                                        .color(Color.parseColor("#1E88E5")));
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("OSRMError", "Parsing error: " + e.getMessage());
                    }
                },
                error -> Log.e("OSRMError", "Network error")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "RiderAppStudentProject");
                return headers;
            }
        };

        queue.add(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}