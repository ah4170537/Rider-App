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

    // Navigation tracking variables
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

        // Initialize the Start Journey Button for in-app 3D navigation
        btnStartJourney = findViewById(R.id.btnStartJourney);
        btnStartJourney.setOnClickListener(v -> {
            if (customerAddressStr == null || customerAddressStr.isEmpty()) {
                Toast.makeText(this, "Destination address not available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (customerLatLng == null) {
                Toast.makeText(this, "Destination coordinates not available", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Call Vercel backend to update order status securely instead of local direct write
            if (documentPath != null && !documentPath.isEmpty()) {
                updateOrderStatusViaVercel(documentPath, "Out for Delivery");
            }

            // 2. Toggle journey state
            isJourneyStarted = true;
            btnStartJourney.setText("Navigating...");
            btnStartJourney.setEnabled(false); // Disable button once started
            Toast.makeText(this, "Delivery Started & 3D Navigation Active!", Toast.LENGTH_SHORT).show();

            // 3. Instantly fetch route and lock camera into 3D navigation mode from current rider position
            if (riderMarker != null) {
                fetchRoadRouteFromOSRM(riderMarker.getPosition(), customerLatLng);
                startNavigationCamera(riderMarker.getPosition(), 0);
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        // Optional: Enable UI settings for a clean look
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

        // 1. Check if we received valid coordinates from the database
        double lat = getIntent().getDoubleExtra("customerLat", 0.0);
        double lng = getIntent().getDoubleExtra("customerLat", 0.0); // (Wait, make sure it's customerLng!)

        // Let's write it cleanly:
        double intentLat = getIntent().getDoubleExtra("customerLat", 0.0);
        double intentLng = getIntent().getDoubleExtra("customerLng", 0.0);

        if (intentLat != 0.0 && intentLng != 0.0) {
            // SUCCESS: Use exact coordinates from Firestore database
            customerLatLng = new LatLng(intentLat, intentLng);

            mMap.addMarker(new MarkerOptions()
                    .position(customerLatLng)
                    .title("Customer Destination (GPS)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            proceedWithRiderLocationAndRoute();

        } else {
            // FALLBACK: Coordinates are missing in DB, fallback to Geocoder using address text string
            Toast.makeText(this, "GPS coordinates missing. Using text address fallback...", Toast.LENGTH_SHORT).show(); // Note: Toast.makeText

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
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))); // Orange marker to show it's a fallback
                    }
                } catch (IOException e) {
                    Log.e("GeocodeError", e.getMessage());
                }
            }

            proceedWithRiderLocationAndRoute();
        }
    }

    // Helper method to keep things clean
    private void proceedWithRiderLocationAndRoute() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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

        // Outer white ring / border paint
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setAntiAlias(true);
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, borderPaint);

        // Inner bright blue dot paint (Uber blue style)
        Paint innerPaint = new Paint();
        innerPaint.setColor(Color.parseColor("#2196F3")); // Vibrant Blue
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setAntiAlias(true);
        canvas.drawCircle(width / 2f, height / 2f, width / 2.6f, innerPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    // Continuously updates rider location, sends to Firestore, and updates 3D map view if journey started
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

                    // Update map marker position smoothly if map is loaded
                    if (riderMarker != null) {
                        riderMarker.setPosition(newPos);
                    }

                    // If user clicked Start Journey, update path dynamically and apply 3D Camera view
                    if (isJourneyStarted && customerLatLng != null) {
                        fetchRoadRouteFromOSRM(newPos, customerLatLng);
                        startNavigationCamera(newPos, bearing);
                    }

                    // Push live GPS coordinates ONLY to this specific order's tracking path in Firestore
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

    // Helper method to lock camera into 3D navigation mode
    private void startNavigationCamera(LatLng currentPos, float bearing) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(currentPos)      // Center map on rider
                .zoom(18f)               // Street level zoom
                .tilt(50f)               // 3D Tilt angle
                .bearing(bearing)        // Rotate map according to movement direction
                .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
    }

    // Helper method to securely update order status via your Vercel backend
    private void updateOrderStatusViaVercel(String docPath, String newStatus) {
        String url = "https://auth-app-backend-dcq81fg1c-xtreme-solutions-systems.vercel.app/api/update-order";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("documentPath", docPath);
            jsonBody.put("status", newStatus);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, jsonBody,
                response -> Log.d("VercelAPI", "Status updated successfully via backend"),
                error -> {
                    // Print the exact error message coming from Vercel's response body
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String errorBody = new String(error.networkResponse.data);
                        Log.e("VercelAPI", "Server Error Code: " + error.networkResponse.statusCode + " | Details: " + errorBody);
                    } else {
                        Log.e("VercelAPI", "Failed to update status via backend: " + error.toString());
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
//                 If your backend needs a custom API key or token, add it here:
                headers.put("Authorization", "Bearer 7f3a9c2e1b8d4f6a0e5c7b9d2a4f6e8c1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f2a");
                return headers;
            }
        };

        queue.add(request);
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
                                // Remove previous polyline so old path lines don't stack up
                                if (currentRoutePolyline != null) {
                                    currentRoutePolyline.remove();
                                }

                                // Add the updated route line in clean blue
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
        // Stop background location updates when leaving the activity to save battery
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}