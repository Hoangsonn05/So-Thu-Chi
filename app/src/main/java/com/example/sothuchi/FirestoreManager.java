package com.example.sothuchi;

import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sync Engine - Trung tâm điều phối đồng bộ Firestore
 * Áp dụng: Write Batching, Uniqueness ID logic, Data Retrieval.
 */
public class FirestoreManager {
    private static final String TAG = "FirestoreManager";
    private final FirebaseFirestore db;
    private final String userId;

    public FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        this.userId = (user != null) ? user.getUid() : null;
    }

    public boolean isUserLoggedIn() {
        return userId != null;
    }

    /**
     * Đồng bộ hồ sơ người dùng (Root document)
     */
    public Task<Void> syncUserProfile(Map<String, Object> profileData) {
        if (!isUserLoggedIn()) return Tasks.forException(new Exception("User not logged in"));
        
        profileData.put("lastSyncAt", FieldValue.serverTimestamp());
        return db.collection("users").document(userId)
                .set(profileData, SetOptions.merge());
    }

    /**
     * Fix Document Overwriting: Sử dụng UUID để đảm bảo tính duy nhất tuyệt đối
     * của mỗi giao dịch mới được đẩy lên Firestore, tránh xung đột ID SQLite.
     */
    public void syncTransactionsBatch(List<Transaction> transactions, SyncCallback callback) {
        if (!isUserLoggedIn() || transactions == null || transactions.isEmpty()) {
            if (callback != null) callback.onComplete(0);
            return;
        }

        WriteBatch batch = db.batch();
        int count = 0;

        SimpleDateFormat yearMonthFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy", Locale.getDefault());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        for (Transaction t : transactions) {
            try {
                java.util.Date date = (t.getDate() != null) ? sdf.parse(t.getDate()) : new java.util.Date();
                if (date == null) date = new java.util.Date();

                // Sử dụng UUID để đảm bảo docId không bao giờ trùng lặp
                String docId = "trans_" + t.getId() + "_" + UUID.randomUUID().toString();
                DocumentReference docRef = db.collection("users").document(userId)
                        .collection("transactions").document(docId);

                Map<String, Object> data = new HashMap<>();
                data.put("amount", t.getAmount());
                data.put("note", t.getNote());
                data.put("category", t.getCategory());
                data.put("type", t.getType());
                data.put("timestamp", new Timestamp(date));
                data.put("yearMonth", yearMonthFmt.format(date));
                data.put("year", Integer.parseInt(yearFmt.format(date)));
                data.put("lastUpdated", FieldValue.serverTimestamp());

                batch.set(docRef, data, SetOptions.merge());
                count++;

                // Commit batch mỗi 500 thao tác để tối ưu hiệu năng Firestore
                if (count % 500 == 0) {
                    batch.commit();
                    batch = db.batch();
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi xử lý bản ghi " + t.getId() + ": " + e.getMessage());
            }
        }

        final int finalCount = count;
        batch.commit().addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Batch sync thành công: " + finalCount + " bản ghi.");
            if (callback != null) callback.onComplete(finalCount);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Batch sync thất bại: " + e.getMessage());
            if (callback != null) callback.onError(e);
        });
    }

    /**
     * Truy xuất toàn bộ dữ liệu giao dịch của người dùng hiện tại từ Firestore.
     * Sắp xếp theo thời gian (timestamp) giảm dần.
     */
    public void downloadUserTransactions(DownloadCallback callback) {
        if (!isUserLoggedIn()) {
            if (callback != null) callback.onError(new Exception("User not logged in"));
            return;
        }

        db.collection("users").document(userId)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Map<String, Object>> transactionsData = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Map<String, Object> data = document.getData();
                        data.put("firebaseDocId", document.getId());
                        transactionsData.add(data);
                    }
                    if (callback != null) callback.onSuccess(transactionsData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi tải dữ liệu người dùng: " + e.getMessage());
                    if (callback != null) callback.onError(e);
                });
    }

    public interface SyncCallback {
        void onComplete(int count);
        void onError(Exception e);
    }

    public interface DownloadCallback {
        void onSuccess(List<Map<String, Object>> data);
        void onError(Exception e);
    }
}
