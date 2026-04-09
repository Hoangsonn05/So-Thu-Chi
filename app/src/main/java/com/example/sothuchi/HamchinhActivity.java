package com.example.sothuchi;

import android.app.DatePickerDialog;
import android.content.Intent;

import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.app.Activity;
import android.text.TextUtils;
import android.view.View;

import android.graphics.Color;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;
import com.example.sothuchi.search.SearchScreenCollector;
import com.example.sothuchi.search.SearchScreenUiState;
import com.example.sothuchi.search.SharedWalletSearchViewModel;
import com.example.sothuchi.realtime.model.FirestoreTransaction;
import com.example.sothuchi.realtime.ui.TransactionRealtimeCollector;
import com.example.sothuchi.realtime.ui.TransactionRealtimeViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import android.graphics.Typeface;

import android.view.Gravity;

public class HamchinhActivity extends AppCompatActivity {
    // API_Key (LƯU Ý: Tuyệt đối không để lộ key này lên GitHub hoặc nơi công cộng)
    private static final String GEMINI_API_KEY = "AIzaSyBk2tRqMVasNvZP13P9O5eymUiD-rSc31A";

    // Các biến theo dõi trạng thái gốc
    private View selectedCategoryView = null;
    private String selectedCategoryName = "";
    private Calendar currentCalendar;
    private DatabaseHelper dbHelper;
    private TransactionRealtimeViewModel transactionRealtimeViewModel;

