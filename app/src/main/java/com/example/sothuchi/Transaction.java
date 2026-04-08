package com.example.sothuchi;

/**
 * Model class for a financial transaction.
 * id: Unique identifier
 * amount: Value of the transaction
 * note: Description
 * category: Category of income/expense
 * date: Date in format dd/MM/yyyy
 * type: 0 for Expense (Chi), 1 for Income (Thu)
 */
public class Transaction {
    private int id;
    private long amount;
    private String note;
    private String category;
    private String date;
    private int type;

    public Transaction() {}

    public Transaction(long amount, String note, String category, String date, int type) {
        this.amount = amount;
        this.note = note;
        this.category = category;
        this.date = date;
        this.type = type;
    }

    public Transaction(int id, long amount, String note, String category, String date, int type) {
        this.id = id;
        this.amount = amount;
        this.note = note;
        this.category = category;
        this.date = date;
        this.type = type;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
}
