package com.example.riderapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailsActivity extends AppCompatActivity {

    private TextView tvOrderId, tvStatus, tvCustomerName, tvAddress, tvCityCountry, tvOrderTime, tvItemsList, tvTotal;
    private Button btnStartDelivery;
    private String documentPath;

    // Class-level variables so updateOrderStatus can access them
    private String customerAddress = "";
    private String customerCity = "";
    private double customerLat = 0.0;
    private double customerLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        // Initialize views safely
        tvOrderId = findViewById(R.id.tvDetailOrderId);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvCustomerName = findViewById(R.id.tvDetailCustomerName);
        tvAddress = findViewById(R.id.tvDetailAddress);
        tvCityCountry = findViewById(R.id.tvDetailCityCountry);
        tvOrderTime = findViewById(R.id.tvDetailOrderTime);
        tvItemsList = findViewById(R.id.tvDetailItemsList);
        tvTotal = findViewById(R.id.tvDetailTotal);
        btnStartDelivery = findViewById(R.id.btnStartDelivery);

        documentPath = getIntent().getStringExtra("documentPath");

        if (documentPath != null) {
            fetchFullOrderDetailsFromFirestore(documentPath);
        } else {
            Toast.makeText(this, "Error: Invalid order path", Toast.LENGTH_SHORT).show();
        }

        btnStartDelivery.setOnClickListener(v -> {
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
    }

    private void fetchFullOrderDetailsFromFirestore(String path) {
        FirebaseFirestore.getInstance().document(path).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String orderId = doc.getId();
                        String fullName = doc.getString("fullName");

                        // Assign values to class-level variables
                        customerAddress = doc.getString("address");
                        customerCity = doc.getString("city");

                        String country = doc.getString("country");
                        String status = doc.getString("status");
                        Double total = doc.getDouble("total");
                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        customerLat = doc.getDouble("latitude") != null ? doc.getDouble("latitude") : 0.0;
                        customerLng = doc.getDouble("longitude") != null ? doc.getDouble("longitude") : 0.0;

                        // Safely set text views
                        if (tvOrderId != null) tvOrderId.setText("Order ID: #" + orderId.toUpperCase());
                        if (tvStatus != null) tvStatus.setText("Status: " + (status != null ? status : "Pending"));
                        if (tvCustomerName != null) tvCustomerName.setText("Customer: " + (fullName != null ? fullName : "N/A"));
                        if (tvAddress != null) tvAddress.setText("Address: " + (customerAddress != null ? customerAddress : "Not provided"));
                        if (tvCityCountry != null) tvCityCountry.setText("City / Country: " + (customerCity != null ? customerCity : "") + (country != null ? ", " + country : ""));
                        if (tvTotal != null) tvTotal.setText("Total Amount: PKR " + (total != null ? total : 0.0));

                        if (tvOrderTime != null) {
                            if (createdAt != null) {
                                Date date = createdAt.toDate();
                                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                                tvOrderTime.setText("Order Time: " + sdf.format(date));
                            } else {
                                tvOrderTime.setText("Order Time: N/A");
                            }
                        }

                        // Parse items list
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

                                            fullDetailsBuilder.append("\n        └─ Part: ").append(partName != null ? partName : "N/A")
                                                    .append(" (Qty: ").append(partQty).append(")");
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

                    } else {
                        Toast.makeText(this, "Order document not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


}