    // Voice input: lưu reference EditText để cập nhật khi voice return
    private EditText pendingVoiceInput = null;
    private final ActivityResultLauncher<Intent> speechRecognizerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty() && pendingVoiceInput != null) {
                        pendingVoiceInput.setText(matches.get(0));
                        pendingVoiceInput.setSelection(pendingVoiceInput.getText().length());
                    }
                }
            });

    // CÂU LỆNH ĐIỀU KHIỂN AI (Prompt Engineering)
    private String getAiPrompt(String userText, String currentDate) {
        return "SYSTEM: Trợ lý tài chính. TASK: Phân tích text -> trả về 1 JSON DUY NHẤT. KHÔNG bọc markdown (```). KHÔNG giải thích.\n" +
                "TODAY: " + currentDate + "\n\n" +
                "RULES:\n" +
                "1. action_type: 'THU', 'CHI', 'MO_BAO_CAO', 'KHONG_RO'.\n" +
                "2. danh_muc_CHI: [Ăn uống, Chi tiêu hàng ngày, Quần áo, Mỹ phẩm, Phí giao lưu, Y tế, Giáo dục, Tiền điện, Du lịch, Phí liên lạc, Tiền nhà, Khác].\n" +
                "3. danh_muc_THU: [Tiền lương, Tiền thưởng, Đầu tư, Khác].\n" +
                "*(Tự suy luận danh mục sát nghĩa nhất theo thói quen)*\n" +
                "4. Thời gian: Tự quy đổi 'hôm qua', 'tuần trước'... ra ngày cụ thể.\n" +
                "5. auto_submit: Đặt 'true' NẾU text có ĐỦ [Số tiền + Mục đích]. Đặt 'false' nếu thiếu.\n" +
                "6. thong_bao: NẾU auto_submit=true -> Viết 1 câu xác nhận ngắn gọn (VD: 'Đã lưu chi 50K cho Cà phê'). NẾU false -> để rỗng \"\".\n\n" +
                "JSON FORMAT BẮT BUỘC:\n" +
                "{\"action_type\":\"\",\"so_tien\":0,\"ghi_chu\":\"\",\"danh_muc\":\"\",\"ngay\":\"dd/MM/yyyy (E)\",\"auto_submit\":true,\"thong_bao\":\"\"}\n\n" +
                "USER TEXT: \"" + userText + "\"";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentCalendar = Calendar.getInstance();
        dbHelper = new DatabaseHelper(this);

        // Firestore offline persistence (phải gọi TRƯỚC mọi thao tác Firestore khác)
        initFirestoreOfflineCache();
        
        // Khởi tạo Firestore Engine mới
        firestoreManager = new FirestoreManager();
        transactionRealtimeViewModel = new ViewModelProvider(this).get(TransactionRealtimeViewModel.class);
        TransactionRealtimeCollector.collect(this, transactionRealtimeViewModel, this::onRealtimeTransactionsChanged);

        showLayout(R.layout.activity_main);
    }

    private FirestoreManager firestoreManager;

    private void initFirestoreOfflineCache() {
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build())
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Exception e) {
            // Settings đã được thiết lập trước đó, bỏ qua
        }
    }

    private void showLayout(int layoutId) {
        layoutId_current = layoutId;
        try {
            ViewDataBinding binding = DataBindingUtil.setContentView(this, layoutId);
            if (binding == null) {
                setContentView(layoutId);
            }
        } catch (Exception e) {
            setContentView(layoutId);
        }

        selectedCategoryView = null;
        selectedCategoryName = "";

        setupNavigation();
        setupDatePicker();
        setupSubmitButton();

        // Nếu là layout Khác, thiết lập nút đăng xuất và đồng bộ
        if (layoutId == R.layout.khac) {
            setupLogoutButton();
            setupSyncButton();
            setupDownloadButton();
        }
        // GỌI HÀM LẮNG NGHE NÚT AI
        setupAiInput();
        
        // Cập nhật dữ liệu báo cáo nếu là layout báo cáo
        if (layoutId == R.layout.baocao || layoutId == R.layout.baocao_thunhap) {
            updateBaoCaoDateUI();
        } else if (layoutId == R.layout.baocao_nam) {
            updateYearlyDateUI();
        } else if (layoutId == R.layout.baocao_toanki) {
            updateToanKiDateUI();
        } else if (layoutId == R.layout.ngansach) {
            setupBudgetDatePicker();
        }

        // Nếu là giao diện Tiền Chi mới, thiết lập RecyclerView
        if (layoutId == R.layout.activity_main) {
            setupTienChiRecyclerView();
        }

        // Nếu là giao diện Tiền Thu mới, thiết lập RecyclerView danh mục
        if (layoutId == R.layout.tienthu) {
            setupTienThuRecyclerView();
        }

        // Nếu là layout lịch, thiết lập tương tác ngày
        if (layoutId == R.layout.lich) {
            setupCalendarInteraction();
        }

        // --- MỚI: Khởi tạo layout Báo cáo trong năm ---
        if (layoutId == R.layout.bctn_chitieu) {
            setupBctnYearlyReport();
        } else if (layoutId == R.layout.bctn_thunhap) {
            setupBctnYearlyIncomeReport();
        } else if (layoutId == R.layout.bctn_tong) {
            setupBctnYearlyNetReport();
        } else if (layoutId == R.layout.bctd_sodu) {
            setupBctdBalanceReport();
        }

        // --- Khởi tạo màn hình Tìm kiếm giao dịch ---
        if (layoutId == R.layout.layout_search) {
            setupSearchScreen();
        }
    }

    // --- CÁC HÀM CÀI ĐẶT GIAO DIỆN & SỰ KIỆN CƠ BẢN ---

    private void setupNavigation() {
        LinearLayout navNhapVao = findViewById(R.id.nav_nhap_vao);
        LinearLayout navLich = findViewById(R.id.nav_lich);
        LinearLayout navBaoCao = findViewById(R.id.nav_bao_cao);
        LinearLayout navNganSach = findViewById(R.id.nav_ngan_sach);
        LinearLayout navKhac = findViewById(R.id.nav_khac);

        View btnReportYearly = findViewById(R.id.btn_report_yearly);
        View btnReportMonthly = findViewById(R.id.btn_report_monthly);
        View btnTienThu = findViewById(R.id.btn_tien_thu);
        View anbtnTienChi = findViewById(R.id.btn_tien_chi);

        if (btnTienThu != null) btnTienThu.setOnClickListener(v -> showLayout(R.layout.tienthu));
        View btnTienChiRedesign = findViewById(R.id.btn_tien_chi);
        if (btnTienChiRedesign != null) btnTienChiRedesign.setOnClickListener(v -> showLayout(R.layout.activity_main));

        if (navNhapVao != null) navNhapVao.setOnClickListener(v -> showLayout(R.layout.activity_main));
        if (navLich != null) navLich.setOnClickListener(v -> showLayout(R.layout.lich));
        if (navBaoCao != null) navBaoCao.setOnClickListener(v -> showLayout(R.layout.baocao));
        if (navNganSach != null) navNganSach.setOnClickListener(v -> showLayout(R.layout.ngansach));
        if (navKhac != null) navKhac.setOnClickListener(v -> showLayout(R.layout.khac));
        if (btnReportYearly != null) btnReportYearly.setOnClickListener(v -> showLayout(R.layout.baocao_nam));
        if (btnReportMonthly != null) btnReportMonthly.setOnClickListener(v -> showLayout(R.layout.baocao));

        // Khac Menu Interactions
        View itemSearchTransaction = findViewById(R.id.item_search_transaction);
        if (itemSearchTransaction != null) itemSearchTransaction.setOnClickListener(v -> showLayout(R.layout.layout_search));

        View itemReportAll = findViewById(R.id.item_report_all);
        if (itemReportAll != null) itemReportAll.setOnClickListener(v -> showLayout(R.layout.baocao_toanki));

        View itemReportYear = findViewById(R.id.item_report_year);
        if (itemReportYear != null) itemReportYear.setOnClickListener(v -> showLayout(R.layout.bctn_chitieu));

        View itemReportBalance = findViewById(R.id.item_report_balance);
        if (itemReportBalance != null) itemReportBalance.setOnClickListener(v -> showLayout(R.layout.bctd_sodu));

        View itemExportData = findViewById(R.id.item_export_data);
        if (itemExportData != null) itemExportData.setOnClickListener(v -> showExportDialog());

        View itemLoginManagement = findViewById(R.id.item_login_management);
        if (itemLoginManagement != null) {
            itemLoginManagement.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.sothuchi.sharedwallet.ui.DeviceManagementActivity.class);
                startActivity(intent);
            });
        }

        View itemChangeUsername = findViewById(R.id.item_change_username);
        if (itemChangeUsername != null) {
            itemChangeUsername.setOnClickListener(v -> showChangeUsernameDialog());
        }
        
        // Back Button in baocao_toanki
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null && layoutId_current == R.layout.baocao_toanki) {
            btnBack.setOnClickListener(v -> showLayout(R.layout.khac));
        }

        // Back Button in Search Screen
        View btnSearchBack = findViewById(R.id.btn_search_back);
        if (btnSearchBack != null) {
            btnSearchBack.setOnClickListener(v -> showLayout(R.layout.khac));
        }

        // Tab switching within Monthly Report
        View tabChiTieu = findViewById(R.id.tab_chi_tieu);
        View tabThuNhap = findViewById(R.id.tab_thu_nhap);
        if (tabChiTieu != null) tabChiTieu.setOnClickListener(v -> showLayout(R.layout.baocao));
        if (tabThuNhap != null) tabThuNhap.setOnClickListener(v -> showLayout(R.layout.baocao_thunhap));

        // Month Navigation in Report
        View btnPrevMonth = findViewById(R.id.btn_prev_month);
        View btnNextMonth = findViewById(R.id.btn_next_month);
        View dateContainer = findViewById(R.id.date_container);

        if (btnPrevMonth != null) btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateBaoCaoDateUI();
        });
        if (btnNextMonth != null) btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateBaoCaoDateUI();
        });
        if (dateContainer != null) dateContainer.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, day) -> {
                currentCalendar.set(year, month, day);
                updateBaoCaoDateUI();
            }, currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Yearly Tab switching
        View tabChiTieuNam = findViewById(R.id.tab_chi_tieu_nam);
        View tabThuNhapNam = findViewById(R.id.tab_thu_nhap_nam);
        if (tabChiTieuNam != null) tabChiTieuNam.setOnClickListener(v -> showLayout(R.layout.baocao_nam));
        if (tabThuNhapNam != null) tabThuNhapNam.setOnClickListener(v -> showLayout(R.layout.baocao_nam_thunhap));

        // Year Navigation in Report
        View btnPrevYear = findViewById(R.id.btn_prev_year);
        View btnNextYear = findViewById(R.id.btn_next_year);
        View yearContainer = findViewById(R.id.year_container);
        if (btnPrevYear != null) btnPrevYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, -1);
            updateYearlyDateUI();
        });
        if (btnNextYear != null) btnNextYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, 1);
            updateYearlyDateUI();
        });
        if (yearContainer != null) yearContainer.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, day) -> {
                currentCalendar.set(Calendar.YEAR, year);
                updateYearlyDateUI();
            }, currentCalendar.get(Calendar.YEAR), 0, 1).show();
        });
    }

    private void setupLogoutButton() {
        View btnLogout = findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                // Xóa Cache Firestore để giải phóng bộ nhớ của User cũ
                com.google.firebase.firestore.FirebaseFirestore.getInstance().clearPersistence();

                FirebaseAuth.getInstance().signOut();
                Toast.makeText(this, "Đã đăng xuất và xóa cache!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void setupSyncButton() {
        View itemSync = findViewById(R.id.item_sync_data);
        if (itemSync != null) {
            itemSync.setOnClickListener(v -> {
                Toast.makeText(this, "Đang khởi tạo thuật toán đồng bộ...", Toast.LENGTH_SHORT).show();
                syncLocalDataToFirestore();
            });
        }
    }

    private void setupDownloadButton() {
        View itemDownload = findViewById(R.id.item_download_data);
        if (itemDownload != null) {
            itemDownload.setOnClickListener(v -> {
                Toast.makeText(this, "Đang tải dữ liệu từ Firebase...", Toast.LENGTH_SHORT).show();
                fetchDataFromFirebase();
            });
        }
    }

    private void fetchDataFromFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Chưa đăng nhập, không thể tải dữ liệu", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("transactions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        dbHelper.clearAllTransactions(); // Clear current Local SQLite
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
                                dbHelper.addTransaction(t);
                            } catch (Exception e) {
                                android.util.Log.e("FirebaseFetch", "Error parsing doc: " + e.getMessage());
                            }
                        }
                        Toast.makeText(this, "Đồng bộ hoàn tất!", Toast.LENGTH_SHORT).show();

                        // Restart app to apply data
                        Intent intent = new Intent(this, HamchinhActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Không có dữ liệu trên Firebase", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi khi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupTienChiRecyclerView() {
        RecyclerView rv = findViewById(R.id.rv_categories_expense);
        if (rv == null) return;

        List<CategoryAdapter.CategoryModel> list = new ArrayList<>();
        list.add(new CategoryAdapter.CategoryModel("Ăn uống", android.R.drawable.ic_menu_today));
        list.add(new CategoryAdapter.CategoryModel("Chi tiêu", android.R.drawable.ic_menu_manage));
        list.add(new CategoryAdapter.CategoryModel("Quần áo", android.R.drawable.ic_menu_gallery));
        list.add(new CategoryAdapter.CategoryModel("Mỹ phẩm", android.R.drawable.ic_menu_camera));
        list.add(new CategoryAdapter.CategoryModel("Giao lưu", android.R.drawable.ic_menu_share));
        list.add(new CategoryAdapter.CategoryModel("Y tế", android.R.drawable.ic_menu_call));
        list.add(new CategoryAdapter.CategoryModel("Giáo dục", android.R.drawable.ic_menu_sort_alphabetically));
        list.add(new CategoryAdapter.CategoryModel("Tiền điện", android.R.drawable.ic_menu_info_details));
        list.add(new CategoryAdapter.CategoryModel("Du lịch", android.R.drawable.ic_menu_directions));
        list.add(new CategoryAdapter.CategoryModel("Liên lạc", android.R.drawable.ic_menu_send));
        list.add(new CategoryAdapter.CategoryModel("Tiền nhà", android.R.drawable.ic_menu_view));
        list.add(new CategoryAdapter.CategoryModel("Khác", android.R.drawable.ic_menu_help));

        CategoryAdapter adapter = new CategoryAdapter(list, Color.parseColor("#1EBE5D"), category -> {
            selectedCategoryName = category.name;
        });

        rv.setAdapter(adapter);
    }

    private void setupTienThuRecyclerView() {
        RecyclerView rv = findViewById(R.id.rv_categories);
        if (rv == null) return;

        List<CategoryAdapter.CategoryModel> list = new ArrayList<>();
        list.add(new CategoryAdapter.CategoryModel("Tiền lương", android.R.drawable.ic_menu_my_calendar));
        list.add(new CategoryAdapter.CategoryModel("Tiền phụ cấp", android.R.drawable.ic_menu_save));
        list.add(new CategoryAdapter.CategoryModel("Tiền thưởng", android.R.drawable.ic_menu_send));
        list.add(new CategoryAdapter.CategoryModel("Thu nhập phụ", android.R.drawable.ic_input_add));
        list.add(new CategoryAdapter.CategoryModel("Đầu tư", android.R.drawable.ic_menu_gallery));
        list.add(new CategoryAdapter.CategoryModel("Thu nhập tạm", android.R.drawable.ic_menu_manage));
        list.add(new CategoryAdapter.CategoryModel("Chỉnh sửa >", android.R.drawable.ic_menu_edit));

        CategoryAdapter adapter = new CategoryAdapter(list, Color.parseColor("#1EBE5D"), category -> {
            selectedCategoryName = category.name;
        });
        rv.setAdapter(adapter);
    }

    private void setupDatePicker() {
        TextView tvDate = findViewById(R.id.value_date);
        if (tvDate == null) tvDate = findViewById(R.id.tv_date_value);

        if (tvDate != null) {
            final TextView finalTvDate = tvDate;
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy (E)", new Locale("vi", "VN"));
            finalTvDate.setText(sdf.format(currentCalendar.getTime()));

            finalTvDate.setOnClickListener(v -> {
                int year = currentCalendar.get(Calendar.YEAR);
                int month = currentCalendar.get(Calendar.MONTH);
                int day = currentCalendar.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(
                        HamchinhActivity.this,
                        (view, selectedYear, selectedMonth, selectedDay) -> {
                            currentCalendar.set(selectedYear, selectedMonth, selectedDay);
                            finalTvDate.setText(sdf.format(currentCalendar.getTime()));
                        },
                        year, month, day
                );
                datePickerDialog.show();
            });

            View btnPrev = findViewById(R.id.btn_prev);
            View btnNext = findViewById(R.id.btn_next);
            if (btnPrev != null) {
                btnPrev.setOnClickListener(v -> {
                    currentCalendar.add(Calendar.DAY_OF_MONTH, -1);
                    finalTvDate.setText(sdf.format(currentCalendar.getTime()));
                });
            }
            if (btnNext != null) {
                btnNext.setOnClickListener(v -> {
                    currentCalendar.add(Calendar.DAY_OF_MONTH, 1);
                    finalTvDate.setText(sdf.format(currentCalendar.getTime()));
                });
            }
        }
    }

    private void updateBaoCaoDateUI() {
        TextView tvMonthYear = findViewById(R.id.tv_month_year);
        TextView tvRange = findViewById(R.id.tv_range);

        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);

        if (tvMonthYear != null) tvMonthYear.setText(String.format(Locale.getDefault(), "%02d/%d", month, year));
        if (tvRange != null) {
            int lastDay = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            tvRange.setText(String.format(Locale.getDefault(), "(01/%02d - %02d/%02d)", month, lastDay, month));
        }

        List<Transaction> transactions = dbHelper.getTransactionsByMonth(month, year);
        long totalIncome = 0;
        long totalExpense = 0;
        for (Transaction t : transactions) {
            if (t.getType() == 1) totalIncome += t.getAmount();
            else totalExpense += t.getAmount();
        }

        TextView tvChiTieu = findViewById(R.id.tv_monthly_chi_tieu);
        TextView tvThuNhap = findViewById(R.id.tv_monthly_thu_nhap);
        TextView tvBalance = findViewById(R.id.tv_monthly_balance);

        if (tvChiTieu != null) tvChiTieu.setText("-" + String.format(Locale.getDefault(), "%,d", totalExpense) + "đ");
        if (tvThuNhap != null) tvThuNhap.setText("+" + String.format(Locale.getDefault(), "%,d", totalIncome) + "đ");
        if (tvBalance != null) {
            long balance = totalIncome - totalExpense;
            tvBalance.setText((balance >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%,d", balance) + "đ");
            tvBalance.setTextColor(balance >= 0 ? ContextCompat.getColor(this, R.color.saturday_blue) : ContextCompat.getColor(this, R.color.sunday_red));
        }

        List<Transaction> filtered = new ArrayList<>();
        if (layoutId_current == R.layout.baocao_thunhap) {
            for (Transaction t : transactions) if (t.getType() == 1) filtered.add(t);
            updateIncomeChartData(filtered);
        } else {
            for (Transaction t : transactions) if (t.getType() == 0) filtered.add(t);
            updateChartData(filtered);
        }
    }

    private void updateIncomeChartData(List<Transaction> transactions) {
        View containerTotal = findViewById(R.id.layout_container_total);
        PieChart pieChart = findViewById(R.id.pie_chart_report);
        LinearLayout listContainer = findViewById(R.id.layout_danh_sach_chi_tieu);

        if (containerTotal == null || pieChart == null || listContainer == null) return;
        if (transactions.isEmpty()) {
            containerTotal.setVisibility(View.GONE);
            return;
        }

        containerTotal.setVisibility(View.VISIBLE);
        Map<String, Long> categorySum = new HashMap<>();
        long totalAmount = 0;
        for (Transaction t : transactions) {
            String cat = t.getCategory();
            categorySum.put(cat, categorySum.getOrDefault(cat, 0L) + t.getAmount());
            totalAmount += t.getAmount();
        }

        List<PieEntry> entries = new ArrayList<>();
        PieEntry maxEntry = null;
        for (Map.Entry<String, Long> entry : categorySum.entrySet()) {
            PieEntry pe = new PieEntry(entry.getValue(), entry.getKey());
            entries.add(pe);
            if (maxEntry == null || pe.getValue() > maxEntry.getValue()) maxEntry = pe;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        int[] colors = {Color.parseColor("#43A047"), Color.parseColor("#2196F3"), Color.parseColor("#009688"), Color.parseColor("#FFC107"), Color.parseColor("#9C27B0")};
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        data.setDrawValues(false);
        pieChart.setData(data);
        pieChart.setHoleRadius(55f);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);

        final long finalTotal = totalAmount;
        if (maxEntry != null) {
            float percent = (maxEntry.getValue() * 100.0f) / finalTotal;
            pieChart.setCenterText(maxEntry.getLabel() + "\n" + String.format(Locale.getDefault(), "%,d", (long)maxEntry.getValue()) + "đ\n(" + String.format(Locale.getDefault(), "%.1f%%", percent) + ")");
        } else {
            pieChart.setCenterText("Tổng Thu\n" + String.format(Locale.getDefault(), "%,d", finalTotal) + "đ");
        }
        pieChart.setCenterTextSize(18f);

        pieChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry pe = (PieEntry) e;
                    float percent = (pe.getValue() * 100.0f) / finalTotal;
                    View cardChartInfo = findViewById(R.id.card_chart_info);
                    TextView tvChartInfo = findViewById(R.id.tv_chart_info);
                    if (tvChartInfo != null) tvChartInfo.setText(pe.getLabel() + ": " + String.format(Locale.getDefault(), "%,d", (long)pe.getValue()) + "đ (" + String.format(Locale.getDefault(), "%.1f%%", percent) + ")");
                    if (cardChartInfo != null) cardChartInfo.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onNothingSelected() {
                View cardChartInfo = findViewById(R.id.card_chart_info);
                if (cardChartInfo != null) cardChartInfo.setVisibility(View.GONE);
            }
        });

        pieChart.animateY(1000);
        pieChart.invalidate();

        listContainer.removeAllViews();
        for (Map.Entry<String, Long> entry : categorySum.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 30, 0, 30);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tvName.setTextColor(Color.BLACK);

            TextView tvAmount = new TextView(this);
            tvAmount.setText("+" + String.format(Locale.getDefault(), "%,dđ", entry.getValue()));
            tvAmount.setTextColor(ContextCompat.getColor(this, R.color.saturday_blue));
            tvAmount.setTypeface(null, Typeface.BOLD);
            tvAmount.setTextSize(16);

            row.addView(tvName);
            row.addView(tvAmount);
            listContainer.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            listContainer.addView(divider);
        }
    }

    private void setupYearlyDatePicker() {
        View yearDisplay = findViewById(R.id.year_display);
        View btnPrev = null;
        View btnNext = null;

        if (yearDisplay instanceof LinearLayout) {
             LinearLayout layout = (LinearLayout) yearDisplay;
             btnPrev = layout.getChildAt(0);
             btnNext = layout.getChildAt(2);
        }

        updateYearlyDateUI();

        if (yearDisplay != null) {
            yearDisplay.setOnClickListener(v -> {
                int year = currentCalendar.get(Calendar.YEAR);
                new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
                    currentCalendar.set(Calendar.YEAR, selectedYear);
                    updateYearlyDateUI();
                }, year, 0, 1).show();
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, -1);
                updateYearlyDateUI();
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, 1);
                updateYearlyDateUI();
            });
        }
    }

    private void updateYearlyDateUI() {
        View yearDisplay = findViewById(R.id.year_display);
        int currentYear = currentCalendar.get(Calendar.YEAR);

        if (yearDisplay != null) {
            try {
                LinearLayout container = (LinearLayout) ((LinearLayout) yearDisplay).getChildAt(1);
                TextView tvYear = (TextView) container.getChildAt(0);
                TextView tvRange = (TextView) container.getChildAt(1);

                tvYear.setText(String.valueOf(currentYear));
                tvRange.setText("(01/01 - 31/12)");
            } catch (Exception e) {
                // Fallback
            }
        }

        // Lấy dữ liệu và tính toán thống kê cả năm từ cơ sở dữ liệu
        currentYear = currentCalendar.get(Calendar.YEAR);
        List<Transaction> yearlyTransactions = dbHelper.getTransactionsByYear(currentYear);

        long totalIncome = 0;
        long totalExpense = 0;

        for (Transaction t : yearlyTransactions) {
            if (t.getType() == 1) { // Thu nhập
                totalIncome += t.getAmount();
            } else if (t.getType() == 0) { // Chi tiêu
                totalExpense += t.getAmount();
            }
        }

        // Cập nhật các thẻ tóm tắt (Summary Cards) trong baocao_nam.xml
        TextView tvChiTieu = findViewById(R.id.tv_yearly_chi_tieu);
        TextView tvThuNhap = findViewById(R.id.tv_yearly_thu_nhap);
        TextView tvBalance = findViewById(R.id.tv_yearly_balance);

        if (tvChiTieu != null) tvChiTieu.setText("-" + String.format(Locale.getDefault(), "%,d", totalExpense) + "đ");
        if (tvThuNhap != null) tvThuNhap.setText("+" + String.format(Locale.getDefault(), "%,d", totalIncome) + "đ");
        if (tvBalance != null) {
            long balance = totalIncome - totalExpense;
            String balanceStr = (balance >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%,d", balance) + "đ";
            tvBalance.setText(balanceStr);
            // saturday_blue cho số dương (+), sunday_red cho số âm (-)
            tvBalance.setTextColor(balance >= 0 ? ContextCompat.getColor(this, R.color.saturday_blue) : ContextCompat.getColor(this, R.color.sunday_red));
        }

        // Determine data type based on layout
        List<Transaction> filtered = new ArrayList<>();
        if (layoutId_current == R.layout.baocao_nam_thunhap) {
            for (Transaction t : yearlyTransactions) if (t.getType() == 1) filtered.add(t);
            updateYearlyIncomeChartData(filtered);
        } else {
            for (Transaction t : yearlyTransactions) if (t.getType() == 0) filtered.add(t);
            updateChartData(filtered);
        }
    }

    private void updateToanKiDateUI() {
        TextView tvIncome = findViewById(R.id.tv_toanki_income);
        TextView tvExpense = findViewById(R.id.tv_toanki_expense);
        TextView tvTotal = findViewById(R.id.tv_toanki_total);
        TextView tvFinalTotal = findViewById(R.id.tv_toanki_final_total);

        if (tvIncome == null || tvExpense == null || tvTotal == null) return;

        // Lấy dữ liệu toàn kì từ dbHelper (không tính toán lặp từ các hàm khác)
        long[] summary = dbHelper.getAllTimeSummary();
        long income = summary[0];
        long expense = summary[1];
        long balance = summary[2];

        tvIncome.setText("+" + String.format(Locale.getDefault(), "%,d", income) + "đ");
        tvExpense.setText("-" + String.format(Locale.getDefault(), "%,d", expense) + "đ");

        String balanceStr = (balance >= 0 ? "+" : "") + String.format(Locale.getDefault(), "%,d", balance) + "đ";
        tvTotal.setText(balanceStr);
        tvFinalTotal.setText(balanceStr);

        int colorBlue = ContextCompat.getColor(this, R.color.saturday_blue);
        int colorRed = ContextCompat.getColor(this, R.color.sunday_red);

        tvTotal.setTextColor(balance >= 0 ? colorBlue : colorRed);
        tvFinalTotal.setTextColor(balance >= 0 ? colorBlue : colorRed);
    }

    private void updateYearlyIncomeChartData(List<Transaction> transactions) {
        View containerTotal = findViewById(R.id.layout_container_total);
        PieChart pieChart = findViewById(R.id.pie_chart_report);
        LinearLayout listContainer = findViewById(R.id.layout_danh_sach_chi_tieu);

        if (containerTotal == null || pieChart == null || listContainer == null) return;
        if (transactions.isEmpty()) {
            containerTotal.setVisibility(View.GONE);
            return;
        }

        containerTotal.setVisibility(View.VISIBLE);
        Map<String, Long> categorySum = new HashMap<>();
        long totalAmount = 0;
        for (Transaction t : transactions) {
            String cat = t.getCategory();
            categorySum.put(cat, categorySum.getOrDefault(cat, 0L) + t.getAmount());
            totalAmount += t.getAmount();
        }

        List<PieEntry> entries = new ArrayList<>();
        PieEntry maxEntry = null;
        for (Map.Entry<String, Long> entry : categorySum.entrySet()) {
            PieEntry pe = new PieEntry(entry.getValue(), entry.getKey());
            entries.add(pe);
            if (maxEntry == null || pe.getValue() > maxEntry.getValue()) maxEntry = pe;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        int[] colors = {Color.parseColor("#43A047"), Color.parseColor("#2E7D32"), Color.parseColor("#1B5E20"), Color.parseColor("#8BC34A"), Color.parseColor("#AEEA00")};
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f); // Giảm gap chút để vòng trông liền mạch và to hơn
        dataSet.setSelectionShift(15f);

        PieData data = new PieData(dataSet);
        data.setDrawValues(false);

        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(55f); // Lỗ nhỏ hơn một chút để vòng biểu đồ dày hơn, trực quan hơn
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setHoleColor(Color.WHITE);

        // Reset CenterText với kích cỡ lớn hơn để cân đối với vòng dày
        final long finalTotal = totalAmount;
        pieChart.setCenterText("Tổng Thu\n" + String.format(Locale.getDefault(), "%,d", finalTotal) + "đ");
        pieChart.setCenterTextSize(18f);
        pieChart.setCenterTextColor(Color.BLACK);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);

        // Tìm info box thông báo phía trên biểu đồ
        View cardInfo = findViewById(R.id.card_chart_info);
        TextView tvChartInfo = findViewById(R.id.tv_chart_info);

        // Xử lý sự kiện click vào slice để hiện thông báo phía trên
        pieChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry && cardInfo != null && tvChartInfo != null) {
                    PieEntry pe = (PieEntry) e;
                    float percent = (pe.getValue() * 100.0f) / finalTotal;
                    String info = pe.getLabel() + ": " + String.format(Locale.getDefault(), "%,d", (long)pe.getValue()) + "đ (" + String.format(Locale.getDefault(), "%.1f%%", percent) + ")";
                    tvChartInfo.setText(info);
                    cardInfo.setVisibility(View.VISIBLE);
                    // Hiệu ứng zoom nhẹ cho CenterText khi chọn
                    pieChart.setCenterText(pe.getLabel() + "\nSelected");
                }
            }

            @Override
            public void onNothingSelected() {
                if (cardInfo != null) cardInfo.setVisibility(View.GONE);
                pieChart.setCenterText("Tổng Thu\n" + String.format(Locale.getDefault(), "%,d", finalTotal) + "đ");
            }
        });

        pieChart.animateY(1200);
        pieChart.invalidate();

        listContainer.removeAllViews();
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(categorySum.entrySet());
        Collections.sort(sortedList, (a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Long> entry : sortedList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 30, 0, 30);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tvName.setTextColor(Color.BLACK);

            TextView tvAmount = new TextView(this);
            tvAmount.setText("+" + String.format(Locale.getDefault(), "%,dđ", entry.getValue()));
            tvAmount.setTextColor(ContextCompat.getColor(this, R.color.saturday_blue));
            tvAmount.setTypeface(null, Typeface.BOLD);
            tvAmount.setTextSize(16);

            row.addView(tvName);
            row.addView(tvAmount);
            listContainer.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
            divider.setBackgroundColor(Color.parseColor("#DDDDDD"));

            listContainer.addView(row);
            listContainer.addView(divider);
        }
    }

    private void updateChartData(List<Transaction> transactions) {
        View containerTotal = findViewById(R.id.layout_container_total);
        PieChart pieChart = findViewById(R.id.pie_chart_report);
        LinearLayout listContainer = findViewById(R.id.layout_danh_sach_chi_tieu);

        if (containerTotal == null || pieChart == null || listContainer == null) return;

        List<Transaction> expenses = new ArrayList<>();
        for (Transaction t : transactions) {
            if (t.getType() == 0) expenses.add(t);
        }

        if (expenses.isEmpty()) {
            containerTotal.setVisibility(View.GONE);
            return;
        }

        containerTotal.setVisibility(View.VISIBLE);

        Map<String, Long> categorySum = new HashMap<>();
        long totalAmount = 0;
        for (Transaction t : expenses) {
            String cat = t.getCategory();
            categorySum.put(cat, categorySum.getOrDefault(cat, 0L) + t.getAmount());
            totalAmount += t.getAmount();
        }

        List<PieEntry> entries = new ArrayList<>();
        PieEntry maxEntry = null;
        for (Map.Entry<String, Long> entry : categorySum.entrySet()) {
            PieEntry pe = new PieEntry(entry.getValue(), entry.getKey());
            entries.add(pe);
            if (maxEntry == null || pe.getValue() > maxEntry.getValue()) {
                maxEntry = pe;
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");

        // Bảng màu hiện đại và cao cấp
        int[] customColors = {
            Color.parseColor("#42A5F5"), // Blue
            Color.parseColor("#66BB6A"), // Green
            Color.parseColor("#FFA726"), // Orange
            Color.parseColor("#26C6DA"), // Teal
            Color.parseColor("#EC407A"), // Pink
            Color.parseColor("#AB47BC"), // Purple
            Color.parseColor("#FF7043"), // Deep Orange
            Color.parseColor("#9CCC65")  // Light Green
        };
        dataSet.setColors(customColors);
        dataSet.setSliceSpace(2f); // Giảm gap chút để vòng trông liền mạch và to hơn
        dataSet.setSelectionShift(15f);

        PieData data = new PieData(dataSet);
        data.setDrawValues(false);

        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(55f); // Lỗ nhỏ hơn một chút để vòng biểu đồ dày hơn, trực quan hơn
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setHoleColor(Color.WHITE);

        // Reset CenterText với kích cỡ lớn hơn để cân đối với vòng dày
        final long finalTotal = totalAmount;
        pieChart.setCenterText("Tổng Chi\n" + String.format(Locale.getDefault(), "%,d", finalTotal) + "đ");
        pieChart.setCenterTextSize(18f);
        pieChart.setCenterTextColor(Color.BLACK);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);

        // Tìm info box thông báo phía trên biểu đồ
        View cardInfo = findViewById(R.id.card_chart_info);
        TextView tvChartInfo = findViewById(R.id.tv_chart_info);

        // Xử lý sự kiện click vào slice để hiện thông báo phía trên
        pieChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight h) {
                if (e instanceof PieEntry && cardInfo != null && tvChartInfo != null) {
                    PieEntry pe = (PieEntry) e;
                    float percent = (pe.getValue() * 100.0f) / finalTotal;
                    String info = pe.getLabel() + ": " + String.format(Locale.getDefault(), "%,d", (long)pe.getValue()) + "đ (" + String.format(Locale.getDefault(), "%.1f%%", percent) + ")";
                    tvChartInfo.setText(info);
                    cardInfo.setVisibility(View.VISIBLE);
                    // Hiệu ứng zoom nhẹ cho CenterText khi chọn
                    pieChart.setCenterText(pe.getLabel() + "\nSelected");
                }
            }

            @Override
            public void onNothingSelected() {
                if (cardInfo != null) cardInfo.setVisibility(View.GONE);
                pieChart.setCenterText("Tổng Chi\n" + String.format(Locale.getDefault(), "%,d", finalTotal) + "đ");
            }
        });

        pieChart.animateY(1200, com.github.mikephil.charting.animation.Easing.EaseOutQuart);
        pieChart.invalidate();

        listContainer.removeAllViews();
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(categorySum.entrySet());
        Collections.sort(sortedList, (a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Long> entry : sortedList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 30, 0, 30);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(this);
            tvName.setText(entry.getKey());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            tvName.setTextColor(Color.BLACK);
            tvName.setTextSize(15);

            float percent = (entry.getValue() * 100.0f) / totalAmount;
            TextView tvPercent = new TextView(this);
            tvPercent.setText(String.format(Locale.getDefault(), "%.1f%%", percent));
            tvPercent.setTextColor(Color.GRAY);
            tvPercent.setPadding(20, 0, 20, 0);

            TextView tvAmount = new TextView(this);
            tvAmount.setText(String.format(Locale.getDefault(), "%,dđ", entry.getValue()));
            tvAmount.setTextColor(ContextCompat.getColor(this, R.color.sunday_red));
            tvAmount.setTypeface(null, Typeface.BOLD);
            tvAmount.setTextSize(16);

            row.addView(tvName);
            row.addView(tvPercent);
            row.addView(tvAmount);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
            divider.setBackgroundColor(Color.parseColor("#DDDDDD"));

            listContainer.addView(row);
            listContainer.addView(divider);
        }
    }

    private void setupBudgetDatePicker() {
        updateBudgetDateUI();
    }

    private void setupSubmitButton() {
        View btnSubmit = findViewById(R.id.btn_submit);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                EditText etAmount = findViewById(R.id.input_amount);
                if (etAmount == null) etAmount = findViewById(R.id.et_amount);

                EditText etNote = findViewById(R.id.input_note);
                if (etNote == null) etNote = findViewById(R.id.et_note);

                TextView tvDate = findViewById(R.id.value_date);
                if (tvDate == null) tvDate = findViewById(R.id.tv_date_value);

                if (selectedCategoryName.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn một danh mục!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String amountStr = etAmount != null ? etAmount.getText().toString().trim() : "";
                if (TextUtils.isEmpty(amountStr) || amountStr.equals("0")) {
                    Toast.makeText(this, "Vui lòng nhập số tiền!", Toast.LENGTH_SHORT).show();
                    return;
                }

                long amount = Long.parseLong(amountStr);
                String noteStr = etNote != null ? etNote.getText().toString().trim() : "Không có ghi chú";
                String fullDateStr = tvDate != null ? tvDate.getText().toString() : "";

                // Ép kiểu chuỗi ngày tháng để chỉ lấy đoạn "dd/MM/yyyy"
                String cleanDate = fullDateStr;
                if (fullDateStr.contains(" ")) {
                    cleanDate = fullDateStr.split(" ")[0];
                }

                // Xác định loại giao dịch dựa trên layout hiện tại
                int type = (layoutId_current == R.layout.tienthu) ? 1 : 0;

                // Lưu vào cơ sở dữ liệu SQLite (local)
                Transaction transaction = new Transaction(amount, noteStr, selectedCategoryName, cleanDate, type);
                attachCurrentDeviceIdentity(transaction);
                long id = dbHelper.addTransaction(transaction);

                if (id != -1) {
                    // Đồng bộ lên Firestore (Subcollection & Overwrite)
                    java.util.Date selectedDate = parseCleanDate(cleanDate);
                    // Dùng mã định danh đồng nhất: legacy_ + id local để không bao giờ bị trùng khi đồng bộ
                    String firestoreId = "legacy_" + id;
                    saveOrUpdateTransaction(firestoreId, amount, noteStr, selectedCategoryName, type, selectedDate);

                    String typeStr = (type == 1) ? "khoản thu" : "khoản chi";
                    Toast.makeText(this, "Đã lưu " + typeStr + ": " + amountStr + "đ", Toast.LENGTH_SHORT).show();

                    // Reset UI
                    if (etAmount != null) etAmount.setText("");
                    if (etNote != null) etNote.setText("");

                    // Reset selection visual
                    if (selectedCategoryView != null) {
                        if (selectedCategoryView instanceof com.google.android.material.card.MaterialCardView) {
                            ((com.google.android.material.card.MaterialCardView) selectedCategoryView).setStrokeWidth(0);
                        } else {
                            updateSelectionVisual(selectedCategoryView, false);
                        }
                    }
                    selectedCategoryView = null;
                    selectedCategoryName = "";

                    // Làm mới lịch nếu đang hiển thị
                    refreshCalendarData();
                } else {
                    Toast.makeText(this, "Lỗi khi lưu vào database!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // --- FIRESTORE SYNC ---

    private java.util.Date parseCleanDate(String cleanDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return sdf.parse(cleanDate);
        } catch (Exception e) {
            return new java.util.Date();
        }
    }

    private void attachCurrentDeviceIdentity(Transaction transaction) {
        if (transaction == null) return;
        com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore store =
                new com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore(this);

        String createdBy = store.getNickname();
        transaction.setCreatedBy(createdBy != null && !createdBy.trim().isEmpty() ? createdBy : "Người dùng ẩn danh");

        String deviceName = android.os.Build.MODEL;
        transaction.setDeviceName(deviceName != null && !deviceName.trim().isEmpty() ? deviceName : "Không rõ thiết bị");

        transaction.setDeviceId(store.getDeviceId());
    }

    private void saveOrUpdateTransaction(String transactionId, long amount, String note, String categoryKey, int type, java.util.Date selectedDate) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || transactionId == null) return;

        String userId = user.getUid();

        // ═════════════════════════════════════════════════════════════
        // TASK 2: Inject Device Metadata (createdBy + deviceName)
        // ═════════════════════════════════════════════════════════════
        com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore store =
                new com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore(this);

        // FIX: createdBy gets the nickname (username) from SharedPreferences
        final String createdBy = (store.getNickname() != null && !store.getNickname().isEmpty())
                ? store.getNickname()
                : "Người dùng ẩn danh";

        // FIX: deviceName gets the hardware model from Build.MODEL (not nickname!)
        final String deviceName = (android.os.Build.MODEL != null && !android.os.Build.MODEL.isEmpty())
                ? android.os.Build.MODEL
                : "Không rõ thiết bị";
        final String deviceId = store.getDeviceId();

        // Chuyển đổi Date → Firestore Timestamp
        com.google.firebase.Timestamp firebaseTimestamp = new com.google.firebase.Timestamp(selectedDate);

        // Tạo yearMonth và year từ Date
        SimpleDateFormat yearMonthFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy", Locale.getDefault());
        String yearMonth = yearMonthFmt.format(selectedDate);
        int year = Integer.parseInt(yearFmt.format(selectedDate));

        // Đóng gói dữ liệu
        Map<String, Object> data = new HashMap<>();
        data.put("amount", amount);
        data.put("note", note);
        data.put("category", categoryKey);
        data.put("type", type);
        data.put("timestamp", firebaseTimestamp);
        data.put("yearMonth", yearMonth);
        data.put("year", year);
        data.put("lastUpdated", FieldValue.serverTimestamp());
        
        // TASK 2: Add metadata fields
        data.put("createdBy", createdBy);
        data.put("devices", deviceName);  // Firestore key is "devices", not "deviceName"
        data.put("deviceId", deviceId);

        // Sử dụng Subcollections: users/{uid}/transactions/{id}
        // SetOptions.merge() giúp Update nếu đã tồn tại, Create nếu chưa có
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("transactions").document(transactionId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    android.util.Log.d("Firestore", "✓ Database synced: " + transactionId);
                    android.util.Log.d("Firestore", "  → createdBy: " + createdBy);
                    android.util.Log.d("Firestore", "  → devices: " + deviceName);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Firestore", "✗ Sync error for " + transactionId + ": " + e.getMessage());
                    // Nếu lỗi do quyền (Permission Denied), hãy kiểm tra Firestore Rules trên Console
                });
    }

    private void syncLocalDataToFirestore() {
        if (firestoreManager == null || !firestoreManager.isUserLoggedIn()) {
            android.util.Log.e("FirestoreSync", "Manager null hoặc User chưa login.");
            return;
        }

        // 1. Đồng bộ Profile trước (Dùng SQLite Data)
        android.content.ContentValues localUser = dbHelper.getLocalUser();
        if (localUser != null) {
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", localUser.getAsString("email"));
            profile.put("fullName", localUser.getAsString("fullName"));
            profile.put("phone", localUser.getAsString("phone"));
            profile.put("password", localUser.getAsString("password"));
            firestoreManager.syncUserProfile(profile);
        }

        // 2. Đồng bộ giao dịch Chi tiêu dùng Thuật toán BATCHING (Gom lô)
        List<Transaction> allLocal = dbHelper.getAllTransactions();
        final int totalRecords = allLocal.size();

        runOnUiThread(() -> android.widget.Toast.makeText(this,
                "Tìm thấy " + totalRecords + " giao dịch. Đang đẩy lên đám mây (Batch/500)...",
                android.widget.Toast.LENGTH_SHORT).show());

        if (totalRecords == 0) return;

        firestoreManager.syncTransactionsBatch(allLocal, new FirestoreManager.SyncCallback() {
            @Override
            public void onComplete(int count) {
                runOnUiThread(() -> android.widget.Toast.makeText(HamchinhActivity.this,
                        "Đã đồng bộ thành công " + count + " bản ghi lên Cloud!",
                        android.widget.Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(HamchinhActivity.this,
                        "Lỗi đồng bộ: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- KHỐI CHỨC NĂNG AI ---

    // Các biến cho tương tác lịch
    private View lastSelectedDayView = null;
    private long lastClickTime = 0;

    // Lưu vị trí FAB để giữ nguyên khi chuyển màn hình
    private static float savedFabX = -1f;
    private static float savedFabY = -1f;

    private void setupAiInput() {
        View fabAi = findViewById(R.id.fab_ai);
        if (fabAi == null) return;

        // Chuyển sang dùng FrameLayout.LayoutParams để có thể di chuyển tự do
        fabAi.post(() -> {
            // Khôi phục vị trí đã lưu (nếu có)
            if (savedFabX >= 0 && savedFabY >= 0) {
                fabAi.setX(savedFabX);
                fabAi.setY(savedFabY);
            }
        });

        // Touch listener: phân biệt giữa "kéo" và "bấm"
        fabAi.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            float startX, startY;
            boolean isDragging = false;
            final float CLICK_THRESHOLD = 10 * getResources().getDisplayMetrics().density; // 10dp

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        isDragging = false;

                        // Phản hồi chạm: nâng lên nhẹ (Antigravity: weightlessness)
                        v.animate().scaleX(1.15f).scaleY(1.15f)
                                .setDuration(150).start();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float moveX = Math.abs(event.getRawX() - startX);
                        float moveY = Math.abs(event.getRawY() - startY);

                        if (moveX > CLICK_THRESHOLD || moveY > CLICK_THRESHOLD) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            float newX = event.getRawX() + dX;
                            float newY = event.getRawY() + dY;

                            // Lấy bounds an toàn (không cho ra ngoài màn hình)
                            android.view.ViewGroup parent = (android.view.ViewGroup) v.getParent();
                            int parentW = parent.getWidth();
                            int parentH = parent.getHeight();
                            float maxX = parentW - v.getWidth();
                            float maxY = parentH - v.getHeight();

                            // Giới hạn trong vùng an toàn
                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));

                            v.setX(newX);
                            v.setY(newY);
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        // Trả lại scale ban đầu (Antigravity: smooth transition)
                        v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(200).start();

                        if (!isDragging) {
                            // Đây là click → mở Bottom Sheet
                            showAiBottomSheet();
                        } else {
                            // Đây là drag → snap vào cạnh gần nhất
                            // (Antigravity: never snap instantly, min 300ms ease-out)
                            android.view.ViewGroup parent = (android.view.ViewGroup) v.getParent();
                            int parentW = parent.getWidth();
                            int parentH = parent.getHeight();

                            float centerX = v.getX() + v.getWidth() / 2f;
                            float targetX;

                            // Snap sang trái hoặc phải (cạnh gần hơn)
                            if (centerX < parentW / 2f) {
                                targetX = 16 * getResources().getDisplayMetrics().density; // margin 16dp
                            } else {
                                targetX = parentW - v.getWidth()
                                        - 16 * getResources().getDisplayMetrics().density;
                            }

                            // Giữ Y trong bounds hợp lệ
                            float targetY = v.getY();
                            float maxY = parentH - v.getHeight()
                                    - 16 * getResources().getDisplayMetrics().density;
                            float minY = 16 * getResources().getDisplayMetrics().density;
                            targetY = Math.max(minY, Math.min(targetY, maxY));

                            // Lưu vị trí mới
                            savedFabX = targetX;
                            savedFabY = targetY;

                            // Animation mượt mà (300ms ease-out)
                            v.animate()
                                    .x(targetX)
                                    .y(targetY)
                                    .setDuration(300)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                    .start();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showAiBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);

        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_ai, null);
        bottomSheet.setContentView(sheetView);

        // Configure flexible height (50% default)
        View bottomSheetInternal = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            bottomSheetInternal.setBackgroundResource(android.R.color.transparent);
            // Force the internal layout to be match_parent to fix the "empty space below" bug
            bottomSheetInternal.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;

            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal);

            // Get screen height
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int halfHeight = metrics.heightPixels / 2;

            behavior.setFitToContents(false);
            behavior.setPeekHeight(halfHeight); // Set peek height to exactly 50%
            behavior.setHalfExpandedRatio(0.5f);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED);
            behavior.setHideable(true);
            behavior.setSkipCollapsed(false);
        }

        EditText inputAiPrompt = sheetView.findViewById(R.id.input_ai_prompt);
        View btnAiSend = sheetView.findViewById(R.id.btn_ai_send);
        View btnVoice = sheetView.findViewById(R.id.btn_voice_input);
        TextView tvStatus = sheetView.findViewById(R.id.tv_ai_status);
        View btnExpand = sheetView.findViewById(R.id.btn_ai_expand_toggle);

        // Auto-expand on interaction
        View.OnTouchListener autoExpandListener = (v, event) -> {
            View bottomSheetInternalFocus = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternalFocus != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternalFocus);
                if (behavior.getState() == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED) {
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                }
            }
            return false;
        };
        inputAiPrompt.setOnTouchListener(autoExpandListener);

        // Manual expand toggle
        if (btnExpand != null) {
            btnExpand.setOnClickListener(v -> {
                View bottomSheetInternalExpand = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheetInternalExpand != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternalExpand);
                    if (behavior.getState() == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED);
                    } else {
                        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                    }
                }
            });
        }

        // Suggestion Chips - khi bấm sẽ tự động điền vào ô input
        View chip1 = sheetView.findViewById(R.id.chip_ai_1);
        View chip2 = sheetView.findViewById(R.id.chip_ai_2);
        View chip3 = sheetView.findViewById(R.id.chip_ai_3);
        View.OnClickListener chipListener = v -> {
            String chipText = ((TextView) v).getText().toString();
            // Loại bỏ emoji ở đầu nếu có
            chipText = chipText.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
            inputAiPrompt.setText(chipText);
            inputAiPrompt.setSelection(inputAiPrompt.getText().length());
        };
        if (chip1 != null) chip1.setOnClickListener(chipListener);
        if (chip2 != null) chip2.setOnClickListener(chipListener);
        if (chip3 != null) chip3.setOnClickListener(chipListener);

        // Voice Input - mở Google Speech Recognizer
        if (btnVoice != null) {
            btnVoice.setOnClickListener(v -> {
                pendingVoiceInput = inputAiPrompt;
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói nội dung giao dịch...");
                try {
                    speechRecognizerLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Thiết bị không hỗ trợ nhập giọng nói", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Send button
        btnAiSend.setOnClickListener(v -> {
            String text = inputAiPrompt.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, "Vui lòng nhập nội dung!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show status indicator
            if (tvStatus != null) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("✨ Chờ tớ chút nha ✨...");
            }
            btnAiSend.setEnabled(false);
            if (btnVoice != null) btnVoice.setEnabled(false);
            inputAiPrompt.setEnabled(false);

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy (E)", new Locale("vi", "VN"));
            String currentDate = sdf.format(currentCalendar.getTime());

            callGeminiApi(getAiPrompt(text, currentDate), bottomSheet, btnAiSend);
        });

        bottomSheet.show();
    }

    private void callGeminiApi(String prompt, com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet, View btnAiSend) {
        OkHttpClient client = new OkHttpClient();

        JSONObject jsonBody = new JSONObject();
        try {
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            JSONArray parts = new JSONArray();
            parts.put(part);
            JSONObject content = new JSONObject();
            content.put("parts", parts);
            JSONArray contents = new JSONArray();
            contents.put(content);
            jsonBody.put("contents", contents);
        } catch (Exception e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                // Thay chữ gemini-1.5-flash thành gemini-2.5-pro (hoặc gemini-1.5-pro tùy thuộc vào phiên bản API bạn đang bật)
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=" + GEMINI_API_KEY)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(HamchinhActivity.this, "Lỗi mạng: Không thể kết nối AI", Toast.LENGTH_SHORT).show();
                    if (btnAiSend != null) btnAiSend.setEnabled(true);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = null;
                try {
                    responseData = response.body() != null ? response.body().string() : null;
                } catch (Exception e) {
                    responseData = null;
                }

                if (responseData == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(HamchinhActivity.this, "Lỗi: phản hồi rỗng từ AI", Toast.LENGTH_SHORT).show();
                        if (btnAiSend != null) btnAiSend.setEnabled(true);
                    });
                    return;
                }

                if (response.isSuccessful()) {
                    try {
                        // Extract AI text safely (tolerant to missing keys)
                        String aiAnswer = null;
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            if (jsonResponse.has("candidates")) {
                                aiAnswer = jsonResponse
                                        .optJSONArray("candidates")
                                        .optJSONObject(0)
                                        .optJSONObject("content")
                                        .optJSONArray("parts")
                                        .optJSONObject(0)
                                        .optString("text", null);
                            }
                        } catch (Exception ignored) {
                        }

                        if (aiAnswer == null) {
                            // Fallback: try to use whole response as the AI answer
                            aiAnswer = responseData;
                        }

                        aiAnswer = aiAnswer.replace("```json", "").replace("```", "").trim();

                        JSONObject resultData;
                        try {
                            resultData = new JSONObject(aiAnswer);
                        } catch (Exception ex) {
                            // If aiAnswer is not a pure JSON object, try to locate a JSON substring
                            int first = aiAnswer.indexOf('{');
                            int last = aiAnswer.lastIndexOf('}');
                            if (first >= 0 && last > first) {
                                String sub = aiAnswer.substring(first, last + 1);
                                resultData = new JSONObject(sub);
                            } else {
                                throw ex;
                            }
                        }

                        final long amount = resultData.optLong("so_tien", 0);
                        final String note = resultData.optString("ghi_chu", "");
                        final String category = resultData.optString("danh_muc", "");
                        final String date = resultData.optString("ngay", "");
                        final boolean autoSubmit = resultData.optBoolean("auto_submit", false);
                        final String message = resultData.optString("thong_bao", "");
                        final String actionType = resultData.optString("action_type", "CHI");

                        runOnUiThread(() -> {
                            if (autoSubmit) {
                                // Close sheet immediately
                                if (bottomSheet != null && bottomSheet.isShowing()) bottomSheet.dismiss();

                                final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                                final Runnable saveRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        int type = actionType.equalsIgnoreCase("THU") ? 1 : 0;
                                        String cleanDate = date != null && date.contains(" ") ? date.split(" ")[0] : (date != null ? date : "");
                                        Transaction t = new Transaction(amount, note, category, cleanDate, type);
                                        attachCurrentDeviceIdentity(t);
                                        long id = -1;
                                        try {
                                            id = dbHelper.addTransaction(t);
                                        } catch (Exception e) {
                                            android.util.Log.e("AutoSubmit", "DB add error: " + e.getMessage());
                                        }
                                        if (id != -1) {
                                            try {
                                                saveOrUpdateTransaction("legacy_" + id, amount, note, category, type, parseCleanDate(cleanDate));
                                            } catch (Exception e) {
                                                android.util.Log.e("AutoSubmit", "Firestore push error: " + e.getMessage());
                                            }
                                            refreshCalendarData();
                                            Toast.makeText(HamchinhActivity.this, "Đã lưu tự động", Toast.LENGTH_SHORT).show();
                                        }

                                        // Ensure send button is enabled after finalizing
                                        if (btnAiSend != null) btnAiSend.setEnabled(true);
                                    }
                                };

                                // Show snackbar with Undo action
                                com.google.android.material.snackbar.Snackbar snackbar =
                                        com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content),
                                                message != null && !message.isEmpty() ? message : "Đang lưu giao dịch…", 5000);

                                snackbar.setAction("HOÀN TÁC", v -> {
                                    handler.removeCallbacks(saveRunnable);
                                    // Đổ dữ liệu ngược lại ra form để user chỉnh sửa
                                    fillAiDataToForm(String.valueOf(amount), note, category, date);
                                    Toast.makeText(HamchinhActivity.this, "Đã hủy lưu tự động", Toast.LENGTH_SHORT).show();

                                    // Re-enable send button
                                    if (btnAiSend != null) btnAiSend.setEnabled(true);
                                });

                                snackbar.addCallback(new com.google.android.material.snackbar.Snackbar.Callback() {
                                    @Override
                                    public void onShown(com.google.android.material.snackbar.Snackbar sb) {
                                        // Start countdown when shown
                                        handler.postDelayed(saveRunnable, 5000);
                                    }

                                    @Override
                                    public void onDismissed(com.google.android.material.snackbar.Snackbar transientBottomBar, int event) {
                                        // If dismissed by timeout and Runnable already executed, nothing to do.
                                        // If dismissed by swipe or action, we rely on removeCallbacks in action.
                                    }
                                });

                                snackbar.show();

                            } else {
                                // Fill form for manual submit
                                fillAiDataToForm(String.valueOf(amount), note, category, date);
                                if (btnAiSend != null) btnAiSend.setEnabled(true);
                                Toast.makeText(HamchinhActivity.this, "Thông tin chưa đủ, mời bạn chỉnh sửa!", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } catch (Exception e) {
                        android.util.Log.e("AIParse", "Error parsing AI response: " + e.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(HamchinhActivity.this, "Lỗi phân tích dữ liệu AI!", Toast.LENGTH_SHORT).show();
                            if (btnAiSend != null) btnAiSend.setEnabled(true);
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(HamchinhActivity.this, "Lỗi API: " + response.message(), Toast.LENGTH_SHORT).show();
                        if (btnAiSend != null) btnAiSend.setEnabled(true);
                    });
                }
            }
        });
    }

    private void fillAiDataToForm(String amount, String note, String category, String date) {
        EditText etAmount = findViewById(R.id.input_amount);
        EditText etNote = findViewById(R.id.input_note);
        TextView tvDate = findViewById(R.id.value_date);

        if (etAmount != null) etAmount.setText(amount);
        if (etNote != null) etNote.setText(note);
        if (tvDate != null) tvDate.setText(date);

        selectedCategoryName = category;
    }

    // Thiết lập tương tác lịch tùy chỉnh
    private void setupCalendarInteraction() {
        RecyclerView rvCalendar = findViewById(R.id.rv_calendar);
        TextView tvMonthTotalIncome = findViewById(R.id.tv_month_total_income);
        TextView tvMonthTotalExpense = findViewById(R.id.tv_month_total_expense);
        TextView tvMonthBalance = findViewById(R.id.tv_month_balance);
        
        // Hiển thị tháng/năm hiện tại lên thanh selector của Lịch
        TextView tvMonthYearLich = findViewById(R.id.tv_month_year_lich);
        TextView tvMonthRangeLich = findViewById(R.id.tv_month_range_lich);
        int currentMonth = currentCalendar.get(Calendar.MONTH) + 1;
        int currentYear = currentCalendar.get(Calendar.YEAR);
        if (tvMonthYearLich != null) tvMonthYearLich.setText(String.format(Locale.getDefault(), "%02d/%d", currentMonth, currentYear));
        if (tvMonthRangeLich != null) {
            int lastDay = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            tvMonthRangeLich.setText(String.format(Locale.getDefault(), "(01/%02d - %02d/%02d)", currentMonth, lastDay, currentMonth));
        }

        // Cài đặt nút bấm Tiến/Lùi tháng và Chọn ngày cho Lịch
        View btnPrev = findViewById(R.id.btn_prev_month_lich);
        View btnNext = findViewById(R.id.btn_next_month_lich);
        View dateContainer = findViewById(R.id.date_container_lich);
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateLichDateUI();
        });
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateLichDateUI();
        });
        if (dateContainer != null) dateContainer.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, day) -> {
                currentCalendar.set(year, month, day);
                updateLichDateUI();
            }, currentYear, currentMonth - 1, currentCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        if (rvCalendar == null) return;

        // 1. Cập nhật Summary cột trên lịch
        long[] summary = dbHelper.getSummaryByMonth(
                currentCalendar.get(Calendar.MONTH) + 1,
                currentCalendar.get(Calendar.YEAR)
        );

        if (tvMonthTotalIncome != null) tvMonthTotalIncome.setText("+" + summary[0] + "đ");
        if (tvMonthTotalExpense != null) tvMonthTotalExpense.setText("-" + summary[1] + "đ");
        if (tvMonthBalance != null) {
            String balance = (summary[2] >= 0 ? "+" : "") + summary[2] + "đ";
            tvMonthBalance.setText(balance);
        }

        // 2. Tính toán danh sách các ngày trong tháng để đổ vào Grid
        List<CalendarAdapter.DayModel> daysList = new ArrayList<>();
        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        // Lấy thứ của ngày đầu tiên (Chủ nhật là 1, Thứ hai là 2...)
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        // Lùi lại để luôn bắt đầu từ Chủ Nhật (vị trí 1 trong Grid 7 cột nếu CN là đầu tuần)
        cal.add(Calendar.DAY_OF_MONTH, -(firstDayOfWeek - 1));

        // Hiển thị 6 tuần (42 ngày) để đảm bảo luôn lấp đầy Grid
        for (int i = 0; i < 42; i++) {
            int d = cal.get(Calendar.DAY_OF_MONTH);
            int m = cal.get(Calendar.MONTH);
            boolean isCurrentMonth = (m == currentCalendar.get(Calendar.MONTH));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String dateKey = sdf.format(cal.getTime());

            long income = dbHelper.getSumByDate(dateKey, 1);
            long expense = dbHelper.getSumByDate(dateKey, 0);

            daysList.add(new CalendarAdapter.DayModel(String.valueOf(d), income, expense, isCurrentMonth));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        CalendarAdapter adapter = new CalendarAdapter(daysList, new CalendarAdapter.OnDayClickListener() {
            @Override
            public void onDayClick(CalendarAdapter.DayModel day) {
                // Khi nhấn 1 lần: Cập nhật danh sách ở dưới
                String selectedDate = (Integer.parseInt(day.day) < 10 ? "0" : "") + day.day + "/" +
                                    (currentCalendar.get(Calendar.MONTH) + 1 < 10 ? "0" : "") + (currentCalendar.get(Calendar.MONTH) + 1) + "/" +
                                    currentCalendar.get(Calendar.YEAR);
                updateHistoryList(selectedDate);
            }

            @Override
            public void onDayDoubleClick(CalendarAdapter.DayModel day) {
                // Khi nhấn đúp: Mở popup nhập liệu
                showDayPopup(day.day);
            }
        });
        rvCalendar.setAdapter(adapter);

        // Hiển thị danh sách của cả tháng lúc mới vào
        updateHistoryList(null);
    }

    private void updateLichDateUI() {
        setupCalendarInteraction();
    }

    // Hàm làm mới toàn bộ dữ liệu trên màn hình Lịch
    private void refreshCalendarData() {
        // Cập nhật lại Grid lịch (số tiền xanh đỏ trên các ô)
        setupCalendarInteraction();
        // Cập nhật lại danh sách lịch sử ở dưới (gom nhóm theo ngày)
        updateHistoryList(null);
    }

    private void updateHistoryList(String selectedDate) {
        RecyclerView rvHistory = findViewById(R.id.rv_history);
        if (rvHistory == null) return;

        List<Transaction> transactions;
        if (selectedDate == null) {
            // Lấy toàn bộ giao dịch trong tháng
            transactions = dbHelper.getTransactionsByMonth(
                    currentCalendar.get(Calendar.MONTH) + 1,
                    currentCalendar.get(Calendar.YEAR)
            );
        } else {
            // Lấy giao dịch theo ngày cụ thể
            transactions = dbHelper.getTransactionsByDate(selectedDate);
        }

        List<HistoryAdapter.HistoryItem> historyItems = groupTransactionsByDate(transactions);
        rvHistory.setAdapter(new HistoryAdapter(historyItems));
    }

    private List<HistoryAdapter.HistoryItem> groupTransactionsByDate(List<Transaction> transactions) {
        List<HistoryAdapter.HistoryItem> list = new ArrayList<>();
        if (transactions.isEmpty()) return list;

        // Group by Date using LinkedHashMap to preserve order
        java.util.LinkedHashMap<String, List<Transaction>> map = new java.util.LinkedHashMap<>();
        for (Transaction t : transactions) {
            String date = t.getDate();
            if (!map.containsKey(date)) {
                map.put(date, new ArrayList<>());
            }
            map.get(date).add(t);
        }

        // Flatten map to list with Headers
        for (String date : map.keySet()) {
            List<Transaction> transList = map.get(date);
            long dayTotal = 0;
            for (Transaction t : transList) {
                dayTotal += (t.getType() == 1 ? t.getAmount() : -t.getAmount());
            }

            // Thêm Header thông báo ngày và tổng thu chi trong ngày
            list.add(new HistoryAdapter.HistoryItem(date, dayTotal));

            // Thêm các giao dịch lẻ
            for (Transaction t : transList) {
                list.add(new HistoryAdapter.HistoryItem(t));
            }
        }
        return list;
    }

    private void handleDayClick(TextView dayView) {
        long now = System.currentTimeMillis();
        boolean isDouble = (now - lastClickTime) < 300 && lastSelectedDayView == dayView;
        // Highlight selection (light green)
        if (lastSelectedDayView != null && lastSelectedDayView != dayView) {
            lastSelectedDayView.setBackgroundColor(0x00000000); // clear
        }
        dayView.setBackgroundColor(0xFFA5D6A7); // light green
        lastSelectedDayView = dayView;
        lastClickTime = now;
        if (isDouble) {
            // Show popup for double click
            String dateText = dayView.getText().toString();
            showDayPopup(dateText);
        }
    }

    private void showDayPopup(String day) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View popupView = getLayoutInflater().inflate(R.layout.day_popup, null);

        bottomSheet.setContentView(popupView);

        android.view.View decorView = bottomSheet.getWindow().getDecorView();
        androidx.lifecycle.ViewTreeLifecycleOwner.set(decorView, this);
        androidx.lifecycle.ViewTreeViewModelStoreOwner.set(decorView, this);
        androidx.savedstate.ViewTreeSavedStateRegistryOwner.set(decorView, this);

        // Nút Bỏ qua
        View btnCancel = popupView.findViewById(R.id.popup_btn_cancel);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> bottomSheet.dismiss());

        // Toggle Chuyển sang Tiền Thu
        TextView btnTienThu = popupView.findViewById(R.id.popup_btn_tien_thu);
        if (btnTienThu != null) {
            btnTienThu.setOnClickListener(v -> {
                bottomSheet.dismiss();
                showTienThuPopup(day);
            });
        }

        // TextView hiển thị ngày
        TextView tvDate = popupView.findViewById(R.id.popup_value_date);
        Calendar cal = (Calendar) currentCalendar.clone();
        if (tvDate != null) {
            try {
                int d = Integer.parseInt(day.trim());
                cal.set(Calendar.DAY_OF_MONTH, d);
            } catch (Exception ignored) {}
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy (E)", new Locale("vi", "VN"));
            tvDate.setText(sdf.format(cal.getTime()));
            tvDate.setOnClickListener(v -> {
                int year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH), dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
                new DatePickerDialog(this, (view, y, m, dom) -> {
                    cal.set(y, m, dom);
                    tvDate.setText(sdf.format(cal.getTime()));
                }, year, month, dayOfMonth).show();
            });
        }

        setupPopupCategorySelection(popupView);

        View btnSubmit = popupView.findViewById(R.id.popup_btn_submit);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                EditText etAmount = popupView.findViewById(R.id.popup_input_amount);
                EditText etNote = popupView.findViewById(R.id.popup_input_note);

                String amountStr = etAmount != null ? etAmount.getText().toString().trim() : "";
                String noteStr = etNote != null ? etNote.getText().toString().trim() : "Không có ghi chú";
                String dateStr = tvDate != null ? tvDate.getText().toString() : "";

                if (selectedCategoryName.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn danh mục!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(amountStr)) {
                    Toast.makeText(this, "Vui lòng nhập số tiền!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String cleanDate = dateStr.contains(" ") ? dateStr.split(" ")[0] : dateStr;
                long amount = Long.parseLong(amountStr);

                // Lưu vào Database (Type 0 là Chi)
                Transaction t = new Transaction(amount, noteStr, selectedCategoryName, cleanDate, 0);
                attachCurrentDeviceIdentity(t);
                if (dbHelper.addTransaction(t) != -1) {
                    Toast.makeText(this, "Đã lưu khoản chi!", Toast.LENGTH_SHORT).show();
                    refreshCalendarData();
                    bottomSheet.dismiss();
                } else {
                    Toast.makeText(this, "Lỗi khi lưu!", Toast.LENGTH_SHORT).show();
                }
            });
        }
        bottomSheet.show();
    }

    private void showTienThuPopup(String day) {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View popupView = getLayoutInflater().inflate(R.layout.tienthu_popup, null);

        bottomSheet.setContentView(popupView);

        android.view.View decorView = bottomSheet.getWindow().getDecorView();
        androidx.lifecycle.ViewTreeLifecycleOwner.set(decorView, this);
        androidx.lifecycle.ViewTreeViewModelStoreOwner.set(decorView, this);
        androidx.savedstate.ViewTreeSavedStateRegistryOwner.set(decorView, this);

        // Nút Bỏ qua
        View btnCancel = popupView.findViewById(R.id.btn_cancel);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> bottomSheet.dismiss());

        // Toggle Chuyển sang Tiền Chi
        View btnTienChi = popupView.findViewById(R.id.btn_tien_chi);
        if (btnTienChi != null) {
            btnTienChi.setOnClickListener(v -> {
                bottomSheet.dismiss();
                showDayPopup(day);
            });
        }

        // TextView hiển thị ngày
        TextView tvDate = popupView.findViewById(R.id.value_date);
        Calendar cal = (Calendar) currentCalendar.clone();
        if (tvDate != null) {
            try {
                int d = Integer.parseInt(day.trim());
                cal.set(Calendar.DAY_OF_MONTH, d);
            } catch (Exception ignored) {}
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy (E)", new Locale("vi", "VN"));
            tvDate.setText(sdf.format(cal.getTime()));
            tvDate.setOnClickListener(v -> {
                int year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH), dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
                new DatePickerDialog(this, (view, y, m, dom) -> {
                    cal.set(y, m, dom);
                    tvDate.setText(sdf.format(cal.getTime()));
                }, year, month, dayOfMonth).show();
            });
        }

        // Thiết lập chọn danh mục cho Tiền Thu
        setupTienThuCategorySelection(popupView);

        View btnSubmit = popupView.findViewById(R.id.btn_submit);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                EditText etAmount = popupView.findViewById(R.id.input_amount);
                EditText etNote = popupView.findViewById(R.id.input_note);

                String amountStr = etAmount != null ? etAmount.getText().toString().trim() : "";
                String noteStr = etNote != null ? etNote.getText().toString().trim() : "Không có ghi chú";
                String dateStr = tvDate != null ? tvDate.getText().toString() : "";

                if (selectedCategoryName.isEmpty()) {
                    Toast.makeText(this, "Vui lòng chọn danh mục!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(amountStr)) {
                    Toast.makeText(this, "Vui lòng nhập số tiền!", Toast.LENGTH_SHORT).show();
                    return;
                }

                String cleanDate = dateStr.contains(" ") ? dateStr.split(" ")[0] : dateStr;
                long amount = Long.parseLong(amountStr);

                // Lưu vào Database (Type 1 là Thu)
                Transaction t = new Transaction(amount, noteStr, selectedCategoryName, cleanDate, 1);
                attachCurrentDeviceIdentity(t);
                if (dbHelper.addTransaction(t) != -1) {
                    Toast.makeText(this, "Đã lưu khoản thu!", Toast.LENGTH_SHORT).show();
                    refreshCalendarData();
                    bottomSheet.dismiss();
                } else {
                    Toast.makeText(this, "Lỗi khi lưu!", Toast.LENGTH_SHORT).show();
                }
            });
        }
        bottomSheet.show();
    }

    private void setupTienThuCategorySelection(View popupView) {
        int[] catIds = {R.id.item_salary, R.id.item_allowance, R.id.item_bonus, R.id.item_side_income, R.id.item_investment, R.id.item_temp_income};
        for (int id : catIds) {
            View cat = popupView.findViewById(id);
            if (cat != null) {
                cat.setOnClickListener(v -> {
                    for (int otherId : catIds) {
                        updateSelectionVisual(popupView.findViewById(otherId), false);
                    }
                    updateSelectionVisual(v, true);
                    selectedCategoryName = getTienThuCategoryName(id);
                });
            }
        }
    }

    private String getTienThuCategoryName(int id) {
        if (id == R.id.item_salary) return "Tiền lương";
        if (id == R.id.item_allowance) return "Tiền phụ cấp";
        if (id == R.id.item_bonus) return "Tiền thưởng";
        if (id == R.id.item_side_income) return "Thu nhập phụ";
        if (id == R.id.item_investment) return "Đầu tư";
        if (id == R.id.item_temp_income) return "Thu nhập tạm thời";
        return "Khác";
    }

    private int layoutId_current = R.layout.activity_main;
    private android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    private List<Transaction> lastFilteredTransactions = new ArrayList<>();
    private SharedWalletSearchViewModel searchViewModel;
    private SearchAdapter searchAdapter;
    private boolean searchCollectorBound = false;
    private boolean isUserFilterChipBinding = false;

    // ═══════════════════════════════════════════════════════════
    // PDF EXPORT — Storage Access Framework Launcher
    // ═══════════════════════════════════════════════════════════
    private final ActivityResultLauncher<String> createPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri != null) {
                    generatePdfAndSave(uri);
                }
            });

    private void generatePdfAndSave(android.net.Uri uri) {
        if (lastFilteredTransactions == null || lastFilteredTransactions.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu để xuất!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang khởi tạo tệp tin PDF...", Toast.LENGTH_SHORT).show();

        final List<Transaction> dataToExport = new ArrayList<>(lastFilteredTransactions);

        // Call the clean Kotlin helper
        com.example.sothuchi.data.PdfGenerator.savePdfAsync(
                this,
                this, // LifecycleOwner
                uri,
                dataToExport,
                success -> {
                    if (success) {
                        Toast.makeText(this, "Xuất PDF thành công!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Lỗi khi tạo PDF", Toast.LENGTH_SHORT).show();
                    }
                    return kotlin.Unit.INSTANCE;
                }
        );
    }

    // ═══════════════════════════════════════════════════════════
    // TÌM KIẾM GIAO DỊCH — Search Screen Logic
    // ═══════════════════════════════════════════════════════════

    private void setupSearchScreen() {
        if (searchViewModel == null) {
            searchViewModel = new ViewModelProvider(this).get(SharedWalletSearchViewModel.class);
        }
        if (searchAdapter == null) {
            searchAdapter = new SearchAdapter();
        }

        RecyclerView rvResults = findViewById(R.id.rv_search_results);
        if (rvResults != null) {
            rvResults.setAdapter(searchAdapter);
        }

        if (!searchCollectorBound) {
            searchCollectorBound = true;
            SearchScreenCollector.collect(this, searchViewModel, state -> {
                runOnUiThread(() -> renderSearchState(state));
            });
        }

        // Tìm kiếm nội dung (Export button)
        View btnExport = findViewById(R.id.btn_search_export);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                com.example.sothuchi.ui.ExportPdfBottomSheet.Companion.newInstance(() -> {
                    // Start the file creation intent
                    String fileName = "BaoCao_GiaoDich_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new java.util.Date()) + ".pdf";
                    createPdfLauncher.launch(fileName);
                    return kotlin.Unit.INSTANCE;
                }).show(getSupportFragmentManager(), com.example.sothuchi.ui.ExportPdfBottomSheet.TAG);
            });
        }

        // EditText + Clear Button
        EditText etSearch = findViewById(R.id.et_search_query);
        View btnClear = findViewById(R.id.btn_search_clear);
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chip_user_filter_group);

        if (btnClear != null && etSearch != null) {
            btnClear.setOnClickListener(v -> {
                etSearch.setText("");
                btnClear.setVisibility(View.GONE);
            });
        }

        // 5. TextWatcher với Debounce 300ms
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // Hiển thị/ẩn nút clear
                    if (btnClear != null) {
                        btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }

                    // Debounce: Hủy tìm kiếm trước đó, đặt lại 300ms
                    if (searchRunnable != null) {
                        searchHandler.removeCallbacks(searchRunnable);
                    }
                    searchRunnable = () -> {
                        String query = s.toString().trim();
                        if (searchViewModel != null) {
                            searchViewModel.setQuery(query);
                        }
                    };
                    searchHandler.postDelayed(searchRunnable, 300);
                }
            });
        }

        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (isUserFilterChipBinding) return;
                if (searchViewModel == null) return;

                if (checkedIds == null || checkedIds.isEmpty()) {
                    searchViewModel.setSelectedUser(SearchScreenUiState.ALL_USERS);
                    return;
                }

                View checkedView = group.findViewById(checkedIds.get(0));
                if (checkedView instanceof com.google.android.material.chip.Chip) {
                    String user = ((com.google.android.material.chip.Chip) checkedView).getText().toString();
                    searchViewModel.setSelectedUser(user);
                }
            });
        }
    }

    private void renderSearchState(SearchScreenUiState state) {
        if (layoutId_current != R.layout.layout_search) return;

        this.lastFilteredTransactions = new ArrayList<>(state.getFilteredTransactions());

        if (searchAdapter != null) {
            searchAdapter.submitList(state.getItems());
        }

        updateSearchSummary(state);
        bindUserFilterChips(state);
        updateSearchEmptyState(state);
    }

    private void updateSearchSummary(SearchScreenUiState state) {
        TextView tvIncome = findViewById(R.id.tv_search_income);
        TextView tvExpense = findViewById(R.id.tv_search_expense);
        TextView tvTotal = findViewById(R.id.tv_search_total);

        java.text.NumberFormat fmt = java.text.NumberFormat.getInstance(new Locale("vi", "VN"));

        if (tvIncome != null) {
            tvIncome.setText("+" + fmt.format(state.getTotalIncome()) + "đ");
        }
        if (tvExpense != null) {
            tvExpense.setText("-" + fmt.format(state.getTotalExpense()) + "đ");
        }
        if (tvTotal != null) {
            String prefix = state.getTotalNet() >= 0 ? "+" : "";
            tvTotal.setText(prefix + fmt.format(state.getTotalNet()) + "đ");
            tvTotal.setTextColor(state.getTotalNet() >= 0
                    ? ContextCompat.getColor(this, R.color.saturday_blue)
                    : ContextCompat.getColor(this, R.color.sunday_red));
        }
    }

    private void bindUserFilterChips(SearchScreenUiState state) {
        com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chip_user_filter_group);
        if (chipGroup == null) return;

        isUserFilterChipBinding = true;
        chipGroup.removeAllViews();

        for (String user : state.getUsers()) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(user);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setId(View.generateViewId());
            chip.setChecked(user.equals(state.getSelectedUser()));
            chipGroup.addView(chip);
        }

        isUserFilterChipBinding = false;
    }

    private void updateSearchEmptyState(SearchScreenUiState state) {
        View emptyLayout = findViewById(R.id.layout_search_empty);
        RecyclerView rvResults = findViewById(R.id.rv_search_results);
        TextView tvEmptyMsg = findViewById(R.id.tv_search_empty_msg);

        boolean empty = state.getFilteredTransactions().isEmpty();
        if (rvResults != null) rvResults.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyLayout != null) emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty && tvEmptyMsg != null) {
            tvEmptyMsg.setText(state.getQuery().isEmpty()
                    ? "Nhập từ khóa để tìm kiếm giao dịch"
                    : "Không tìm thấy giao dịch phù hợp");
        }
    }

    /**
     * Thực thi logic lọc và hiển thị kết quả tìm kiếm.
     *
     * Thuật toán:
     * 1. Chuẩn hóa query (xóa dấu tiếng Việt, lowercase)
     * 2. Lọc transactions theo: category, note, date
     * 3. Nhóm kết quả theo ngày
     * 4. Tính tổng thu nhập/chi phí/tổng cộng
     * 5. Cập nhật Summary Bar + RecyclerView
     */
    private void performSearch(String query, List<Transaction> allTransactions, SearchAdapter adapter) {
        String normalizedQuery = VietUtils.removeAccents(query);

        // ── Bước 1: Lọc giao dịch ──
        List<Transaction> filtered = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (normalizedQuery.isEmpty()) {
                filtered.add(t);
            } else {
                boolean matchCategory = VietUtils.containsIgnoreAccent(t.getCategory(), query);
                boolean matchNote = VietUtils.containsIgnoreAccent(t.getNote(), query);
                boolean matchDate = VietUtils.containsIgnoreAccent(t.getDate(), query);
                if (matchCategory || matchNote || matchDate) {
                    filtered.add(t);
                }
            }
        }
        // Lưu trữ danh sách lọc mới nhất để xuất PDF
        this.lastFilteredTransactions = new ArrayList<>(filtered);

        // ── Bước 2: Tính tổng Thu nhập / Chi phí ──
        long totalIncome = 0;
        long totalExpense = 0;
        for (Transaction t : filtered) {
            if (t.getType() == 1) {
                totalIncome += t.getAmount();
            } else {
                totalExpense += t.getAmount();
            }
        }
        long totalNet = totalIncome - totalExpense;

        // Cập nhật Summary Bar
        TextView tvIncome = findViewById(R.id.tv_search_income);
        TextView tvExpense = findViewById(R.id.tv_search_expense);
        TextView tvTotal = findViewById(R.id.tv_search_total);

        java.text.NumberFormat fmt = java.text.NumberFormat.getInstance(new Locale("vi", "VN"));

        if (tvIncome != null) {
            tvIncome.setText("+" + fmt.format(totalIncome) + "đ");
        }
        if (tvExpense != null) {
            tvExpense.setText("-" + fmt.format(totalExpense) + "đ");
        }
        if (tvTotal != null) {
            String prefix = totalNet >= 0 ? "+" : "";
            tvTotal.setText(prefix + fmt.format(totalNet) + "đ");
            tvTotal.setTextColor(totalNet >= 0
                    ? ContextCompat.getColor(this, R.color.saturday_blue)
                    : ContextCompat.getColor(this, R.color.sunday_red));
        }

        // ── Bước 3: Nhóm kết quả theo ngày ──
        // Sắp xếp theo ngày giảm dần
        java.text.SimpleDateFormat sdfParse = new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Collections.sort(filtered, (a, b) -> {
            try {
                java.util.Date dateA = sdfParse.parse(a.getDate());
                java.util.Date dateB = sdfParse.parse(b.getDate());
                if (dateA != null && dateB != null) return dateB.compareTo(dateA);
            } catch (Exception e) { /* ignore */ }
            return 0;
        });

        // Nhóm theo ngày
        java.util.LinkedHashMap<String, List<Transaction>> grouped = new java.util.LinkedHashMap<>();
        for (Transaction t : filtered) {
            String date = t.getDate() != null ? t.getDate() : "";
            if (!grouped.containsKey(date)) {
                grouped.put(date, new ArrayList<>());
            }
            grouped.get(date).add(t);
        }

        // Tạo danh sách phẳng (flat list) cho adapter
        List<SearchListItem> items = new ArrayList<>();
        java.text.SimpleDateFormat sdfDay = new java.text.SimpleDateFormat("dd/MM/yyyy (E)", new Locale("vi", "VN"));

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            String dateStr = entry.getKey();
            List<Transaction> dayTransactions = entry.getValue();

            // Tính tổng trong ngày
            long dayTotal = 0;
            for (Transaction t : dayTransactions) {
                if (t.getType() == 1) dayTotal += t.getAmount();
                else dayTotal -= t.getAmount();
            }

            // Tạo header text với ngày trong tuần
            String headerText = dateStr;
            try {
                java.util.Date parsedDate = sdfParse.parse(dateStr);
                if (parsedDate != null) {
                    headerText = sdfDay.format(parsedDate);
                }
            } catch (Exception e) { /* giữ nguyên dateStr */ }

            // Header hiển thị: "28/03/2026 (Th 7) (-5,000đ)"
            String dayTotalStr = "(" + (dayTotal >= 0 ? "+" : "") + fmt.format(dayTotal) + "đ)";
            String fullHeader = headerText + " " + dayTotalStr;

            items.add(new SearchListItem.Header(fullHeader, dayTotal));

            for (Transaction t : dayTransactions) {
                items.add(new SearchListItem.TransactionRow(t));
            }
        }

        // ── Bước 4: Cập nhật RecyclerView ──
        adapter.submitList(items);

        // ── Bước 5: Xử lý Empty State ──
        View emptyLayout = findViewById(R.id.layout_search_empty);
        RecyclerView rvResults = findViewById(R.id.rv_search_results);
        TextView tvEmptyMsg = findViewById(R.id.tv_search_empty_msg);

        if (filtered.isEmpty()) {
            if (rvResults != null) rvResults.setVisibility(View.GONE);
            if (emptyLayout != null) emptyLayout.setVisibility(View.VISIBLE);
            if (tvEmptyMsg != null) {
                tvEmptyMsg.setText(query.isEmpty()
                        ? "Nhập từ khóa để tìm kiếm giao dịch"
                        : "Không tìm thấy giao dịch phù hợp");
            }
        } else {
            if (rvResults != null) rvResults.setVisibility(View.VISIBLE);
            if (emptyLayout != null) emptyLayout.setVisibility(View.GONE);
        }
    }


    private void updateBudgetDateUI() {
        androidx.compose.ui.platform.ComposeView composeView = findViewById(R.id.compose_view_budget);
        if (composeView == null) return;

        int month = currentCalendar.get(Calendar.MONTH) + 1;
        int year = currentCalendar.get(Calendar.YEAR);
        String monthYear = String.format(Locale.getDefault(), "%02d/%d", month, year);
        int lastDay = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        String dateRange = String.format(Locale.getDefault(), "(01/%02d - %02d/%02d)", month, lastDay, month);

        List<Transaction> transactions = dbHelper.getTransactionsByMonth(month, year);
        long totalIncome = 0;
        long totalExpense = 0;
        Map<String, Long> categorySpent = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.getType() == 1) {
                totalIncome += t.getAmount();
            } else {
                totalExpense += t.getAmount();
                String cat = t.getCategory();
                categorySpent.put(cat, categorySpent.getOrDefault(cat, 0L) + t.getAmount());
            }
        }

        List<BudgetItem> budgetItems = new ArrayList<>();
        // Total Budget Item (using formula: Budget = Total Income)
        budgetItems.add(new BudgetItem("Tổng ngân sách", totalIncome, totalExpense, 0xFF1EBE5DL));

        // Category Items
        for (Map.Entry<String, Long> entry : categorySpent.entrySet()) {
            // Mocking category budget as 25% of total income for visualization if not set
            long catBudget = totalIncome / 4;
            budgetItems.add(new BudgetItem(entry.getKey(), catBudget, entry.getValue(), 0xFF1EBE5DL));
        }

        BudgetBridge.setupBudgetScreen(
            composeView,
            monthYear,
            dateRange,
            budgetItems,
            () -> { currentCalendar.add(Calendar.MONTH, -1); updateBudgetDateUI(); },
            () -> { currentCalendar.add(Calendar.MONTH, 1); updateBudgetDateUI(); },
            () -> {
                new DatePickerDialog(this, (view, y, m, d) -> {
                    currentCalendar.set(y, m, d);
                    updateBudgetDateUI();
                }, currentCalendar.get(Calendar.YEAR), currentCalendar.get(Calendar.MONTH), currentCalendar.get(Calendar.DAY_OF_MONTH)).show();
            }
        );
    }

    private void setupPopupCategorySelection(View popupView) {
        int[] popupCategoryIds = {
                R.id.popup_au, R.id.popup_cthn, R.id.popup_qo, R.id.popup_mp,
                R.id.popup_pgl, R.id.popup_yt, R.id.popup_gd, R.id.popup_td,
                R.id.popup_dl, R.id.popup_pll, R.id.popup_tn, R.id.popup_cs
        };
        for (int id : popupCategoryIds) {
            View catView = popupView.findViewById(id);
            if (catView != null) {
                catView.setOnClickListener(v -> {
                    for (int otherId : popupCategoryIds) {
                        updateSelectionVisual(popupView.findViewById(otherId), false);
                    }
                    updateSelectionVisual(v, true);
                    selectedCategoryName = getCategoryNameFromPopupId(id);
                });
            }
        }
    }

    private void updateSelectionVisual(View view, boolean isSelected) {
        if (view instanceof com.google.android.material.card.MaterialCardView) {
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) view;
            if (isSelected) {
                card.setStrokeWidth(6);
                card.setStrokeColor(getResources().getColor(R.color.primary_green));
                card.setCardElevation(dpToPx(4));
            } else {
                card.setStrokeWidth(1);
                card.setStrokeColor(0xFFEEEEEE);
                card.setCardElevation(0);
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String getCategoryNameFromPopupId(int id) {
        if (id == R.id.popup_au) return "Ăn uống";
        if (id == R.id.popup_cthn) return "Chi tiêu hàng ngày";
        if (id == R.id.popup_qo) return "Quần áo";
        if (id == R.id.popup_mp) return "Mỹ phẩm";
        if (id == R.id.popup_pgl) return "Phí giao lưu";
        if (id == R.id.popup_yt) return "Y tế";
        if (id == R.id.popup_gd) return "Giáo dục";
        if (id == R.id.popup_td) return "Tiền điện";
        if (id == R.id.popup_dl) return "Du lịch";
        if (id == R.id.popup_pll) return "Phí liên lạc";
        if (id == R.id.popup_tn) return "Tiền nhà";
        return "Khác";
    }

    // Các hàm phụ trợ cũ đã xóa vì không còn dùng ID cứng (R.id.au, R.id.cthn...)

    private void setupBctnYearlyReport() {
        // 1. Nút Back về màn hình Khác
        View btnBack = findViewById(R.id.btn_back_to_khac);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> showLayout(R.layout.khac));
        }

        // Cài đặt nút chọn năm
        View btnPrevYear = findViewById(R.id.btn_prev_year_bctn);
        View btnNextYear = findViewById(R.id.btn_next_year_bctn);
        View yearContainer = findViewById(R.id.year_container_bctn);

        if (btnPrevYear != null) {
            btnPrevYear.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, -1);
                updateBctnYearlyData();
            });
        }

        if (btnNextYear != null) {
            btnNextYear.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, 1);
                updateBctnYearlyData();
            });
        }

        if (yearContainer != null) {
            yearContainer.setOnClickListener(v -> {
                new DatePickerDialog(this, (view, year, month, day) -> {
                    currentCalendar.set(Calendar.YEAR, year);
                    updateBctnYearlyData();
                }, currentCalendar.get(Calendar.YEAR), 0, 1).show();
            });
        }

        // Chuyển Tab
        View tabThuNhap = findViewById(R.id.tab_bctn_thu_nhap);
        if (tabThuNhap != null) tabThuNhap.setOnClickListener(v -> showLayout(R.layout.bctn_thunhap));
        View tabTong = findViewById(R.id.tab_bctn_tong);
        if (tabTong != null) tabTong.setOnClickListener(v -> showLayout(R.layout.bctn_tong));

        // 3. Khởi tạo cấu hình mặc định biểu đồ BarChart
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly);
        if (barChart != null) {
            barChart.getDescription().setEnabled(false);
            barChart.setDrawGridBackground(false);
            barChart.getLegend().setEnabled(false);
            barChart.setTouchEnabled(true);
            barChart.setScaleEnabled(false);
            barChart.setPinchZoom(false);

            barChart.getXAxis().setDrawGridLines(false);
            barChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            barChart.getAxisLeft().setDrawGridLines(false);
            barChart.getAxisRight().setEnabled(false);
        }

        // Lấy dữ liệu và đắp lên UI
        updateBctnYearlyData();
    }

    private void updateBctnYearlyData() {
        int currentYear = currentCalendar.get(Calendar.YEAR);
        TextView tvYearDisplay = findViewById(R.id.tv_year_display_bctn);
        if (tvYearDisplay != null) tvYearDisplay.setText(String.valueOf(currentYear));

        // Lấy dữ liệu 1 năm, chia thành 12 tháng (Demo Logic Lấy DB)
        List<Transaction> yearlyTransactions = dbHelper.getTransactionsByYear(currentYear);
        long[] monthlyExpenses = new long[12];
        long totalExpense = 0;

        for (Transaction t : yearlyTransactions) {
            if (t.getType() == 0) { // Lọc chi tiêu
                try {
                    String dateStr = t.getDate();
                    String[] parts = dateStr.split(" ")[0].split("/");
                    int monthIndex = Integer.parseInt(parts[1]) - 1;
                    monthlyExpenses[monthIndex] += t.getAmount();
                    totalExpense += t.getAmount();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Tính toán tháng để chia trung bình
        int currentSysYear = Calendar.getInstance().get(Calendar.YEAR);
        int monthsPassed = 12;
        if (currentYear == currentSysYear) {
            monthsPassed = Calendar.getInstance().get(Calendar.MONTH) + 1; // Month is 0-indexed
        } else if (currentYear > currentSysYear) {
            monthsPassed = 1; // Tránh chia 0 hoặc âm tương lai
        }
        long averageExpense = totalExpense / monthsPassed;
        
        // Cập nhật khối thống kê
        TextView tvTong = findViewById(R.id.tv_bctn_tong);
        TextView tvTrungBinh = findViewById(R.id.tv_bctn_trung_binh);
        if (tvTong != null) tvTong.setText("-" + String.format(Locale.getDefault(), "%,dđ", totalExpense));
        if (tvTrungBinh != null) tvTrungBinh.setText("-" + String.format(Locale.getDefault(), "%,dđ", averageExpense));

        // Đổ Data BarChart
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly);
        if (barChart != null) {
            List<com.github.mikephil.charting.data.BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                entries.add(new com.github.mikephil.charting.data.BarEntry(i, monthlyExpenses[i]));
            }

            com.github.mikephil.charting.data.BarDataSet dataSet = new com.github.mikephil.charting.data.BarDataSet(entries, "Chi tiêu");
            dataSet.setColor(Color.parseColor("#2196F3"));
            dataSet.setDrawValues(false);

            com.github.mikephil.charting.data.BarData barData = new com.github.mikephil.charting.data.BarData(dataSet);
            barChart.setData(barData);

            com.github.mikephil.charting.formatter.ValueFormatter formatter = new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "T" + ((int) value + 1);
                }
            };
            barChart.getXAxis().setValueFormatter(formatter);
            barChart.getXAxis().setLabelCount(12);
            barChart.animateY(800);
            barChart.invalidate();
        }

        // Đổ Data List Months
        LinearLayout listContainer = findViewById(R.id.list_months_container_bctn);
        if (listContainer != null) {
            listContainer.removeAllViews();
            for (int i = 0; i < 12; i++) {
                View itemView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                TextView text1 = itemView.findViewById(android.R.id.text1);
                TextView text2 = itemView.findViewById(android.R.id.text2);

                text1.setText("Tháng " + (i + 1));
                text1.setTextColor(Color.BLACK);
                text1.setTypeface(null, Typeface.BOLD);
                text1.setTextSize(16);

                text2.setText(String.format(Locale.getDefault(), "%,dđ", monthlyExpenses[i]));
                text2.setGravity(Gravity.END);
                text2.setTextColor(monthlyExpenses[i] > 0 ? Color.BLACK : Color.GRAY);
                text2.setTextSize(16);

                itemView.setPadding(60, 40, 60, 40);

                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));

                listContainer.addView(itemView);
                listContainer.addView(divider);
            }
        }
    }

    private void setupBctnYearlyIncomeReport() {
        // 1. Nút Back về màn hình Khác
        View btnBack = findViewById(R.id.btn_back_to_khac_in);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> showLayout(R.layout.khac));
        }

        // Cài đặt nút chọn năm
        View btnPrevYear = findViewById(R.id.btn_prev_year_bctn_in);
        View btnNextYear = findViewById(R.id.btn_next_year_bctn_in);
        View yearContainer = findViewById(R.id.year_container_bctn_in);

        if (btnPrevYear != null) {
            btnPrevYear.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, -1);
                updateBctnYearlyIncomeData();
            });
        }

        if (btnNextYear != null) {
            btnNextYear.setOnClickListener(v -> {
                currentCalendar.add(Calendar.YEAR, 1);
                updateBctnYearlyIncomeData();
            });
        }

        if (yearContainer != null) {
            yearContainer.setOnClickListener(v -> {
                new DatePickerDialog(this, (view, year, month, day) -> {
                    currentCalendar.set(Calendar.YEAR, year);
                    updateBctnYearlyIncomeData();
                }, currentCalendar.get(Calendar.YEAR), 0, 1).show();
            });
        }

        // Chuyển Tab ngược về
        View tabChiTieu = findViewById(R.id.tab_bctn_chi_tieu_in);
        if (tabChiTieu != null) tabChiTieu.setOnClickListener(v -> showLayout(R.layout.bctn_chitieu));
        View tabTongIn = findViewById(R.id.tab_bctn_tong_in);
        if (tabTongIn != null) tabTongIn.setOnClickListener(v -> showLayout(R.layout.bctn_tong));

        // Khởi tạo cấu hình mặc định biểu đồ BarChart
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly_in);
        if (barChart != null) {
            barChart.getDescription().setEnabled(false);
            barChart.setDrawGridBackground(false);
            barChart.getLegend().setEnabled(false);
            barChart.setTouchEnabled(true);
            barChart.setScaleEnabled(false);
            barChart.setPinchZoom(false);

            barChart.getXAxis().setDrawGridLines(false);
            barChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            barChart.getAxisLeft().setDrawGridLines(false);
            barChart.getAxisRight().setEnabled(false);
        }

        updateBctnYearlyIncomeData();
    }

    private void updateBctnYearlyIncomeData() {
        int currentYear = currentCalendar.get(Calendar.YEAR);
        TextView tvYearDisplay = findViewById(R.id.tv_year_display_bctn_in);
        if (tvYearDisplay != null) tvYearDisplay.setText(String.valueOf(currentYear));

        List<Transaction> yearlyTransactions = dbHelper.getTransactionsByYear(currentYear);
        long[] monthlyIncomes = new long[12];
        long totalIncome = 0;

        for (Transaction t : yearlyTransactions) {
            if (t.getType() == 1) { // Lọc thu nhập
                try {
                    String dateStr = t.getDate();
                    String[] parts = dateStr.split(" ")[0].split("/");
                    int monthIndex = Integer.parseInt(parts[1]) - 1;
                    monthlyIncomes[monthIndex] += t.getAmount();
                    totalIncome += t.getAmount();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Tính toán tháng để chia trung bình
        int currentSysYear = Calendar.getInstance().get(Calendar.YEAR);
        int monthsPassed = 12;
        if (currentYear == currentSysYear) {
            monthsPassed = Calendar.getInstance().get(Calendar.MONTH) + 1; // Month is 0-indexed
        } else if (currentYear > currentSysYear) {
            monthsPassed = 1; // Tránh chia 0 hoặc âm tương lai
        }
        long averageIncome = totalIncome / monthsPassed;

        // Cập nhật khối thống kê
        TextView tvTong = findViewById(R.id.tv_bctn_tong_in);
        TextView tvTrungBinh = findViewById(R.id.tv_bctn_trung_binh_in);
        if (tvTong != null) tvTong.setText("+" + String.format(Locale.getDefault(), "%,dđ", totalIncome));
        if (tvTrungBinh != null) tvTrungBinh.setText("+" + String.format(Locale.getDefault(), "%,dđ", averageIncome));

        // Đổ Data BarChart
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly_in);
        if (barChart != null) {
            List<com.github.mikephil.charting.data.BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                entries.add(new com.github.mikephil.charting.data.BarEntry(i, monthlyIncomes[i]));
            }

            com.github.mikephil.charting.data.BarDataSet dataSet = new com.github.mikephil.charting.data.BarDataSet(entries, "Thu nhập");
            dataSet.setColor(ContextCompat.getColor(this, R.color.saturday_blue)); // Xanh dương
            dataSet.setDrawValues(false);

            com.github.mikephil.charting.data.BarData barData = new com.github.mikephil.charting.data.BarData(dataSet);
            barChart.setData(barData);

            com.github.mikephil.charting.formatter.ValueFormatter formatter = new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "T" + ((int) value + 1);
                }
            };
            barChart.getXAxis().setValueFormatter(formatter);
            barChart.getXAxis().setLabelCount(12);
            barChart.animateY(800);
            barChart.invalidate();
        }

        // Đổ Data List Months
        LinearLayout listContainer = findViewById(R.id.list_months_container_bctn_in);
        if (listContainer != null) {
            listContainer.removeAllViews();
            for (int i = 0; i < 12; i++) {
                View itemView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                TextView text1 = itemView.findViewById(android.R.id.text1);
                TextView text2 = itemView.findViewById(android.R.id.text2);

                text1.setText("Tháng " + (i + 1));
                text1.setTextColor(Color.BLACK);
                text1.setTypeface(null, Typeface.BOLD);
                text1.setTextSize(16);

                text2.setText("+" + String.format(Locale.getDefault(), "%,dđ", monthlyIncomes[i]));
                text2.setGravity(Gravity.END);
                text2.setTextColor(monthlyIncomes[i] > 0 ? ContextCompat.getColor(this, R.color.saturday_blue) : Color.GRAY);
                text2.setTextSize(16);

                itemView.setPadding(60, 40, 60, 40);
                
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                
                listContainer.addView(itemView);
                listContainer.addView(divider);
            }
        }
    }

    private void setupBctnYearlyNetReport() {
        // Nút Back
        View btnBack = findViewById(R.id.btn_back_to_khac_all);
        if (btnBack != null) btnBack.setOnClickListener(v -> showLayout(R.layout.khac));

        // Cài đặt nút chọn năm
        View btnPrevYear = findViewById(R.id.btn_prev_year_bctn_all);
        View btnNextYear = findViewById(R.id.btn_next_year_bctn_all);
        View yearContainer = findViewById(R.id.year_container_bctn_all);

        if (btnPrevYear != null) btnPrevYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, -1);
            updateBctnYearlyNetData();
        });

        if (btnNextYear != null) btnNextYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, 1);
            updateBctnYearlyNetData();
        });

        if (yearContainer != null) yearContainer.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, day) -> {
                currentCalendar.set(Calendar.YEAR, year);
                updateBctnYearlyNetData();
            }, currentCalendar.get(Calendar.YEAR), 0, 1).show();
        });

        // Chuyển Tab
        View tabChiTieu = findViewById(R.id.tab_bctn_chi_tieu_all);
        if (tabChiTieu != null) tabChiTieu.setOnClickListener(v -> showLayout(R.layout.bctn_chitieu));
        View tabThuNhap = findViewById(R.id.tab_bctn_thu_nhap_all);
        if (tabThuNhap != null) tabThuNhap.setOnClickListener(v -> showLayout(R.layout.bctn_thunhap));

        // Biểu đồ
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly_all);
        if (barChart != null) {
            barChart.getDescription().setEnabled(false);
            barChart.setDrawGridBackground(false);
            barChart.getLegend().setEnabled(false);
            barChart.setTouchEnabled(true);
            barChart.setScaleEnabled(false);
            barChart.setPinchZoom(false);

            barChart.getXAxis().setDrawGridLines(false);
            barChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            barChart.getAxisLeft().setDrawGridLines(false);
            barChart.getAxisRight().setEnabled(false);
        }

        updateBctnYearlyNetData();
    }

    private void updateBctnYearlyNetData() {
        int currentYear = currentCalendar.get(Calendar.YEAR);
        TextView tvYearDisplay = findViewById(R.id.tv_year_display_bctn_all);
        if (tvYearDisplay != null) tvYearDisplay.setText(String.valueOf(currentYear));

        List<Transaction> yearlyTransactions = dbHelper.getTransactionsByYear(currentYear);
        long[] monthlyNets = new long[12];
        long totalIncome = 0;
        long totalExpense = 0;

        for (Transaction t : yearlyTransactions) {
            try {
                int monthIndex = Integer.parseInt(t.getDate().split(" ")[0].split("/")[1]) - 1;
                if (t.getType() == 1) monthlyNets[monthIndex] += t.getAmount();
                else monthlyNets[monthIndex] -= t.getAmount();
            } catch (Exception ignored) {}
        }

        // Tính Net & Average
        long netTotal = totalIncome - totalExpense;

        int currentSysYear = Calendar.getInstance().get(Calendar.YEAR);
        int monthsPassed = 12;
        if (currentYear == currentSysYear) {
            monthsPassed = Calendar.getInstance().get(Calendar.MONTH) + 1;
        } else if (currentYear > currentSysYear) {
            monthsPassed = 1;
        }
        long averageNet = netTotal / monthsPassed;

        // Cập nhật TextView
        TextView tvTong = findViewById(R.id.tv_bctn_tong_all);
        TextView tvTrungBinh = findViewById(R.id.tv_bctn_trung_binh_all);
        TextView tvThuNhap = findViewById(R.id.tv_bctn_thu_nhap_all);
        TextView tvChiTieu = findViewById(R.id.tv_bctn_chi_tieu_all);

        if (tvTong != null) {
            tvTong.setText((netTotal > 0 ? "+" : "") + String.format(Locale.getDefault(), "%,dđ", netTotal));
            tvTong.setTextColor(netTotal >= 0 ? ContextCompat.getColor(this, R.color.saturday_blue) : ContextCompat.getColor(this, R.color.sunday_red));
        }

        if (tvTrungBinh != null) {
            tvTrungBinh.setText((averageNet > 0 ? "+" : "") + String.format(Locale.getDefault(), "%,dđ", averageNet));
            tvTrungBinh.setTextColor(averageNet >= 0 ? ContextCompat.getColor(this, R.color.saturday_blue) : ContextCompat.getColor(this, R.color.sunday_red));
        }

        if (tvThuNhap != null) tvThuNhap.setText("+" + String.format(Locale.getDefault(), "%,dđ", totalIncome));
        if (tvChiTieu != null) tvChiTieu.setText("-" + String.format(Locale.getDefault(), "%,dđ", totalExpense));

        // Biểu đồ
        com.github.mikephil.charting.charts.BarChart barChart = findViewById(R.id.bar_chart_yearly_all);
        if (barChart != null) {
            List<com.github.mikephil.charting.data.BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                entries.add(new com.github.mikephil.charting.data.BarEntry(i, monthlyNets[i]));
            }

            com.github.mikephil.charting.data.BarDataSet dataSet = new com.github.mikephil.charting.data.BarDataSet(entries, "Tổng");
            dataSet.setColor(Color.parseColor("#FF5722"));
            dataSet.setDrawValues(false);

            com.github.mikephil.charting.data.BarData barData = new com.github.mikephil.charting.data.BarData(dataSet);
            barChart.setData(barData);

            com.github.mikephil.charting.formatter.ValueFormatter formatter = new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "T" + ((int) value + 1);
                }
            };
            barChart.getXAxis().setValueFormatter(formatter);
            barChart.getXAxis().setLabelCount(12);
            barChart.animateY(800);
            barChart.invalidate();
        }
    }

    private void setupBctdBalanceReport() {
        View btnBack = findViewById(R.id.btn_back_to_khac_balance);
        if (btnBack != null) btnBack.setOnClickListener(v -> showLayout(R.layout.khac));

        View btnPrevYear = findViewById(R.id.btn_prev_year_balance);
        View btnNextYear = findViewById(R.id.btn_next_year_balance);
        View yearContainer = findViewById(R.id.year_container_balance);

        if (btnPrevYear != null) btnPrevYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, -1);
            updateBctdBalanceData();
        });
        if (btnNextYear != null) btnNextYear.setOnClickListener(v -> {
            currentCalendar.add(Calendar.YEAR, 1);
            updateBctdBalanceData();
        });
        if (yearContainer != null) yearContainer.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, day) -> {
                currentCalendar.set(Calendar.YEAR, year);
                updateBctdBalanceData();
            }, currentCalendar.get(Calendar.YEAR), 0, 1).show();
        });

        com.github.mikephil.charting.charts.LineChart lineChart = findViewById(R.id.line_chart_balance);
        if (lineChart != null) {
            lineChart.getDescription().setEnabled(false);
            lineChart.setDrawGridBackground(false);

            com.github.mikephil.charting.components.XAxis xAxis = lineChart.getXAxis();
            xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(true);
            xAxis.setAxisLineColor(Color.parseColor("#E0E0E0"));
            xAxis.setTextColor(Color.parseColor("#757575"));
            xAxis.setTextSize(10f);

            com.github.mikephil.charting.components.YAxis leftAxis = lineChart.getAxisLeft();
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#F5F5F5"));
            leftAxis.setDrawAxisLine(false);
            leftAxis.setTextColor(Color.parseColor("#9E9E9E"));
            leftAxis.setTextSize(10f);
            leftAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) {
                    if (value == 0) return "0";
                    return String.format(Locale.getDefault(), "%,d", (long)value/1000) + "k";
                }
            });

            lineChart.getAxisRight().setEnabled(false);
            lineChart.getLegend().setEnabled(false);
            lineChart.setExtraOffsets(8f, 16f, 16f, 16f);

            CustomMarkerView mv = new CustomMarkerView(this, R.layout.marker_view);
            mv.setChartView(lineChart);
            lineChart.setMarker(mv);
        }

        updateBctdBalanceData();
    }

    private void updateBctdBalanceData() {
        int currentYear = currentCalendar.get(Calendar.YEAR);
        TextView tvTitle = findViewById(R.id.tv_title_bctd_balance);
        TextView tvYearDisplay = findViewById(R.id.tv_year_display_balance);
        if (tvTitle != null) tvTitle.setText(currentYear + " Số dư hàng năm");
        if (tvYearDisplay != null) tvYearDisplay.setText(currentYear + " (01/01 - 31/12)");

        List<Transaction> yearlyTransactions = dbHelper.getTransactionsByYear(currentYear);
        long[] monthlyNets = new long[12];
        for (Transaction t : yearlyTransactions) {
            try {
                int monthIndex = Integer.parseInt(t.getDate().split(" ")[0].split("/")[1]) - 1;
                if (t.getType() == 1) monthlyNets[monthIndex] += t.getAmount();
                else monthlyNets[monthIndex] -= t.getAmount();
            } catch (Exception ignored) {}
        }

        // Tính số dư cộng dồn (Cumulative Balance)
        float[] cumulativeBalances = new float[12];
        long rollingBalance = 0;
        for (int i = 0; i < 12; i++) {
            rollingBalance += monthlyNets[i];
            cumulativeBalances[i] = rollingBalance;
        }

        // Cập nhật LineChart
        com.github.mikephil.charting.charts.LineChart lineChart = findViewById(R.id.line_chart_balance);
        if (lineChart != null) {
            List<com.github.mikephil.charting.data.Entry> entries = new ArrayList<>();
            for (int i = 0; i < 12; i++) entries.add(new com.github.mikephil.charting.data.Entry(i, cumulativeBalances[i]));

            com.github.mikephil.charting.data.LineDataSet dataSet = new com.github.mikephil.charting.data.LineDataSet(entries, "Số dư");
            dataSet.setColor(Color.parseColor("#2196F3"));
            dataSet.setCircleColor(Color.parseColor("#1976D2"));
            dataSet.setLineWidth(3f);
            dataSet.setCircleRadius(5f);
            dataSet.setCircleHoleRadius(2.5f);
            dataSet.setDrawCircleHole(true);
            dataSet.setCircleHoleColor(Color.WHITE);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(Color.parseColor("#64B5F6"));
            dataSet.setFillAlpha(50);
            dataSet.setMode(com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER);

            dataSet.setDrawValues(false); // Ẩn số trên mỗi điểm
            dataSet.setHighlightEnabled(true); // Bật highlight khi chạm
            dataSet.setHighLightColor(Color.parseColor("#42A5F5"));
            dataSet.setHighlightLineWidth(1.5f);

            lineChart.setData(new com.github.mikephil.charting.data.LineData(dataSet));
            lineChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override public String getFormattedValue(float value) { return "T" + ((int) value + 1); }
            });
            lineChart.getXAxis().setLabelCount(12);
            lineChart.animateX(1000);
            lineChart.invalidate();
        }

        // Cập nhật danh sách 12 tháng
        LinearLayout listContainer = findViewById(R.id.list_months_container_balance);
        if (listContainer != null) {
            listContainer.removeAllViews();
            for (int i = 0; i < 12; i++) {
                View itemView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                TextView text1 = itemView.findViewById(android.R.id.text1);
                TextView text2 = itemView.findViewById(android.R.id.text2);

                text1.setText("Tháng " + (i + 1));
                text1.setTextColor(Color.DKGRAY);

                long bal = (long) cumulativeBalances[i];
                if (bal == 0 && monthlyNets[i] == 0) {
                    text2.setText("-- đ");
                    text2.setTextColor(Color.GRAY);
                } else {
                    text2.setText(String.format(Locale.getDefault(), "%,dđ", bal));
                    text2.setTextColor(bal >= 0 ? Color.BLACK : ContextCompat.getColor(this, R.color.sunday_red));
                }
                text2.setGravity(Gravity.END);
                itemView.setPadding(60, 40, 60, 40);

                View div = new View(this);
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                div.setBackgroundColor(Color.parseColor("#EEEEEE"));

                listContainer.addView(itemView);
                listContainer.addView(div);
            }
        }
    }

    class CustomMarkerView extends com.github.mikephil.charting.components.MarkerView {
        private TextView tvContent;

        public CustomMarkerView(android.content.Context context, int layoutResource) {
            super(context, layoutResource);
            tvContent = findViewById(R.id.tvContent);
        }

        @Override
        public void refreshContent(com.github.mikephil.charting.data.Entry e, com.github.mikephil.charting.highlight.Highlight highlight) {
            if (tvContent != null) {
                tvContent.setText(String.format(java.util.Locale.getDefault(), "%,dđ", (long) e.getY()));
            }
            super.refreshContent(e, highlight);
        }

        @Override
        public com.github.mikephil.charting.utils.MPPointF getOffset() {
            return new com.github.mikephil.charting.utils.MPPointF(-(getWidth() / 2f), -getHeight() - 15f);
        }
    }

    private void showExportDialog() {
        android.content.Context context = this;

        com.example.sothuchi.ui.ExportEmailBottomSheet.Companion.newInstance(() -> {
            sendReportRequestToBackend();
            return kotlin.Unit.INSTANCE;
        }).show(getSupportFragmentManager(), com.example.sothuchi.ui.ExportEmailBottomSheet.TAG);
    }

    /**
     * TASK 1: Show MaterialAlertDialog to change the device username.
     * Saves locally to SharedPreferences and updates Firestore devices collection.
     */
    private void showChangeUsernameDialog() {
        com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore store =
                new com.example.sothuchi.sharedwallet.identity.DeviceIdentityStore(this);

        com.example.sothuchi.ui.ChangeUsernameBottomSheet.Companion.newInstance(
                store.getNickname(),
                newNickname -> {
                    com.example.sothuchi.sharedwallet.data.UsernameSyncManager.changeUsername(
                            HamchinhActivity.this,
                            newNickname,
                            new com.example.sothuchi.sharedwallet.data.UsernameSyncManager.Callback() {
                                @Override
                                public void onSuccess(int updatedCount) {
                                    Toast.makeText(
                                            HamchinhActivity.this,
                                            "✓ Đã lưu username. Đồng bộ " + updatedCount + " giao dịch cũ",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    android.util.Log.d("ChangeUsername", "Batch updated transactions: " + updatedCount);
                                }

                                @Override
                                public void onError(String message) {
                                    Toast.makeText(HamchinhActivity.this, "✗ Lỗi lưu: " + message, Toast.LENGTH_SHORT).show();
                                    android.util.Log.e("ChangeUsername", "Error: " + message);
                                }
                            }
                    );
                    return kotlin.Unit.INSTANCE;
                }
        ).show(getSupportFragmentManager(), com.example.sothuchi.ui.ChangeUsernameBottomSheet.TAG);
    }

    private void sendReportRequestToBackend() {
        android.content.Context context = this;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            Toast.makeText(context, "Chưa đăng nhập hoặc tài khoản không có Email", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, "Đang tạo báo cáo và gửi email...", Toast.LENGTH_SHORT).show();

        String url = "https://unsystematising-judie-interfoliaceous.ngrok-free.dev/api/export-email";

        try {
            JSONObject payload = new JSONObject();
            payload.put("userId", user.getUid());
            payload.put("userEmail", user.getEmail());

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(context, "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    boolean isSuccess = response.isSuccessful();
                    if (response.body() != null) {
                        response.body().close();
                    }

                    runOnUiThread(() -> {
                        if (isSuccess) {
                            Toast.makeText(context, "Gửi thành công! Vui lòng kiểm tra Email.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, "Lỗi Server: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(context, "Lỗi xử lý JSON: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleJsonResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            boolean autoSubmit = jsonObject.getBoolean("auto_submit");
            String thongBao = jsonObject.getString("thong_bao");

            if (autoSubmit) {
                // Auto-submit logic
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                Runnable submitRunnable = () -> pushDataToFirebase(jsonObject);

                com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), thongBao, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                        .setAction("HOÀN TÁC", v -> {
                            handler.removeCallbacks(submitRunnable);
                            fillFormWithJson(jsonObject);
                        });

                snackbar.setDuration(5000); // 5 seconds
                snackbar.show();

                handler.postDelayed(submitRunnable, 5000);
            } else {
                // Fill form for manual submission
                fillFormWithJson(jsonObject);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý JSON: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fillFormWithJson(JSONObject jsonObject) {
        try {
            android.widget.EditText etSoTien = findViewById(getResourceIdIfExists("input_amount"));
            if (etSoTien == null) etSoTien = findViewById(getResourceIdIfExists("et_amount"));
            if (etSoTien == null) etSoTien = findViewById(getResourceIdIfExists("input_so_tien"));

            android.widget.EditText etGhiChu = findViewById(getResourceIdIfExists("input_note"));
            if (etGhiChu == null) etGhiChu = findViewById(getResourceIdIfExists("et_note"));

            android.widget.EditText etDanhMuc = findViewById(getResourceIdIfExists("et_danh_muc"));
            if (etDanhMuc == null) etDanhMuc = findViewById(getResourceIdIfExists("input_category"));

            android.widget.TextView tvNgay = findViewById(getResourceIdIfExists("value_date"));
            if (tvNgay == null) tvNgay = findViewById(getResourceIdIfExists("tv_date_value"));
            if (tvNgay == null) {
                // try EditText date field
                android.widget.EditText etNgay = findViewById(getResourceIdIfExists("et_ngay"));
                if (etNgay != null) etNgay.setText(jsonObject.optString("ngay", ""));
            }

            if (etSoTien != null) etSoTien.setText(String.valueOf(jsonObject.optInt("so_tien", 0)));
            if (etGhiChu != null) etGhiChu.setText(jsonObject.optString("ghi_chu", ""));
            if (etDanhMuc != null) etDanhMuc.setText(jsonObject.optString("danh_muc", ""));
            if (tvNgay != null) tvNgay.setText(jsonObject.optString("ngay", ""));

            selectedCategoryName = jsonObject.optString("danh_muc", "");
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi điền dữ liệu vào form: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Helper: return id resource by name or 0
    private int getResourceIdIfExists(String name) {
        try {
            int id = getResources().getIdentifier(name, "id", getPackageName());
            return id != 0 ? id : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void pushDataToFirebase(JSONObject jsonObject) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Chưa đăng nhập để đồng bộ dữ liệu", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            int type = "THU".equalsIgnoreCase(jsonObject.optString("action_type")) ? 1 : 0;
            long amount = jsonObject.optLong("so_tien", 0L);
            String note = jsonObject.optString("ghi_chu", "");
            String category = jsonObject.optString("danh_muc", "Khác");

            Date now = new Date();
            SimpleDateFormat yearMonthFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            SimpleDateFormat yearFmt = new SimpleDateFormat("yyyy", Locale.getDefault());

            data.put("amount", amount);
            data.put("note", note);
            data.put("category", category);
            data.put("type", type);
            data.put("timestamp", new com.google.firebase.Timestamp(now));
            data.put("yearMonth", yearMonthFmt.format(now));
            data.put("year", Integer.parseInt(yearFmt.format(now)));
            data.put("lastUpdated", FieldValue.serverTimestamp());

            db.collection("users").document(user.getUid())
                    .collection("transactions")
                    .add(data)
                    .addOnSuccessListener(documentReference -> Toast.makeText(this, "Dữ liệu đã được lưu!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Lỗi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi đẩy dữ liệu lên Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onRealtimeTransactionsChanged(List<FirestoreTransaction> remoteTransactions) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        dbHelper.clearAllTransactions();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        for (FirestoreTransaction rt : remoteTransactions) {
            Date dateObj = rt.getTimestamp() != null ? rt.getTimestamp().toDate() : new Date();
            String dateStr = sdf.format(dateObj);

            Transaction t = new Transaction(rt.getAmount(), rt.getNote(), rt.getCategory(), dateStr, rt.getType());
            t.setCreatedBy(rt.getCreatedBy());
            t.setDeviceName(rt.getDeviceName());
            t.setDeviceId(rt.getDeviceId());
            dbHelper.addTransaction(t);
        }

        runOnUiThread(this::refreshCurrentLayoutData);
    }

    private void refreshCurrentLayoutData() {
        if (layoutId_current == R.layout.layout_search) {
            setupSearchScreen();
        } else if (layoutId_current == R.layout.lich) {
            refreshCalendarData();
        } else if (layoutId_current == R.layout.baocao || layoutId_current == R.layout.baocao_thunhap) {
            updateBaoCaoDateUI();
        } else if (layoutId_current == R.layout.baocao_nam || layoutId_current == R.layout.baocao_nam_thunhap) {
            updateYearlyDateUI();
        } else if (layoutId_current == R.layout.baocao_toanki) {
            updateToanKiDateUI();
        } else if (layoutId_current == R.layout.ngansach) {
            updateBudgetDateUI();
        }
    }
}
