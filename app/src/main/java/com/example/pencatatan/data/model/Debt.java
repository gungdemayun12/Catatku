package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

/**
 * Money the user owes (a debt) or is owed (a receivable).
 *
 * <p>One model for both directions because they are the same object seen from
 * two sides: a name, an amount, an optional due date, and "how much has been
 * settled so far". Keeping them together means the list can show a single
 * balance ("you owe Rp 300.000 net") instead of two screens that the user has
 * to mentally subtract.</p>
 *
 * <p>Recording partial payments matters: people rarely settle a debt in one go,
 * so {@link #paidAmount} accumulates and the card shows "Rp 200.000 of
 * Rp 500.000". A debt is only finished when the two are equal.</p>
 */
@IgnoreExtraProperties
public class Debt {

    public static final String TYPE_DEBT = "debt";
    public static final String TYPE_RECEIVABLE = "receivable";

    @DocumentId
    private String docId;
    /** {@link #TYPE_DEBT} = I owe them, {@link #TYPE_RECEIVABLE} = they owe me. */
    private String direction = TYPE_DEBT;
    /** Who the money is owed to, or owed by. */
    private String personName;
    private long amount;
    private String currency = "IDR";
    private long paidAmount;
    private Timestamp dueDate;
    private String note;
    /** Optional wallet the money moved through, kept for the record only. */
    private String walletId;
    private String walletName;
    private boolean isSettled = false;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Debt() {
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getPaidAmount() { return paidAmount; }
    public void setPaidAmount(long paidAmount) { this.paidAmount = paidAmount; }
    public Timestamp getDueDate() { return dueDate; }
    public void setDueDate(Timestamp dueDate) { this.dueDate = dueDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    public String getWalletName() { return walletName; }
    public void setWalletName(String walletName) { this.walletName = walletName; }
    @PropertyName("isSettled")
    public boolean isSettled() { return isSettled; }
    @PropertyName("isSettled")
    public void setSettled(boolean settled) { isSettled = settled; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isReceivable() {
        return TYPE_RECEIVABLE.equals(direction);
    }

    /** What is still outstanding. Zero once settled, never negative. */
    public long remainingAmount() {
        return Math.max(0L, amount - paidAmount);
    }

    public int progressPercent() {
        if (amount <= 0) return 0;
        long percent = Math.round(paidAmount * 100.0 / amount);
        return (int) Math.max(0L, Math.min(100L, percent));
    }

    /**
     * True when there is still money outstanding and the due date has passed.
     * A settled debt is never overdue, no matter how old its date is.
     */
    public boolean isOverdue() {
        if (isSettled || remainingAmount() == 0 || dueDate == null) return false;
        return dueDate.toDate().getTime() < System.currentTimeMillis();
    }
}
