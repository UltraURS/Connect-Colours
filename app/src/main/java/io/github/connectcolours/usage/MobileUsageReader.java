package io.github.connectcolours.usage;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.RemoteException;

import java.time.ZoneId;

/** Invoke on a worker thread in the SystemUI process, using its privileged context. */
public final class MobileUsageReader {
    private MobileUsageReader() {}

    @SuppressWarnings("deprecation")
    public static long readToday(Context context) throws RemoteException {
        long now = System.currentTimeMillis();
        long start = DailyUsage.startOfDay(now, ZoneId.systemDefault());
        NetworkStatsManager manager = context.getSystemService(NetworkStatsManager.class);
        // A null subscriber ID includes all mobile subscriptions, excluding Wi-Fi.
        NetworkStats.Bucket bucket = manager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE, null, start, now);
        // Preserve failures so the caller can retain the original tile label.
        // Never display a failed query as zero usage.
        if (bucket == null) throw new IllegalStateException("No mobile usage summary");
        return bucket.getRxBytes() + bucket.getTxBytes();
    }
}
