package com.example.pencatatan.finance;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import com.example.pencatatan.R;

/**
 * Hands an exported file to the system share sheet.
 *
 * <p>A {@code FileProvider} uri is only readable by the receiving app if the
 * read grant actually travels with the intent. Setting
 * {@code FLAG_GRANT_READ_URI_PERMISSION} alone is not enough on modern Android:
 * the chooser re-dispatches the intent to the target, and the grant is only
 * carried along when the uri is also present in the intent's {@link ClipData}.
 * Without it the share sheet still opens, but every target fails with
 * "Permission Denial: reading ... FileProvider uri ... requires the provider be
 * exported, or grantUriPermission()" and the user gets an empty/broken file.</p>
 */
public final class ExportShare {

    private ExportShare() {
    }

    /**
     * Shares {@code uri} as {@code mimeType}.
     *
     * <p>The {@link ClipData} carries the uri so the permission grant survives the
     * chooser hop; the flag is set on both the send intent and the chooser so no
     * step in the chain drops it.</p>
     */
    public static void share(Activity activity, Uri uri, String mimeType) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mimeType);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT,
                activity.getString(R.string.report_export_subject));
        send.setClipData(ClipData.newRawUri(
                activity.getString(R.string.report_export_subject), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(send,
                activity.getString(R.string.report_export_chooser));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(chooser);
    }
}
