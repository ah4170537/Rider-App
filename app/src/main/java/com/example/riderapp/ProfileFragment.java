package com.example.riderapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private TextView tvFullName, tvEmail, btnEdit, tvProfileInitial;
    private FrameLayout loadingContainer;
    private ScrollView profileContentLayout;

    // Field view holders
    private View layoutFullName, layoutEmail, layoutPhone, layoutCnic, layoutCountry, layoutState, layoutCity, layoutBikeModel, layoutBikeNumber;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_profile, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind main views
        tvFullName = view.findViewById(R.id.tvFullName);
        tvEmail = view.findViewById(R.id.tvEmail);
        btnEdit = view.findViewById(R.id.btnEdit);
        tvProfileInitial = view.findViewById(R.id.tvProfileInitial);
        loadingContainer = view.findViewById(R.id.loadingContainer);
        profileContentLayout = view.findViewById(R.id.profileContentLayout);

        // Bind custom row layouts
        layoutFullName = view.findViewById(R.id.layoutFullName);
        layoutEmail = view.findViewById(R.id.layoutEmail);
        layoutPhone = view.findViewById(R.id.layoutPhone);
        layoutCnic = view.findViewById(R.id.layoutCnic);
        layoutCountry = view.findViewById(R.id.layoutCountry);
        layoutState = view.findViewById(R.id.layoutState);
        layoutCity = view.findViewById(R.id.layoutCity);
        layoutBikeModel = view.findViewById(R.id.layoutBikeModel);
        layoutBikeNumber = view.findViewById(R.id.layoutBikeNumber);

        // Setup row labels and icons (blank default values instead of "Loading...")
        setupRow(layoutFullName, android.R.drawable.ic_menu_myplaces, "Full Name", "");
        setupRow(layoutEmail, android.R.drawable.ic_dialog_email, "Email", "");
        setupRow(layoutPhone, android.R.drawable.ic_menu_call, "Primary Phone", "");
        setupRow(layoutCnic, android.R.drawable.ic_menu_info_details, "CNIC", "");
        setupRow(layoutCountry, android.R.drawable.ic_menu_compass, "Country", "");
        setupRow(layoutState, android.R.drawable.ic_menu_mapmode, "State", "");
        setupRow(layoutCity, android.R.drawable.ic_menu_mylocation, "City", "");
        setupRow(layoutBikeModel, android.R.drawable.ic_menu_manage, "Bike Model", "");
        setupRow(layoutBikeNumber, android.R.drawable.ic_menu_agenda, "Bike Number", "");

        // Load data
        loadUserProfileData();

        btnEdit.setOnClickListener(v -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Edit Profile Clicked", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
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
        if (mAuth.getCurrentUser() == null) {
            hideLoader();
            return;
        }
        String currentUserId = mAuth.getCurrentUser().getUid();

        DocumentReference docRef = db.collection("riders").document(currentUserId);
        docRef.addSnapshotListener((documentSnapshot, error) -> {
            if (!isAdded()) return;

            if (error != null) {
                hideLoader();
                return;
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                String fullName = documentSnapshot.getString("fullName");
                String email = documentSnapshot.getString("email");
                String phone = documentSnapshot.getString("phone");
                String cnic = documentSnapshot.getString("cnic");
                String country = documentSnapshot.getString("country");
                String state = documentSnapshot.getString("state");
                String city = documentSnapshot.getString("city");
                String bikeModel = documentSnapshot.getString("bikeModel");
                String bikeNumber = documentSnapshot.getString("bikeNumber");

                tvFullName.setText(fullName != null ? fullName : "User");
                tvEmail.setText(email != null ? email : "");

                // Set circular avatar initial dynamically
                if (fullName != null && !fullName.isEmpty() && tvProfileInitial != null) {
                    tvProfileInitial.setText(String.valueOf(fullName.charAt(0)).toUpperCase());
                }

                setRowValue(layoutFullName, fullName);
                setRowValue(layoutEmail, email);
                setRowValue(layoutPhone, phone);
                setRowValue(layoutCnic, cnic);
                setRowValue(layoutCountry, country);
                setRowValue(layoutState, state);
                setRowValue(layoutCity, city);
                setRowValue(layoutBikeModel, bikeModel);
                setRowValue(layoutBikeNumber, bikeNumber);
            }
            hideLoader();
        });
    }

    private void hideLoader() {
        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.GONE);
        }
        if (profileContentLayout != null) {
            profileContentLayout.setVisibility(View.VISIBLE);
        }
    }
}
