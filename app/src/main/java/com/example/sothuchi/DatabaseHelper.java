package com.example.sothuchi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SoThuChi.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_USERS = "users";

    private static final String COLUMN_ID = "id";
    // Transactions columns
    private static final String COLUMN_AMOUNT = "amount";
    private static final String COLUMN_NOTE = "note";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TYPE = "type";

    // Users columns
    private static final String COLUMN_U_NAME = "full_name";
    private static final String COLUMN_U_EMAIL = "email";
    private static final String COLUMN_U_PHONE = "phone";
    private static final String COLUMN_U_PASS = "password";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TRANSACTIONS = "CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_AMOUNT + " INTEGER,"
                + COLUMN_NOTE + " TEXT,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_DATE + " TEXT,"
                + COLUMN_TYPE + " INTEGER" + ")";
        
        String CREATE_USERS = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_U_NAME + " TEXT,"
                + COLUMN_U_EMAIL + " TEXT,"
                + COLUMN_U_PHONE + " TEXT,"
                + COLUMN_U_PASS + " TEXT" + ")";

        db.execSQL(CREATE_TRANSACTIONS);
        db.execSQL(CREATE_USERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Bảo vệ dữ liệu người dùng: Không xóa bảng khi nâng cấp, chỉ tạo nếu chưa có
        onCreate(db);
    }

    // Insert a new transaction
    public long addTransaction(Transaction t) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_AMOUNT, t.getAmount());
        values.put(COLUMN_NOTE, t.getNote());
        values.put(COLUMN_CATEGORY, t.getCategory());
        values.put(COLUMN_DATE, t.getDate());
        values.put(COLUMN_TYPE, t.getType());

        long result = db.insert(TABLE_TRANSACTIONS, null, values);
        db.close();
        return result;
    }

    // Get all transactions
    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_TRANSACTIONS + " ORDER BY id DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Transaction t = new Transaction();
                t.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                t.setAmount(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)));
                t.setNote(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE)));
                t.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
                t.setDate(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)));
                t.setType(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
                transactions.add(t);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return transactions;
    }

    // Get transactions by month and year
    // Note: date is stored as dd/MM/yyyy. We use LIKE '%/MM/yyyy' to filter.
    public List<Transaction> getTransactionsByMonth(int month, int year) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String monthStr = (month < 10 ? "0" : "") + month;
        // Search for dates matching like %/05/2026
        String pattern = "%/" + monthStr + "/" + year + "%";
        
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, COLUMN_DATE + " LIKE ?",
                new String[]{pattern}, null, null, "id DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Transaction t = new Transaction(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
                        cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        cursor.getString(cursor.getColumnIndexOrThrow("date")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("type"))
                );
                transactions.add(t);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return transactions;
    }

    public List<Transaction> getTransactionsByYear(int year) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String pattern = "%/" + year + "%";
        
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, COLUMN_DATE + " LIKE ?",
                new String[]{pattern}, null, null, "id DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Transaction t = new Transaction(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
                        cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        cursor.getString(cursor.getColumnIndexOrThrow("date")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("type"))
                );
                transactions.add(t);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return transactions;
    }

    // Get transactions by exact date
    public List<Transaction> getTransactionsByDate(String date) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, COLUMN_DATE + " = ?",
                new String[]{date}, null, null, "id DESC");

        if (cursor.moveToFirst()) {
            do {
                Transaction t = new Transaction();
                t.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                t.setAmount(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)));
                t.setNote(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE)));
                t.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
                t.setDate(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)));
                t.setType(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
                transactions.add(t);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return transactions;
    }

    // Lấy tổng số tiền theo ngày và loại (0: Chi, 1: Thu)
    public long getSumByDate(String date, int type) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COLUMN_DATE + " = ? AND " + COLUMN_TYPE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{date, String.valueOf(type)});
        long total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getLong(0);
        }
        cursor.close();
        return total;
    }

    // Lấy tổng Thu, Chi, Tổng của cả tháng
    public long[] getSummaryByMonth(int month, int year) {
        String monthStr = (month < 10 ? "0" : "") + month;
        String pattern = "%/" + monthStr + "/" + year;

        SQLiteDatabase db = this.getReadableDatabase();
        String queryIncome = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COLUMN_DATE + " LIKE ? AND " + COLUMN_TYPE + " = 1";
        String queryExpense = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS +
                " WHERE " + COLUMN_DATE + " LIKE ? AND " + COLUMN_TYPE + " = 0";

        long income = 0;
        long expense = 0;

        Cursor c1 = db.rawQuery(queryIncome, new String[]{pattern});
        if (c1.moveToFirst()) income = c1.getLong(0);
        c1.close();

        Cursor c2 = db.rawQuery(queryExpense, new String[]{pattern});
        if (c2.moveToFirst()) expense = c2.getLong(0);
        c2.close();

        return new long[]{income, expense, income - expense};
    }

    // Lấy tổng tất cả các khoảng Thu/Chi trong toàn bộ quá trình sử dụng
    public long[] getAllTimeSummary() {
        SQLiteDatabase db = this.getReadableDatabase();
        String queryIncome = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS + " WHERE " + COLUMN_TYPE + " = 1";
        String queryExpense = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_TRANSACTIONS + " WHERE " + COLUMN_TYPE + " = 0";

        long income = 0;
        long expense = 0;

        Cursor c1 = db.rawQuery(queryIncome, null);
        if (c1.moveToFirst()) income = c1.getLong(0);
        c1.close();

        Cursor c2 = db.rawQuery(queryExpense, null);
        if (c2.moveToFirst()) expense = c2.getLong(0);
        c2.close();

        db.close();
        return new long[]{income, expense, income - expense};
    }

    // Xóa toàn bộ giao dịch cục bộ (dùng khi nhập dữ liệu từ Firebase)
    public void clearAllTransactions() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, null, null);
        db.close();
    }
    // Lưu thông tin người dùng
    public void saveUserLocal(String name, String email, String phone, String pass) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_U_NAME, name);
        values.put(COLUMN_U_EMAIL, email);
        values.put(COLUMN_U_PHONE, phone);
        values.put(COLUMN_U_PASS, pass);
        
        // Xóa thông tin cũ trước khi lưu mới (app này hỗ trợ 1 user local)
        db.delete(TABLE_USERS, null, null);
        db.insert(TABLE_USERS, null, values);
        db.close();
    }

    // Lấy thông tin user hiện tại
    public android.content.ContentValues getLocalUser() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, null, null, null, null, null);
        android.content.ContentValues values = null;
        if (cursor.moveToFirst()) {
            values = new android.content.ContentValues();
            values.put("fullName", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_U_NAME)));
            values.put("email", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_U_EMAIL)));
            values.put("phone", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_U_PHONE)));
            values.put("password", cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_U_PASS)));
        }
        cursor.close();
        return values;
    }
}
