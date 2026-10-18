package cn.samo.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/** Shared local opt-out and launch guard for the narrowly scoped Xiaomi adapter. */
final class HomeAdapter {
    static final String PREFS_NAME="samo_home";
    static final String PREF_ENABLED="home_adapter_enabled";
    private static final String STARTUP_BOOT="startup_boot_count";
    private static final String STARTUP_BROADCAST="startup_broadcast_attempted";
    private static final String STARTUP_BINDING="startup_binding_attempted";
    private static final String STARTUP_RESUMED="startup_activity_resumed";
    private static final String STARTUP_TAG="SamoStartup";

    private HomeAdapter() {}

    static boolean isRequested(Context context) {
        // The system must explicitly enable the service before this has any effect.
        return context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).getBoolean(PREF_ENABLED,true);
    }

    static void setRequested(Context context,boolean enabled) {
        context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED,enabled).apply();
    }

    static boolean isServiceEnabled(Context context) {
        try {
            if(Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
            String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(enabled==null)return false;
            ComponentName own=new ComponentName(context,HomeAccessibilityService.class);
            for(String value:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(value)))return true;
        } catch(RuntimeException unavailable) {
            // A firmware restriction must leave the original desktop operational.
        }
        return false;
    }

    static synchronized boolean openHomeIfEnabled(Context context) {
        if(!isRequested(context) || !isServiceEnabled(context))return false;
        boolean requested=startHome(context);
        logStartup("stock_home",requested?"launch_requested":"launch_failed",bootCount(context),SystemClock.elapsedRealtime());
        return requested;
    }

    /** At most one broadcast attempt and one early binding compensation per boot. */
    static synchronized void openAtBoot(Context context,BootStartupPolicy.Source source) {
        int boot=bootCount(context);
        long elapsed=SystemClock.elapsedRealtime();
        try {
            SharedPreferences prefs=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
            BootStartupPolicy.State state=readStartupState(prefs);
            BootStartupPolicy.Decision decision=BootStartupPolicy.evaluate(source,
                isRequested(context),isServiceEnabled(context),boot,elapsed,state);
            if(decision!=BootStartupPolicy.Decision.REQUEST_LAUNCH) {
                logStartup(source.name(),decision.name(),boot,elapsed);
                return;
            }
            // Claim synchronously before launching, so process death/rebinding cannot
            // repeat this source. A request is deliberately not a resume receipt.
            if(!writeStartupState(prefs,state.claim(boot,source))) {
                logStartup(source.name(),"claim_failed",boot,elapsed);
                return;
            }
            logStartup(source.name(),startHome(context)?"launch_requested":"launch_failed",boot,elapsed);
        } catch(RuntimeException unavailable) {
            logStartup(source.name(),"state_unavailable",boot,elapsed);
        }
    }

    /** Called only by the real activity lifecycle, never by a launch request. */
    static synchronized void acknowledgeResume(Context context) {
        int boot=bootCount(context);
        long elapsed=SystemClock.elapsedRealtime();
        if(boot<0) {
            logStartup("activity","activity_resumed_unknown_boot",boot,elapsed);
            return;
        }
        try {
            SharedPreferences prefs=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);
            BootStartupPolicy.State state=readStartupState(prefs);
            boolean saved=(state.bootCount==boot && state.resumed)
                || writeStartupState(prefs,state.acknowledgeResume(boot));
            logStartup("activity",saved?"activity_resumed":"activity_resumed_receipt_unsaved",boot,elapsed);
        } catch(RuntimeException unavailable) {
            logStartup("activity","activity_resumed_receipt_unavailable",boot,elapsed);
        }
    }

    private static BootStartupPolicy.State readStartupState(SharedPreferences prefs) {
        return new BootStartupPolicy.State(prefs.getInt(STARTUP_BOOT,-1),
            prefs.getBoolean(STARTUP_BROADCAST,false),prefs.getBoolean(STARTUP_BINDING,false),
            prefs.getBoolean(STARTUP_RESUMED,false));
    }

    private static boolean writeStartupState(SharedPreferences prefs,BootStartupPolicy.State state) {
        return prefs.edit().putInt(STARTUP_BOOT,state.bootCount)
            .putBoolean(STARTUP_BROADCAST,state.broadcastAttempted)
            .putBoolean(STARTUP_BINDING,state.bindingAttempted)
            .putBoolean(STARTUP_RESUMED,state.resumed).commit();
    }

    private static int bootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);
        } catch(RuntimeException unavailable) {
            return -1;
        }
    }

    private static void logStartup(String source,String result,int boot,long elapsed) {
        Log.i(STARTUP_TAG,"source="+source+" result="+result+" boot="+boot+" elapsed_ms="+elapsed);
    }

    private static boolean startHome(Context context) {
        try {
            Intent home=new Intent(context,MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);
            return true;
        } catch(RuntimeException unavailable) {
            // No retry loop or background daemon if the OS blocks the activity.
            return false;
        }
    }
}
