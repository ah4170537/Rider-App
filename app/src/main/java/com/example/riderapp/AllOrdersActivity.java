package com.example.riderapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AllOrdersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FirebaseFirestore db;
    private OrdersAdapter adapter;
    private List<OrderModel> orderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_orders);

        recyclerView = findViewById(R.id.recyclerViewOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        orderList = new ArrayList<>();
        adapter = new OrdersAdapter(orderList);
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        fetchOrders();
    }

    private void fetchOrders() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            listenToOrders(null);
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        db.collection("riders").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String riderCity = documentSnapshot.exists() ? documentSnapshot.getString("city") : null;
                    listenToOrders(riderCity);
                })
                .addOnFailureListener(e -> listenToOrders(null));
    }

    private void listenToOrders(String riderCity) {
        db.collectionGroup("user_orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        android.util.Log.e("FirestoreError", "Failed to fetch orders: " + error.getMessage(), error);
                        return;
                    }

                    if (value != null) {
                        orderList.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            OrderModel order = doc.toObject(OrderModel.class);
                            if (order != null) {
                                String orderCity = order.getCity();
                                if (riderCity != null && !riderCity.trim().isEmpty()) {
                                    if (orderCity == null || !orderCity.trim().equalsIgnoreCase(riderCity.trim())) {
                                        continue; // Skip order if city doesn't match rider's city
                                    }
                                }
                                order.setOrderId(doc.getId());
                                order.setDocumentPath(doc.getReference().getPath());
                                orderList.add(order);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // Order Model Class - Field name matches Firestore "fullName" exactly
    public static class OrderModel {
        private String orderId;
        private String documentPath;
        private String fullName; // Matches Firestore field name directly

        private double total;
        private String status;
        private String address;
        private String city;
        private String country;
        private Timestamp createdAt;
        private List<Map<String, Object>> items;

        public OrderModel() {} // Required for Firestore

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getDocumentPath() { return documentPath; }
        public void setDocumentPath(String documentPath) { this.documentPath = documentPath; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public double getTotal() { return total; }
        public String getStatus() { return status; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getCountry() { return country; }
        public Timestamp getCreatedAt() { return createdAt; }
        public List<Map<String, Object>> getItems() { return items; }
    }

    // RecyclerView Adapter
    private static class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {
        private final List<OrderModel> orders;

        public OrdersAdapter(List<OrderModel> orders) {
            this.orders = orders;
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
            return new OrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
            OrderModel order = orders.get(position);

            holder.tvOrderId.setText("Order ID: #" + (order.getOrderId() != null ? order.getOrderId().toUpperCase() : ""));
            holder.tvCustomerName.setText("Customer: " + (order.getFullName() != null ? order.getFullName() : "Unknown"));

            // Build item previews for card view
            StringBuilder itemsDisplay = new StringBuilder();
            List<Map<String, Object>> items = order.getItems();

            if (items != null && !items.isEmpty()) {
                for (int i = 0; i < items.size(); i++) {
                    Map<String, Object> itemMap = items.get(i);
                    String itemName = (String) itemMap.get("name");

                    Object qtyObj = itemMap.get("quantity");
                    if (qtyObj == null) qtyObj = itemMap.get("qty");
                    long itemQuantity = (qtyObj instanceof Number) ? ((Number) qtyObj).longValue() : 1;

                    Object priceObj = itemMap.get("price");
                    double itemPrice = (priceObj instanceof Number) ? ((Number) priceObj).doubleValue() : 0.0;

                    if (itemName != null) {
                        itemsDisplay.append("• ").append(itemName)
                                .append("\n    Qty: ").append(itemQuantity)
                                .append(" | Price: PKR ").append(itemPrice);

                        Object partsObj = itemMap.get("parts");
                        if (partsObj instanceof List) {
                            List<Map<String, Object>> partsList = (List<Map<String, Object>>) partsObj;
                            if (!partsList.isEmpty()) {
                                for (Map<String, Object> partMap : partsList) {
                                    String partName = (String) partMap.get("partName");
                                    Object partQtyObj = partMap.get("quantity");
                                    if (partQtyObj == null) partQtyObj = partMap.get("qty");
                                    long partQuantity = (partQtyObj instanceof Number) ? ((Number) partQtyObj).longValue() : 1;

                                    itemsDisplay.append("\n        └─ Part: ").append(partName != null ? partName : "N/A")
                                            .append(" (Qty: ").append(partQuantity).append(")");
                                }
                            }
                        }

                        if (i < items.size() - 1) {
                            itemsDisplay.append("\n\n");
                        }
                    }
                }
            } else {
                itemsDisplay.append("No items found");
            }

            holder.tvItemCount.setText(itemsDisplay.toString());
            holder.tvTotalAmount.setText("PKR " + order.getTotal());
            holder.tvOrderStatus.setText(order.getStatus() != null ? order.getStatus() : "Pending");

            // Launch OrderDetailsActivity passing the Firestore document path
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), OrderDetailsActivity.class);
                intent.putExtra("documentPath", order.getDocumentPath());
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return orders.size();
        }

        static class OrderViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvOrderStatus, tvCustomerName, tvItemCount, tvTotalAmount;

            public OrderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tvOrderId);
                tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
                tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
                tvItemCount = itemView.findViewById(R.id.tvItemCount);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            }
        }
    }
}