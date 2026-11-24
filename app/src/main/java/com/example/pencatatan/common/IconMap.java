package com.example.pencatatan.common;

import com.example.pencatatan.R;

/** Maps the stored icon keys to bundled vector drawables. */
public final class IconMap {

    private IconMap() {
    }

    public static final String[] FINANCE_ICONS = {
            "food", "transport", "shopping", "bill", "fun", "salary", "gift", "other"
    };

    public static int forFinance(String icon) {
        if (icon == null) return R.drawable.ic_tag;
        switch (icon) {
            case "food":
                return R.drawable.ic_restaurant;
            case "transport":
                return R.drawable.ic_payments;
            case "shopping":
                return R.drawable.ic_shopping_bag;
            case "bill":
                return R.drawable.ic_bill_slip;
            case "fun":
                return R.drawable.ic_entertainment;
            case "salary":
                return R.drawable.ic_trending_up;
            case "gift":
                return R.drawable.ic_money_circle;
            default:
                return R.drawable.ic_tag;
        }
    }

    public static int forWallet(String type) {
        if ("bank".equals(type)) return R.drawable.ic_bank;
        if ("ewallet".equals(type)) return R.drawable.ic_payments;
        return R.drawable.ic_wallet;
    }

    public static int forAction(String action) {
        if (action == null) return R.drawable.ic_info;
        switch (action) {
            case "created":
                return R.drawable.ic_add;
            case "updated":
                return R.drawable.ic_edit;
            case "completed":
                return R.drawable.ic_check_circle;
            case "uncompleted":
                return R.drawable.ic_repeat;
            case "deleted":
                return R.drawable.ic_delete;
            default:
                return R.drawable.ic_info;
        }
    }
}
