package cn.samo.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** One claimed boot attempt, only after explicit system accessibility approval. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent!=null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            HomeAdapter.openAtBoot(context,BootStartupPolicy.Source.BOOT_BROADCAST);
    }
}
