package com.example.pencatatan.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;

import java.util.ArrayList;
import java.util.List;

/**
 * A savings target, e.g. "New laptop — Rp 12.000.000".
 *
 * <p>The point of this model is that saving is a <em>running total</em>, not a
 * one-shot boolean. A goal is created with a target and an optional starting
 * amount; after that the user adds whatever they managed to put aside
 * ("masukki tabungan berapapun"), and the progress bar moves. Every deposit is
 * also stored as a small entry in {@link #contributions} so the goal screen can
 * show the history of what went in and when — otherwise the number would just
 * change with no explanation, which makes people stop trusting it.</p>
 *
 * <p>The wallet link is optional. When a goal is tied to a wallet, adding money
 * here is understood as "this much of that wallet is earmarked", and the goal
 * screen can show it against the real balance. Keeping it optional means the
 * feature still works for people who never set up wallets.</p>
 */
@IgnoreExtraProperties
public class SavingsGoal {

    /** Shown above the title on the goal card. */
    public static final String TYPE_TRAVEL = "travel";
    public static final String TYPE_GADGET = "gadget";
    public static final String TYPE_EMERGENCY = "emergency";
    public static final String TYPE_HOME = "home";
    public static final String TYPE_EDUCATION = "education";
    public static final String TYPE_OTHER = "other";

    @DocumentId
    private String docId;
    private String title;
    private String note;
    /** Key from the TYPE_* set above; drives the icon and colour of the card. */
    private String goalType = TYPE_OTHER;
    /** How much the user wants to end up with. */
    private long targetAmount;
    /** How much has been set aside so far. Never written directly — see addAmount(). */
    private long savedAmount;
    /** Optional deadline; used only to say "Rp X per week to make it". */
    private Timestamp targetDate;
    private String walletId;
    private String walletName;
    private boolean isArchived = false;
    /** One entry per deposit: {"amount": long, "at": Timestamp, "note": String}. */
    private List<Contribution> contributions = new ArrayList<>();
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public SavingsGoal() {
    }

    /**
     * One deposit into the goal.
     *
     * <p>Stored inline on the goal document rather than in a subcollection: a
     * goal is small, its history is small, and having them in one document
     * means the progress bar and the history can never disagree.</p>
     */
    @IgnoreExtraProperties
    public static class Contribution {
        private long amount;
        private Timestamp at;
        private String note;

        public Contribution() {
        }

        public Contribution(long amount, Timestamp at, String note) {
            this.amount = amount;
            this.at = at;
            this.note = note;
        }

        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }
        public Timestamp getAt() { return at; }
        public void setAt(Timestamp at) { this.at = at; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public long getTargetAmount() { return targetAmount; }
    public void setTargetAmount(long targetAmount) { this.targetAmount = targetAmount; }
    public long getSavedAmount() { return savedAmount; }
    public void setSavedAmount(long savedAmount) { this.savedAmount = savedAmount; }
    public Timestamp getTargetDate() { return targetDate; }
    public void setTargetDate(Timestamp targetDate) { this.targetDate = targetDate; }
    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }
    public String getWalletName() { return walletName; }
    public void setWalletName(String walletName) { this.walletName = walletName; }
    @PropertyName("isArchived")
    public boolean isArchived() { return isArchived; }
    @PropertyName("isArchived")
    public void setArchived(boolean archived) { isArchived = archived; }
    public List<Contribution> getContributions() {
        return contributions == null ? new ArrayList<>() : contributions;
    }
    public void setContributions(List<Contribution> contributions) {
        this.contributions = contributions;
    }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /** How much is still missing. Never negative, so an overshoot reads as "done". */
    public long remainingAmount() {
        return Math.max(0L, targetAmount - savedAmount);
    }

    /** 0–100. A goal with no target is reported as empty rather than as 100% done. */
    public int progressPercent() {
        if (targetAmount <= 0) return 0;
        long percent = Math.round(savedAmount * 100.0 / targetAmount);
        return (int) Math.max(0L, Math.min(100L, percent));
    }

    public boolean isReached() {
        return targetAmount > 0 && savedAmount >= targetAmount;
    }
}
