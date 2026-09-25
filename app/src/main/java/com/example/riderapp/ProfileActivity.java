package com.example.riderapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvFullName, tvEmail, btnEdit;
    private ImageView imgProfile;

    // Field view holders
    private View layoutFullName, layoutEmail, layoutPhone, layoutCnic, layoutCountry, layoutState, layoutCity, layoutBikeModel, layoutBikeNumber;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind main views
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        btnEdit = findViewById(R.id.btnEdit);
        imgProfile = findViewById(R.id.imgProfile);

        // Bind custom row layouts
        layoutFullName = findViewById(R.id.layoutFullName);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutCnic = findViewById(R.id.layoutCnic);
        layoutCountry = findViewById(R.id.layoutCountry);
        layoutState = findViewById(R.id.layoutState);
        layoutCity = findViewById(R.id.layoutCity);
        layoutBikeModel = findViewById(R.id.layoutBikeModel);
        layoutBikeNumber = findViewById(R.id.layoutBikeNumber);

        // Setup individual row labels, icons, and placeholder values
        setupRow(layoutFullName, android.R.drawable.ic_menu_myplaces, "Full Name", "Loading...");
        setupRow(layoutEmail, android.R.drawable.ic_dialog_email, "Email", "Loading...");
        setupRow(layoutPhone, android.R.drawable.ic_menu_call, "Primary Phone", "Loading...");
        setupRow(layoutCnic, android.R.drawable.ic_menu_info_details, "CNIC", "Loading...");
        setupRow(layoutCountry, android.R.drawable.ic_menu_compass, "Country", "Loading...");
        setupRow(layoutState, android.R.drawable.ic_menu_mapmode, "State", "Loading...");
        setupRow(layoutCity, android.R.drawable.ic_menu_mylocation, "City", "Loading...");
        setupRow(layoutBikeModel, android.R.drawable.ic_menu_manage, "Bike Model", "Loading...");
        setupRow(layoutBikeNumber, android.R.drawable.ic_menu_agenda, "Bike Number", "Loading...");

        // Fetch dynamic data from Firestore database
        loadUserProfileData();

        // Edit button click listener
        btnEdit.setOnClickListener(v -> {
            Toast.makeText(ProfileActivity.this, "Edit Profile Clicked", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRow(View rowView, int iconResId, String label, String defaultValue) {
        ImageView ivIcon = rowView.findViewById(R.id.ivFieldIcon);
        TextView tvLabel = rowView.findViewById(R.id.tvFieldLabel);
        TextView tvValue = rowView.findViewById(R.id.tvFieldValue);

        ivIcon.setImageResource(iconResId);
        tvLabel.setText(label);
        tvValue.setText(defaultValue);
    }

    private void setRowValue(View rowView, String value) {
        TextView tvValue = rowView.findViewById(R.id.tvFieldValue);
        if (value != null && !value.isEmpty()) {
            tvValue.setText(value);
        } else {
            tvValue.setText("Not set");
        }
    }

    private void loadUserProfileData() {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "cXlrxJcveVWWHKKDvtaF5Vfjj1u03";

        DocumentReference docRef = db.collection("riders").document(currentUserId);
        docRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String fullName = documentSnapshot.getString("fullName");
                String email = documentSnapshot.getString("email");
                String phone = documentSnapshot.getString("phone");
                String cnic = documentSnapshot.getString("cnic");
                String country = documentSnapshot.getString("country");
                String state = documentSnapshot.getString("state");
                String city = documentSnapshot.getString("city");
                String bikeModel = documentSnapshot.getString("bikeModel");
                String bikeNumber = documentSnapshot.getString("bikeNumber");

                // Bind header values
                tvFullName.setText(fullName != null ? fullName : "User");
                tvEmail.setText(email != null ? email : "");

                // Bind row values directly into Account Information list
                setRowValue(layoutFullName, fullName);
                setRowValue(layoutEmail, email);
                setRowValue(layoutPhone, phone);
                setRowValue(layoutCnic, cnic);
                setRowValue(layoutCountry, country);
                setRowValue(layoutState, state);
                setRowValue(layoutCity, city);
                setRowValue(layoutBikeModel, bikeModel);
                setRowValue(layoutBikeNumber, bikeNumber);

            } else {
                Toast.makeText(ProfileActivity.this, "Profile data not found in database.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(ProfileActivity.this, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}