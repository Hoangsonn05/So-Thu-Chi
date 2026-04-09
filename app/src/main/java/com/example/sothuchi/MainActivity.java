package com.example.sothuchi;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isOfflineMode = getIntent().getBooleanExtra(SplashActivity.EXTRA_IS_OFFLINE_MODE, false);
        if (isOfflineMode) {
            Intent offlineIntent = new Intent(MainActivity.this, HamchinhActivity.class);
            offlineIntent.putExtra(SplashActivity.EXTRA_IS_OFFLINE_MODE, true);
            startActivity(offlineIntent);
            finish();
            return;
        }

        // Khởi tạo Firebase Auth và kiểm tra trạng thái đăng nhập
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            // Nếu đã đăng nhập, chuyển thẳng vào màn hình chính
            Intent intent = new Intent(MainActivity.this, HamchinhActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.dangnhap); // Load giao diện đăng nhập

        // Ánh xạ các View
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Nút Đăng nhập
        btnLogin.setOnClickListener(v -> loginUser());

        // Nút Đăng ký
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DangkyActivity.class);
            startActivity(intent);
        });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Đối chiếu Firebase
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                        // NẾU THÀNH CÔNG: Tải thông tin từ Firestore về local để đồng bộ sau này
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();
                            final String currentEmail = email; // Final for listener
                            final String currentPass = password; // Final for listener
                            Toast.makeText(MainActivity.this, "Đang tải dữ liệu đám mây...", Toast.LENGTH_SHORT).show();
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users").document(uid).get()
                                    .addOnSuccessListener(documentSnapshot -> {
                                        DatabaseHelper dbLocal = new DatabaseHelper(MainActivity.this);
                                        
                                        // 1. XÓA TOÀN BỘ DỮ LIỆU CŨ CỦA NGƯỜI DÙNG TRƯỚC ĐÓ ĐỂ KHÔNG BỊ GHI ĐÈ XUYÊN TÀI KHOẢN
                                        dbLocal.clearAllTransactions();

                                        // 2. Lưu lại bản ghi profile
                                        if (documentSnapshot.exists()) {
                                            String fName = documentSnapshot.getString("fullName");
                                            String fPhone = documentSnapshot.getString("phone");
                                            dbLocal.saveUserLocal(fName, currentEmail, fPhone, currentPass);
                                        } else {
                                            dbLocal.saveUserLocal(null, currentEmail, null, currentPass);
                                        }
                                        
                                        // 3. TỰ ĐỘNG TẢI DỮ LIỆU GIAO DỊCH CỦA ACCOUNT NAY VỀ TRƯỚC KHI VÀO APP
                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                            .collection("users").document(uid)
                                            .collection("transactions")
                                            .get()
                                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                                if (!queryDocumentSnapshots.isEmpty()) {
                                                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                                                        try {
                                                            long amount = doc.getLong("amount") != null ? doc.getLong("amount") : 0;
                                                            String note = doc.getString("note") != null ? doc.getString("note") : "";
                                                            String category = doc.getString("category") != null ? doc.getString("category") : "";
                                                            int type = doc.getLong("type") != null ? doc.getLong("type").intValue() : 0;
                                                            String createdBy = doc.getString("createdBy") != null ? doc.getString("createdBy") : "";
                                                            String deviceName = doc.getString("devices") != null ? doc.getString("devices") : "";
                                                            String deviceId = doc.getString("deviceId") != null ? doc.getString("deviceId") : "";

                                                            java.util.Date dateObj = null;
                                                            if (doc.getTimestamp("timestamp") != null) {
                                                                dateObj = doc.getTimestamp("timestamp").toDate();
                                                            }
                                                            String dateStr = "";
                                                            if (dateObj != null) {
                                                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                                                                dateStr = sdf.format(dateObj);
                                                            }

                                                            Transaction t = new Transaction(amount, note, category, dateStr, type);
                                                            t.setCreatedBy(createdBy);
                                                            t.setDeviceName(deviceName);
                                                            t.setDeviceId(deviceId);
                                                            dbLocal.addTransaction(t);
                                                        } catch (Exception e) {
                                                            android.util.Log.e("FirebaseFetch", "Error parsing doc: " + e.getMessage());
                                                        }
                                                    }
                                                }
                                                // Chuyển màn hình sau khi tải xong toàn bộ dữ liệu
                                                Intent intentFinal = new Intent(MainActivity.this, SplashActivity.class);
                                                startActivity(intentFinal);
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                // Lỗi tải giao dịch vẫn cho phép vào trong (vì profile đã lưu)
                                                Intent intentErr = new Intent(MainActivity.this, SplashActivity.class);
                                                startActivity(intentErr);
                                                finish();
                                            });

                                    })
                                    .addOnFailureListener(e -> {
                                        // Nếu lỗi fetch Profile, đi thẳng vào (fallback)
                                        Intent intentErr = new Intent(MainActivity.this, SplashActivity.class);
                                        startActivity(intentErr);
                                        finish();
                                    });
                        } else {
                        // NẾU THẤT BẠI
                        Toast.makeText(MainActivity.this, "Tài khoản không tồn tại", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}