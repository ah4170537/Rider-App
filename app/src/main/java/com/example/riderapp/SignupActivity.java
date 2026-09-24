package com.example.riderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignup;
    private TextView tvGoToLogin;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etName = findViewById(R.id.etSignupName);
        etEmail = findViewById(R.id.etSignupEmail);
        etPassword = findViewById(R.id.etSignupPassword);
        etConfirmPassword = findViewById(R.id.etSignupConfirmPassword);
        btnSignup = findViewById(R.id.btnSignUp);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        progressBar = findViewById(R.id.signupProgressBar);

        progressBar.setVisibility(View.GONE);

        btnSignup.setOnClickListener(v -> registerRider());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerRider() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Check if any field is empty
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate password length
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if password and confirm password match
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSignup.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        // Save Rider Profile in Firestore 'riders' collection
                        Map<String, Object> riderData = new HashMap<>();
                        riderData.put("uid", uid);
                        riderData.put("fullName", name);
                        riderData.put("email", email);
                        riderData.put("role", "rider");
                        riderData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                        db.collection("riders").document(uid)
                                .set(riderData)
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(SignupActivity.this, "Rider Account Created Successfully!", Toast.LENGTH_SHORT).show();

                                    // Open Dashboard & Clear Stack
                                    Intent intent = new Intent(SignupActivity.this, DashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    btnSignup.setEnabled(true);
                                    Toast.makeText(SignupActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });

                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnSignup.setEnabled(true);
                        Toast.makeText(SignupActivity.this, "Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}