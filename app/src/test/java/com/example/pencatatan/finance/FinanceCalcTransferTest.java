package com.example.pencatatan.finance;

import static org.junit.Assert.assertEquals;

import com.example.pencatatan.data.model.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Locks in the rule that a wallet transfer is neither income nor spending.
 *
 * <p>Both legs of a transfer are ordinary rows in the transaction list, so any
 * code that totals transactions has to skip them. That was missed in the export
 * writers, the Home summary and the report donut, which made a self-transfer
 * show up as extra spending and disagree with the Finance screen.</p>
 */
public class FinanceCalcTransferTest {

    /** One real expense, plus a transfer that must not affect either total. */
    private static List<Transaction> sample() {
        List<Transaction> data = new ArrayList<>();

        Transaction expense = new Transaction();
        expense.setType(Transaction.EXPENSE);
        expense.setAmount(75_000);
        expense.setAmountBase(75_000);
        expense.setCategoryId("bills");
        data.add(expense);

        Transaction out = new Transaction();
        out.setType(Transaction.EXPENSE);
        out.setAmount(200_000);
        out.setAmountBase(200_000);
        out.setTransferGroup("group-1");
        data.add(out);

        Transaction in = new Transaction();
        in.setType(Transaction.INCOME);
        in.setAmount(200_000);
        in.setAmountBase(200_000);
        in.setTransferGroup("group-1");
        data.add(in);

        return data;
    }

    @Test
    public void incomeIgnoresTransferInLeg() {
        assertEquals(0, FinanceCalc.income(sample()));
    }

    @Test
    public void expenseIgnoresTransferOutLeg() {
        assertEquals(75_000, FinanceCalc.expense(sample()));
    }

    @Test
    public void categoryTotalsIgnoreTransfers() {
        // Only "bills" exists; the transfer legs have no category and must not
        // create an "__none__" bucket worth 200k.
        assertEquals(75_000, FinanceCalc.expenseByCategory(sample()).get("bills").longValue());
        assertEquals(1, FinanceCalc.expenseByCategory(sample()).size());
    }
}
