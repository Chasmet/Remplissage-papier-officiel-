package com.chasmet.remplissagepapierofficiel;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class ProfileActivity extends Activity {
    public static final String PREFS = "profile";

    private EditText firstName, lastName, birthDate, birthPlace, address, postalCode, city, phone, email, otherId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        firstName = findViewById(R.id.etFirstName);
        lastName = findViewById(R.id.etLastName);
        birthDate = findViewById(R.id.etBirthDate);
        birthPlace = findViewById(R.id.etBirthPlace);
        address = findViewById(R.id.etAddress);
        postalCode = findViewById(R.id.etPostalCode);
        city = findViewById(R.id.etCity);
        phone = findViewById(R.id.etPhone);
        email = findViewById(R.id.etEmail);
        otherId = findViewById(R.id.etOtherId);

        loadProfile();
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfile());
    }

    private void loadProfile() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        firstName.setText(p.getString("firstName", ""));
        lastName.setText(p.getString("lastName", ""));
        birthDate.setText(p.getString("birthDate", ""));
        birthPlace.setText(p.getString("birthPlace", ""));
        address.setText(p.getString("address", ""));
        postalCode.setText(p.getString("postalCode", ""));
        city.setText(p.getString("city", ""));
        phone.setText(p.getString("phone", ""));
        email.setText(p.getString("email", ""));
        otherId.setText(p.getString("otherId", ""));
    }

    private void saveProfile() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("firstName", firstName.getText().toString().trim())
                .putString("lastName", lastName.getText().toString().trim())
                .putString("birthDate", birthDate.getText().toString().trim())
                .putString("birthPlace", birthPlace.getText().toString().trim())
                .putString("address", address.getText().toString().trim())
                .putString("postalCode", postalCode.getText().toString().trim())
                .putString("city", city.getText().toString().trim())
                .putString("phone", phone.getText().toString().trim())
                .putString("email", email.getText().toString().trim())
                .putString("otherId", otherId.getText().toString().trim())
                .apply();
        Toast.makeText(this, "Profil enregistré", Toast.LENGTH_SHORT).show();
    }
}
