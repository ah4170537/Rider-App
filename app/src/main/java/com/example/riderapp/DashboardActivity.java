package com.example.riderapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private Button btnLogout;
    private TextView tvActiveStatusLabel, tvCompletedCount;
    private RecyclerView rvOrdersFeed;
    private BottomNavigationView bottomNavigationView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DashboardOrdersAdapter adapter;
    private List<AllOrdersActivity.OrderModel> orderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI Views
        btnLogout = findViewById(R.id.btnLogout);
        tvActiveStatusLabel = findViewById(R.id.tvActiveStatusLabel);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        rvOrdersFeed = findViewById(R.id.rvOrdersFeed);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // Setup RecyclerView
        rvOrdersFeed.setLayoutManager(new LinearLayoutManager(this));
        orderList = new ArrayList<>();
        adapter = new DashboardOrdersAdapter(orderList);
        rvOrdersFeed.setAdapter(adapter);

        // Handle Logout Click & Session Termination
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut(); // Clear Firebase Session
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // Redirect to Login and clear back stack so user can't press back to return here
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Bottom Navigation handling
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, AllOrdersActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profile section coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        // Fetch live orders into dashboard feed
        fetchDashboardOrders();
    }

    private void fetchDashboardOrders() {
        db.collectionGroup("user_orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (value != null) {
                        orderList.clear();
                        int completedCount = 0;

                        for (DocumentSnapshot doc : value.getDocuments()) {
                            AllOrdersActivity.OrderModel order = doc.toObject(AllOrdersActivity.OrderModel.class);
                            if (order != null) {
                                order.setOrderId(doc.getId());
                                order.setDocumentPath(doc.getReference().getPath());
                                orderList.add(order);

                                // Count completed ones for stats bar
                                if ("Delivered".equalsIgnoreCase(order.getStatus())) {
                                    completedCount++;
                                }
                            }
                        }
                        tvCompletedCount.setText(String.valueOf(completedCount));
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // Lightweight adapter specifically for the dashboard feed cards
    private static class DashboardOrdersAdapter extends RecyclerView.Adapter<DashboardOrdersAdapter.DashboardOrderViewHolder> {
        private final List<AllOrdersActivity.OrderModel> orders;

        public DashboardOrdersAdapter(List<AllOrdersActivity.OrderModel> orders) {
            this.orders = orders;
        }

        @NonNull
        @Override
        public DashboardOrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
            return new DashboardOrderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DashboardOrderViewHolder holder, int position) {
            AllOrdersActivity.OrderModel order = orders.get(position);

            holder.tvOrderId.setText("Order ID: #" + (order.getOrderId() != null ? order.getOrderId().toUpperCase() : ""));
            holder.tvCustomerName.setText("Customer: " + (order.getFullName() != null ? order.getFullName() : "Unknown"));
            holder.tvTotalAmount.setText("PKR " + order.getTotal());
            holder.tvOrderStatus.setText(order.getStatus() != null ? order.getStatus() : "Pending");

            List<Map<String, Object>> items = order.getItems();
            int itemCount = (items != null) ? items.size() : 0;
            holder.tvItemCount.setText(itemCount + " item(s) in this order");

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

        static class DashboardOrderViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvOrderStatus, tvCustomerName, tvItemCount, tvTotalAmount;

            public DashboardOrderViewHolder(@NonNull View itemView) {
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