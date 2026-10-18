package cn.samo.tv;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Watches only the known Xiaomi home activity; never inspects window content. */
public final class HomeAccessibilityService extends AccessibilityService {
    private static final String STOCK_PACKAGE="com.mitv.tvhome";
    private static final String STOCK_HOME_CLASS="com.mitv.tvhome.MainActivityUserMode";

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        HomeAdapter.openAtBoot(this,BootStartupPolicy.Source.SERVICE_CONNECTED);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(!STOCK_PACKAGE.contentEquals(event.getPackageName()==null?"":event.getPackageName()))return;
        if(!STOCK_HOME_CLASS.contentEquals(event.getClassName()==null?"":event.getClassName()))return;
        HomeAdapter.openHomeIfEnabled(this);
    }

    @Override public void onInterrupt() {
        // No ongoing work, timer or retry loop to interrupt.
    }
}
