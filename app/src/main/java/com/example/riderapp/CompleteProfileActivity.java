package com.example.riderapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.hbb20.CountryCodePicker;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompleteProfileActivity extends AppCompatActivity {

    private static final String DATA_FILE = "countries_states_cities.json";

    private EditText etPhone, etCnic, etBikeNo, etBikeModel;
    private Spinner spinnerCountry, spinnerState, spinnerCity;
    private CountryCodePicker ccpCountryCode;
    private Button btnSaveProfile;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String intentName, intentEmail, intentPassword;

    // Location data loaded from assets
    private static class StateData {
        String name;
        List<String> cities = new ArrayList<>();
    }

    private static class CountryData {
        String name;
        List<StateData> states = new ArrayList<>();
    }

    private final List<CountryData> countryList = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<StateData> currentStates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Retrieve credentials passed from SignupActivity
        intentName = getIntent().getStringExtra("name");
        intentEmail = getIntent().getStringExtra("email");
        intentPassword = getIntent().getStringExtra("password");

        etPhone = findViewById(R.id.etProfilePhone);
        etCnic = findViewById(R.id.etProfileCnic);
        etBikeNo = findViewById(R.id.etProfileBikeNo);
        etBikeModel = findViewById(R.id.etProfileBikeModel);
        ccpCountryCode = findViewById(R.id.ccpCountryCode);
        spinnerCountry = findViewById(R.id.spinnerCountry);
        spinnerState = findViewById(R.id.spinnerState);
        spinnerCity = findViewById(R.id.spinnerCity);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        progressBar = findViewById(R.id.profileProgressBar);

        progressBar.setVisibility(View.GONE);
        setupSpinners();
        loadLocationData();

        btnSaveProfile.setOnClickListener(v -> registerAndSaveProfile());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void setupSpinners() {
        // Initial placeholders (replaced once the dataset finishes loading)
        setSpinnerItems(spinnerCountry, singleItem("Select Country"));
        setSpinnerItems(spinnerState, singleItem("Select State"));
        setSpinnerItems(spinnerCity, singleItem("Select City"));

        spinnerCountry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // position 0 is "Select Country"
                if (position > 0 && position - 1 < countryList.size()) {
                    updateStates(countryList.get(position - 1).states);
                } else {
                    updateStates(new ArrayList<>());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerState.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // position 0 is "Select State"
                if (position > 0 && position - 1 < currentStates.size()) {
                    updateCities(currentStates.get(position - 1).cities);
                } else {
                    updateCities(new ArrayList<>());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateStates(List<StateData> states) {
        currentStates = states;
        List<String> names = new ArrayList<>();
        names.add("Select State");
        for (StateData s : states) names.add(s.name);
        setSpinnerItems(spinnerState, names);
    }

    private void updateCities(List<String> cities) {
        List<String> names = new ArrayList<>();
        names.add("Select City");
        names.addAll(cities);
        setSpinnerItems(spinnerCity, names);
    }

    private void setSpinnerItems(Spinner spinner, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, items);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        spinner.setAdapter(adapter);
    }

    private List<String> singleItem(String text) {
        List<String> list = new ArrayList<>();
        list.add(text);
        return list;
    }

    // ---------- Dataset loading (background thread) ----------

    private void loadLocationData() {
        executor.execute(() -> {
            List<CountryData> loaded = new ArrayList<>();
            try (JsonReader reader = new JsonReader(
                    new InputStreamReader(getAssets().open(DATA_FILE), "UTF-8"))) {
                reader.beginArray();
                while (reader.hasNext()) {
                    loaded.add(readCountry(reader));
                }
                reader.endArray();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Failed to load location data: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
                return;
            }

            runOnUiThread(() -> {
                countryList.clear();
                countryList.addAll(loaded);
                List<String> names = new ArrayList<>();
                names.add("Select Country");
                for (CountryData c : countryList) names.add(c.name);
                setSpinnerItems(spinnerCountry, names);
            });
        });
    }

    private CountryData readCountry(JsonReader reader) throws Exception {
        CountryData country = new CountryData();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("name") && reader.peek() == JsonToken.STRING) {
                country.name = reader.nextString();
            } else if (key.equals("states") && reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                while (reader.hasNext()) {
                    country.states.add(readState(reader));
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return country;
    }

    private StateData readState(JsonReader reader) throws Exception {
        StateData state = new StateData();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("name") && reader.peek() == JsonToken.STRING) {
                state.name = reader.nextString();
            } else if (key.equals("cities") && reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                while (reader.hasNext()) {
                    state.cities.add(readCityName(reader));
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return state;
    }

    private String readCityName(JsonReader reader) throws Exception {
        String name = "";
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("name") && reader.peek() == JsonToken.STRING) {
                name = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return name;
    }

    // ---------- Registration (unchanged logic) ----------

    private void registerAndSaveProfile() {
        String code = ccpCountryCode.getSelectedCountryCodeWithPlus();
        String rawPhone = etPhone.getText().toString().trim();
        String cnic = etCnic.getText().toString().trim();
        String bikeNo = etBikeNo.getText().toString().trim();
        String bikeModel = etBikeModel.getText().toString().trim();
        String country = spinnerCountry.getSelectedItem().toString();
        String state = spinnerState.getSelectedItem().toString();
        String city = spinnerCity.getSelectedItem().toString();

        if (TextUtils.isEmpty(rawPhone) || TextUtils.isEmpty(cnic) || TextUtils.isEmpty(bikeNo) || TextUtils.isEmpty(bikeModel) ||
                country.startsWith("Select") || state.startsWith("Select") || city.startsWith("Select")) {
            Toast.makeText(this, "Please complete all fields correctly", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullPhoneNumber = code + rawPhone;

        progressBar.setVisibility(View.VISIBLE);
        btnSaveProfile.setEnabled(false);

        // Now create the Firebase Auth user account since all profile data is validated & ready
        mAuth.createUserWithEmailAndPassword(intentEmail, intentPassword)
                .addOnCompleteListener(this, authTask -> {
                    if (authTask.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        Map<String, Object> riderData = new HashMap<>();
                        riderData.put("uid", uid);
                        riderData.put("fullName", intentName);
                        riderData.put("email", intentEmail);
                        riderData.put("phone", fullPhoneNumber);
                        riderData.put("cnic", cnic);
                        riderData.put("bikeNumber", bikeNo);
                        riderData.put("bikeModel", bikeModel);
                        riderData.put("country", country);
                        riderData.put("state", state);
                        riderData.put("city", city);
                        riderData.put("role", "rider");
                        riderData.put("createdAt", FieldValue.serverTimestamp());

                        // Save full details to Firestore
                        db.collection("riders").document(uid)
                                .set(riderData)
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();

                                    Intent intent = new Intent(CompleteProfileActivity.this, DashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    btnSaveProfile.setEnabled(true);
                                    Toast.makeText(this, "Failed to save data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });

                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnSaveProfile.setEnabled(true);
                        Toast.makeText(this, "Registration Failed: " + authTask.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}