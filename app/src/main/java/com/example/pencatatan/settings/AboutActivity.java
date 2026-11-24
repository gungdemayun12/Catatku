package com.example.pencatatan.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.pencatatan.BuildConfig;
import com.example.pencatatan.R;
import com.example.pencatatan.common.BaseActivity;
import com.example.pencatatan.databinding.ActivityAboutBinding;
import com.example.pencatatan.databinding.ViewSettingRowBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AboutActivity extends BaseActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.backButton.setOnClickListener(v -> finish());

        setupRow(binding.rowShare, R.drawable.ic_share, R.string.about_share,
                R.string.about_share_desc);
        binding.rowShare.getRoot().setOnClickListener(v -> shareApp());

        // "Rate the app" and "Send feedback" are intentionally not shown: CatatKu
        // is not on the Play Store yet, so a store link would open a dead page and
        // an app review cannot be left. They can be restored with
        // rateApp()/sendFeedback() once the listing is live.

        setupRow(binding.rowVersion, R.drawable.ic_info, R.string.about_version_label,
                R.string.about_version_label);
        binding.rowVersion.rowSubtitle.setText(BuildConfig.VERSION_NAME);
        binding.rowVersion.rowChevron.setVisibility(View.GONE);
        binding.rowVersion.getRoot().setClickable(false);
        binding.rowVersion.getRoot().setFocusable(false);

        setupRow(binding.rowPrivacy, R.drawable.ic_info, R.string.about_privacy,
                R.string.about_privacy_desc);
        binding.rowPrivacy.getRoot().setOnClickListener(v -> showPrivacy());
    }

    private void setupRow(ViewSettingRowBinding row, int icon, int title, int subtitle) {
        row.rowIcon.setImageResource(icon);
        row.rowTitle.setText(title);
        row.rowSubtitle.setText(subtitle);
    }

    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_text)
                + "\nhttps://play.google.com/store/apps/details?id=" + getPackageName());
        startActivity(Intent.createChooser(intent, getString(R.string.about_share_chooser)));
    }

    /**
     * Opens the Play Store listing. Currently unused because CatatKu is not
     * published; kept so re-enabling the row is a one-line change.
     */
    @SuppressWarnings("unused")
    private void rateApp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + getPackageName())));
        } catch (ActivityNotFoundException error) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://play.google.com/store/apps/details?id=" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
                toast(getString(R.string.about_store_unavailable));
            }
        }
    }

    /**
     * Opens a mail draft for feedback. Currently unused because the feedback row
     * was removed until the app is released; kept for the same reason as
     * {@link #rateApp()}.
     */
    @SuppressWarnings("unused")
    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + getString(R.string.about_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_feedback_subject));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast(getString(R.string.about_store_unavailable));
        }
    }

    private void showPrivacy() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_privacy_title)
                .setMessage(R.string.about_privacy_text)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
