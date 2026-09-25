package com.example.riderapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private ImageView btnMenuToggle;
    private TextView tvActiveStatusLabel, tvCompletedCount, tvRiderName, tvProfileInitial, tvOrdersFeedTitle;
    private RecyclerView rvOrdersFeed;
    private BottomNavigationView bottomNavigationView;
    private FrameLayout loadingContainer;
    private LinearLayout mainContentLayout;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DashboardOrdersAdapter adapter;
    private List<AllOrdersActivity.OrderModel> orderList;

    private boolean isProfileLoaded = false;
    private boolean isOrdersLoaded = false;
    private String riderCity = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI Views
        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenuToggle = findViewById(R.id.btnMenuToggle);
        tvActiveStatusLabel = findViewById(R.id.tvActiveStatusLabel);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        rvOrdersFeed = findViewById(R.id.rvOrdersFeed);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        loadingContainer = findViewById(R.id.loadingContainer);
        mainContentLayout = findViewById(R.id.mainContentLayout);

        tvRiderName = findViewById(R.id.tvRiderName);
        tvProfileInitial = findViewById(R.id.tvProfileInitial);
        tvOrdersFeedTitle = findViewById(R.id.tvOrdersFeedTitle);

        // Setup RecyclerView
        rvOrdersFeed.setLayoutManager(new LinearLayoutManager(this));
        orderList = new ArrayList<>();
        adapter = new DashboardOrdersAdapter(orderList);
        rvOrdersFeed.setAdapter(adapter);

        // Fetch Data
        fetchRiderProfile();

        // Open Sidebar Drawer on clicking the 3 lines
        btnMenuToggle.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Bind Sidebar Header / Menu clicks from nav_header_drawer layout
        View navView = findViewById(R.id.navigationView);

        TextView menuDashboard = navView.findViewById(R.id.menuDashboard);
        TextView menuOrderProgress = navView.findViewById(R.id.menuOrderProgress);
        TextView menuShippingAddresses = navView.findViewById(R.id.menuShippingAddresses);
        TextView menuSettings = navView.findViewById(R.id.menuSettings);
        TextView menuHelpSupport = navView.findViewById(R.id.menuHelpSupport);
        Button btnDrawerLogout = navView.findViewById(R.id.btnDrawerLogout);

        if (menuDashboard != null) {
            menuDashboard.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
        }
        if (menuOrderProgress != null) {
            menuOrderProgress.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, AllOrdersActivity.class));
            });
        }
        if (menuShippingAddresses != null) {
            menuShippingAddresses.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                Toast.makeText(this, "Shipping Addresses coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (menuSettings != null) {
            menuSettings.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (menuHelpSupport != null) {
            menuHelpSupport.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                Toast.makeText(this, "Help & Support coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (btnDrawerLogout != null) {
            btnDrawerLogout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showLogoutConfirmationDialog();
            });
        }

        // Bottom Navigation handling
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                findViewById(R.id.mainContentLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.fragmentContainer).setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, AllOrdersActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                findViewById(R.id.mainContentLayout).setVisibility(View.GONE);
                findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new ProfileFragment())
                        .commit();
                return true;
            }
            return false;
        });
    }

    private void showLogoutConfirmationDialog() {
        android.content.Context context = new android.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog_Alert);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out of your rider account?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Logout", (dialog, which) -> {
                    // 1. Sign out from Firebase
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();

                    // 2. Redirect to Login activity and clear back stack
                    android.content.Intent intent = new android.content.Intent(DashboardActivity.this, LoginActivity.class);
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void checkAndHideLoader() {
        if (isProfileLoaded && isOrdersLoaded) {
            loadingContainer.setVisibility(View.GONE);
            mainContentLayout.setVisibility(View.VISIBLE);
            bottomNavigationView.setVisibility(View.VISIBLE);
        }
    }

    private void fetchRiderProfile() {
        if (mAuth.getCurrentUser() == null) {
            isProfileLoaded = true;
            fetchDashboardOrders();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("riders").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        riderCity = documentSnapshot.getString("city");

                        if (fullName != null && !fullName.isEmpty()) {
                            tvRiderName.setText(fullName);
                            if (tvProfileInitial != null) {
                                tvProfileInitial.setText(String.valueOf(fullName.charAt(0)).toUpperCase());
                            }

                            // Also update sidebar header name/email
                            View navView = findViewById(R.id.navigationView);
                            TextView tvDrawerName = navView.findViewById(R.id.navDrawerName);
                            TextView tvDrawerEmail = navView.findViewById(R.id.navDrawerEmail);
                            TextView tvDrawerInitial = navView.findViewById(R.id.navDrawerProfileInitial);
                            if (tvDrawerName != null) tvDrawerName.setText(fullName);
                            if (tvDrawerEmail != null && mAuth.getCurrentUser() != null) {
                                tvDrawerEmail.setText(mAuth.getCurrentUser().getEmail());
                            }
                            if (tvDrawerInitial != null) tvDrawerInitial.setText(String.valueOf(fullName.charAt(0)).toUpperCase());
                        }

                        if (riderCity != null && !riderCity.trim().isEmpty()) {
                            if (tvOrdersFeedTitle != null) {
                                tvOrdersFeedTitle.setText("Available Orders (" + riderCity.trim() + ")");
                            }
                            if (tvActiveStatusLabel != null) {
                                tvActiveStatusLabel.setText("Active in " + riderCity.trim());
                            }
                        }
                    }
                    isProfileLoaded = true;
                    fetchDashboardOrders();
                })
                .addOnFailureListener(e -> {
                    isProfileLoaded = true;
                    fetchDashboardOrders();
                });
    }

    private void fetchDashboardOrders() {
        db.collectionGroup("user_orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        isOrdersLoaded = true;
                        checkAndHideLoader();
                        return;
                    }

                    if (value != null) {
                        orderList.clear();
                        int completedCount = 0;

                        for (DocumentSnapshot doc : value.getDocuments()) {
                            AllOrdersActivity.OrderModel order = doc.toObject(AllOrdersActivity.OrderModel.class);
                            if (order != null) {
                                // Filter by rider's city if riderCity is set
                                String orderCity = order.getCity();
                                if (riderCity != null && !riderCity.trim().isEmpty()) {
                                    if (orderCity == null || !orderCity.trim().equalsIgnoreCase(riderCity.trim())) {
                                        continue; // Skip order if city doesn't match rider's city
                                    }
                                }

                                order.setOrderId(doc.getId());
                                order.setDocumentPath(doc.getReference().getPath());
                                orderList.add(order);

                                if ("Delivered".equalsIgnoreCase(order.getStatus())) {
                                    completedCount++;
                                }
                            }
                        }
                        tvCompletedCount.setText(String.valueOf(completedCount));
                        adapter.notifyDataSetChanged();
                    }

                    isOrdersLoaded = true;
                    checkAndHideLoader();
                });
    }

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