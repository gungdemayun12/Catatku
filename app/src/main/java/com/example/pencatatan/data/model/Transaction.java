package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Transaction {

    public static final String INCOME = "income";
    public static final String EXPENSE = "expense";

    @DocumentId
    private String docId;
    private String type = EXPENSE;
    private long amount;
    private String currency = "IDR";
    private double exchangeRate = 1;
    private long amountBase;
    private String walletId;
    private String walletName;
    private String categoryId;
    private String categoryName;
    private String note;
    private Timestamp date;
    private String monthKey;
    /**
     * Non-null when this row is one leg of a wallet transfer. Both legs share the
     * same group id, which is what lets the app tell a transfer apart from real
     * income or spending: a transfer moves money the user already had, so it must
     * never count towards income, expense, or category totals.
     */
    private String transferGroup;
    /**
     * Recurring-transaction support.
     *
     * <p>{@code repeatType} is one of {@code "none"}, {@code "daily"},
     * {@code "weekly"}, {@code "monthly"} or {@code "yearly"}. When it is not
     * "none", {@code repeatNextDate} holds the date the next copy should be
     * created, and {@code repeatEndDate} (optional) stops the series.</p>
     *
     * <p>{@code repeatGroup} ties every generated copy of one series together,
     * exactly like {@code transferGroup} does for transfers — that is what
     * lets the app answer "is this transaction part of a series?" without
     * scanning dates.</p>
     */
    private String repeatType = "none";
    private Timestamp repeatNextDate;
    private Timestamp repeatEndDate;
    private String repeatGroup;
    /**
     * Day-of-month the monthly/yearly series is anchored to (1-31), or 0 for
     * daily/weekly series. Stored so a series created on the 31st keeps landing
     * on the 31st after February clamped it to the 28th — without it the clamp
     * would become the new anchor and the series would silently drift to the
     * 28th for the rest of its life.
     */
    private int repeatAnchorDay = 0;
    /** True on the copies the app generated, false for the one the user typed. */
    private boolean isGenerated = false;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Transaction() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(double exchangeRate) { this.exchangeRate = exchangeRate; }
    public long getAmountBase() { return amountBase; }
    public void setAmountBase(long amountBase) { this.amountBase = amountBase; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    public String getWalletName() { return walletName; }
    public void setWalletName(String walletName) { this.walletName = walletName; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }
    public String getMonthKey() { return monthKey; }
    public void setMonthKey(String monthKey) { this.monthKey = monthKey; }

    public String getTransferGroup() { return transferGroup; }
    public void setTransferGroup(String transferGroup) { this.transferGroup = transferGroup; }

    /** True when this row is one leg of a wallet-to-wallet transfer. */
    public boolean isTransfer() { return transferGroup != null && !transferGroup.isEmpty(); }

    public String getRepeatType() { return repeatType == null ? "none" : repeatType; }
    public void setRepeatType(String repeatType) { this.repeatType = repeatType; }
    public Timestamp getRepeatNextDate() { return repeatNextDate; }
    public void setRepeatNextDate(Timestamp repeatNextDate) { this.repeatNextDate = repeatNextDate; }
    public Timestamp getRepeatEndDate() { return repeatEndDate; }
    public void setRepeatEndDate(Timestamp repeatEndDate) { this.repeatEndDate = repeatEndDate; }
    public String getRepeatGroup() { return repeatGroup; }
    public void setRepeatGroup(String repeatGroup) { this.repeatGroup = repeatGroup; }
    public int getRepeatAnchorDay() { return repeatAnchorDay; }
    public void setRepeatAnchorDay(int repeatAnchorDay) { this.repeatAnchorDay = repeatAnchorDay; }
    public boolean isGenerated() { return isGenerated; }
    public void setGenerated(boolean generated) { this.isGenerated = generated; }

    /** True when this transaction repeats and has not been switched off. */
    public boolean isRepeating() {
        return !"none".equals(getRepeatType());
    }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isIncome() {
        return INCOME.equals(type);
    }
}
