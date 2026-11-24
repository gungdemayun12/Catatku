package com.example.pencatatan.finance;

import com.example.pencatatan.common.DialogScroll;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.common.Money;
import com.example.pencatatan.common.MoneyText;
import com.example.pencatatan.common.Prefs;
import com.example.pencatatan.data.model.ExchangeRate;
import com.example.pencatatan.data.model.User;
import com.example.pencatatan.data.repository.FinanceRepository;
import com.example.pencatatan.data.repository.UserRepository;
import com.example.pencatatan.databinding.ActivityExchangeRatesBinding;
import com.example.pencatatan.databinding.DialogRateBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class ExchangeRatesActivity extends BaseActivity {

    private ActivityExchangeRatesBinding binding;
    private RateAdapter adapter;
    private final FinanceRepository repository = new FinanceRepository();
    private final UserRepository userRepository = new UserRepository();

    private final Map<String, ExchangeRate> rates = new HashMap<>();
    private String baseCurrency = "IDR";

    private ListenerRegistration rateListener;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (uid() == null) {
            finish();
            return;
        }
        baseCurrency = Prefs.getCurrency(this);
        binding = ActivityExchangeRatesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Back button (top-left)
        binding.backButton.setOnClickListener(v -> finish());

        // Auto-fetch rates button
        binding.refreshRatesButton.setOnClickListener(v -> fetchLiveRates());

        adapter = new RateAdapter(this::openRateDialog);
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        listenUser();
        listenRates();
        applyBase();
    }

    private void listenUser() {
        userListener = userRepository.listen(uid(), new UserRepository.UserListener() {
            @Override
            public void onUser(User user) {
                if (user != null && user.getCurrency() != null && !user.getCurrency().isEmpty()) {
                    baseCurrency = user.getCurrency();
                    Prefs.setCurrency(ExchangeRatesActivity.this, baseCurrency);
                    applyBase();
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

    private void listenRates() {
        rateListener = repository.listenRates(uid(), new FinanceRepository.OnRates() {
            @Override
            public void onData(Map<String, ExchangeRate> data) {
                rates.clear();
                if (data != null) rates.putAll(data);
                adapter.submit(rates, baseCurrency);
            }

            @Override
            public void onError(Exception error) {
                toast(getString(R.string.error_load));
            }
        });
    }

    private void applyBase() {
        binding.baseValue.setText(baseCurrency);
        binding.baseHint.setText(getString(R.string.rate_main_currency, baseCurrency));
        adapter.submit(rates, baseCurrency);
    }

    /** Fetch live rates from FreeCurrencyAPI and save them all to Firestore */
    private void fetchLiveRates() {
        binding.refreshRatesButton.setEnabled(false);
        binding.fetchProgressBar.setVisibility(View.VISIBLE);
        binding.fetchStatusText.setVisibility(View.VISIBLE);
        binding.fetchStatusText.setText(R.string.rate_fetching);

        CurrencyApiService.fetchRates(baseCurrency, new CurrencyApiService.RateCallback() {
            @Override
            public void onRates(Map<String, Double> fetchedRates) {
                // Save each rate that is in our SUPPORTED list
                int[] saved = {0};
                int[] total = {0};

                for (String code : Money.SUPPORTED) {
                    if (code.equalsIgnoreCase(baseCurrency)) continue;
                    Double rate = fetchedRates.get(code);
                    if (rate != null && rate > 0) {
                        total[0]++;
                        repository.saveRate(uid(), code, rate,
                                new FinanceRepository.SaveCallback() {
                                    @Override
                                    public void onSuccess(String id) {
                                        saved[0]++;
                                        if (saved[0] == total[0]) onAllSaved();
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        saved[0]++;
                                        if (saved[0] == total[0]) onAllSaved();
                                    }
                                });
                    }
                }

                if (total[0] == 0) onAllSaved();
            }

            @Override
            public void onError(String message) {
                binding.refreshRatesButton.setEnabled(true);
                binding.fetchProgressBar.setVisibility(View.GONE);
                binding.fetchStatusText.setText(R.string.rate_fetch_failed);
                toast(getString(R.string.rate_fetch_failed));
            }
        });
    }

    private void onAllSaved() {
        binding.refreshRatesButton.setEnabled(true);
        binding.fetchProgressBar.setVisibility(View.GONE);
        binding.fetchStatusText.setText(R.string.rate_fetch_done);
        toast(getString(R.string.rate_fetch_done));
    }

    private void openRateDialog(String currency) {
        if (currency == null || currency.equals(baseCurrency)) return;
        DialogRateBinding dialogBinding = DialogRateBinding.inflate(getLayoutInflater());
        dialogBinding.rateSummary.setText(getString(R.string.rate_for, currency, baseCurrency));
        dialogBinding.rateLayout.setSuffixText(baseCurrency);
        dialogBinding.rateEdit.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        ExchangeRate existing = rates.get(currency);
        if (existing != null && existing.getRateToBase() > 0) {
            dialogBinding.rateEdit.setText(MoneyText.formatRate(existing.getRateToBase()));
            dialogBinding.rateEdit.setSelection(dialogBinding.rateEdit.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rate_edit_title)
                .setView(DialogScroll.wrap(dialogBinding.getRoot()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    dialogBinding.rateLayout.setError(null);
                    double rate = parseRate(text(dialogBinding.rateEdit.getText()));
                    if (rate <= 0) {
                        dialogBinding.rateLayout.setError(getString(R.string.error_rate_positive));
                        return;
                    }
                    repository.saveRate(uid(), currency, rate,
                            new FinanceRepository.SaveCallback() {
                                @Override
                                public void onSuccess(String id) {
                                    toast(getString(R.string.rate_saved));
                                    dialog.dismiss();
                                }

                                @Override
                                public void onError(Exception error) {
                                    toast(getString(R.string.save_failed));
                                }
                            });
                }));
        dialog.show();
    }

    private double parseRate(String raw) {
        if (raw == null) return 0;
        try {
            return Double.parseDouble(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rateListener != null) rateListener.remove();
        if (userListener != null) userListener.remove();
    }
}
