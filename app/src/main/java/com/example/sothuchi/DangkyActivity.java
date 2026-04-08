package com.example.sothuchi;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DangkyActivity extends AppCompatActivity {

    // 1. Khai báo bổ sung thêm etFullName và etPhone
    private TextInputEditText etFullName, etEmail, etPhone, etPassword;
    private Button btnRegister, btnNavLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dangky);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 2. Ánh xạ ĐẦY ĐỦ các View từ XML
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnNavLogin = findViewById(R.id.btnNavLogin);

        btnRegister.setOnClickListener(v -> registerUser());
        btnNavLogin.setOnClickListener(v -> finish());
    }

    private void saveUserToFirestore(String userId, String fullName, String email, String phone, String password) {
        Map<String, Object> userDetail = new HashMap<>();
        userDetail.put("fullName", fullName);
        userDetail.put("email", email);
        userDetail.put("phone", phone);
        userDetail.put("password", password);
        userDetail.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("users").document(userId)
                .set(userDetail, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(DangkyActivity.this, "Đăng ký thành công!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DangkyActivity.this, "Lỗi lưu thông tin: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void registerUser() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ tất cả thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        DatabaseHelper dbLocal = new DatabaseHelper(this);
                        
                        // ĐẢM BẢO NGƯỜI MỚI CÓ DỮ LIỆU CỤC BỘ TRỐNG (Tránh ghi đè từ session trước)
                        dbLocal.clearAllTransactions();
                        
                        // 1. Lưu SQLite local
                        dbLocal.saveUserLocal(fullName, email, phone, password);
                        
                        // 2. Lưu Firestore (Cô lập UID)
                        saveUserToFirestore(userId, fullName, email, phone, password);
                    } else {
                        Toast.makeText(DangkyActivity.this, "Lỗi: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}