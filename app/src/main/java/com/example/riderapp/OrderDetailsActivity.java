package com.example.riderapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailsActivity extends AppCompatActivity {

    private TextView tvOrderId, tvStatus, tvCustomerName, tvAddress, tvCityCountry, tvOrderTime, tvItemsList, tvTotal;
    private Button btnSeeLocation, btnAcceptDelivery;
    private String documentPath;

    private String customerAddress = "";
    private String customerCity = "";
    private double customerLat = 0.0;
    private double customerLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        tvOrderId = findViewById(R.id.tvDetailOrderId);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvCustomerName = findViewById(R.id.tvDetailCustomerName);
        tvAddress = findViewById(R.id.tvDetailAddress);
        tvCityCountry = findViewById(R.id.tvDetailCityCountry);
        tvOrderTime = findViewById(R.id.tvDetailOrderTime);
        tvItemsList = findViewById(R.id.tvDetailItemsList);
        tvTotal = findViewById(R.id.tvDetailTotal);

        btnSeeLocation = findViewById(R.id.btnSeeLocation);
        btnAcceptDelivery = findViewById(R.id.btnAcceptDelivery);

        documentPath = getIntent().getStringExtra("documentPath");

        if (documentPath != null) {
            fetchFullOrderDetailsFromFirestore(documentPath);
        } else {
            Toast.makeText(this, "Error: Invalid order path", Toast.LENGTH_SHORT).show();
        }

        // 1. See Location Button: Open map activity directly
        btnSeeLocation.setOnClickListener(v -> {
            if (customerLat != 0.0 && customerLng != 0.0) {
                Intent intent = new Intent(OrderDetailsActivity.this, DeliveryMapActivity.class);
                intent.putExtra("customerLat", customerLat);
                intent.putExtra("customerLng", customerLng);
                intent.putExtra("documentPath", documentPath);
                intent.putExtra("customerAddress", customerAddress);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Customer coordinates missing in database", Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Accept Delivery Button: Updates status to "Out for Delivery" via Vercel Backend
        btnAcceptDelivery.setOnClickListener(v -> {
            if (documentPath != null && !documentPath.isEmpty()) {
                updateOrderStatusViaVercel(documentPath, "Out for Delivery");
            } else {
                Toast.makeText(this, "Invalid document reference", Toast.LENGTH_SHORT).show();
            }
        });
    }

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
                response -> {
                    Toast.makeText(this, "Delivery Accepted! Status: Out for Delivery", Toast.LENGTH_SHORT).show();
                    tvStatus.setText("Status: " + newStatus);

                    // Lock down button once accepted successfully
                    btnAcceptDelivery.setEnabled(false);
                    btnAcceptDelivery.setText("Accepted");

                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String errorBody = new String(error.networkResponse.data);
                        Log.e("VercelAPI", "Server Error: " + errorBody);
                    }
                    Toast.makeText(this, "Failed to update order status", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer 7f3a9c2e1b8d4f6a0e5c7b9d2a4f6e8c1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f2a");
                return headers;
            }
        };

        queue.add(request);
    }

    private void fetchFullOrderDetailsFromFirestore(String path) {
        FirebaseFirestore.getInstance().document(path).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !doc.exists()) {
                return;
            }

            String orderId = doc.getId();
            String fullName = doc.getString("fullName");

            customerAddress = doc.getString("address");
            customerCity = doc.getString("city");

            String country = doc.getString("country");
            String status = doc.getString("status");
            Double total = doc.getDouble("total");
            Timestamp createdAt = doc.getTimestamp("createdAt");
            customerLat = doc.getDouble("latitude") != null ? doc.getDouble("latitude") : 0.0;
            customerLng = doc.getDouble("longitude") != null ? doc.getDouble("longitude") : 0.0;

            if (tvOrderId != null) tvOrderId.setText("Order ID: #" + orderId);
            if (tvStatus != null) tvStatus.setText("Status: " + (status != null ? status : "Pending"));
            if (tvCustomerName != null) tvCustomerName.setText(fullName != null ? fullName : "N/A");
            if (tvAddress != null) tvAddress.setText(customerAddress != null ? customerAddress : "Not provided");
            if (tvCityCountry != null) tvCityCountry.setText((customerCity != null ? customerCity : "") + (country != null ? ", " + country : ""));
            if (tvTotal != null) tvTotal.setText("Total Amount: PKR " + (total != null ? total : 0.0));

            // Check if the order is already out for delivery or accepted
            if (status != null && (status.equalsIgnoreCase("Out for Delivery") || status.equalsIgnoreCase("Accepted"))) {
                btnAcceptDelivery.setEnabled(false);
                btnAcceptDelivery.setText("Accepted");
            }

            if (tvOrderTime != null) {
                if (createdAt != null) {
                    Date date = createdAt.toDate();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
                    tvOrderTime.setText("Date: " + sdf.format(date));
                } else {
                    tvOrderTime.setText("Date: N/A");
                }
            }

            // Parse items list format
            List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get("items");
            StringBuilder fullDetailsBuilder = new StringBuilder();

            if (items != null && !items.isEmpty()) {
                for (int i = 0; i < items.size(); i++) {
                    Map<String, Object> itemMap = items.get(i);
                    String itemName = (String) itemMap.get("name");

                    Object qtyObj = itemMap.get("quantity");
                    if (qtyObj == null) qtyObj = itemMap.get("qty");
                    long quantity = (qtyObj instanceof Number) ? ((Number) qtyObj).longValue() : 1;

                    Object priceObj = itemMap.get("price");
                    double price = (priceObj instanceof Number) ? ((Number) priceObj).doubleValue() : 0.0;

                    fullDetailsBuilder.append("• ").append(itemName != null ? itemName : "Product")
                            .append("\n    Qty: ").append(quantity)
                            .append(" | Price: PKR ").append(price);

                    Object partsObj = itemMap.get("parts");
                    if (partsObj instanceof List) {
                        List<Map<String, Object>> partsList = (List<Map<String, Object>>) partsObj;
                        if (!partsList.isEmpty()) {
                            for (Map<String, Object> partMap : partsList) {
                                String partName = (String) partMap.get("partName");
                                Object partQtyObj = partMap.get("quantity");
                                if (partQtyObj == null) partQtyObj = partMap.get("qty");
                                long partQty = (partQtyObj instanceof Number) ? ((Number) partQtyObj).longValue() : 1;

                                fullDetailsBuilder.append("\n        └─ ").append(partName != null ? partName : "N/A")
                                        .append(" (x").append(partQty).append(")");
                            }
                        }
                    }

                    if (i < items.size() - 1) {
                        fullDetailsBuilder.append("\n\n");
                    }
                }
            } else {
                fullDetailsBuilder.append("No items found for this order.");
            }

            if (tvItemsList != null) {
                tvItemsList.setText(fullDetailsBuilder.toString());
            }
        });
    }
}