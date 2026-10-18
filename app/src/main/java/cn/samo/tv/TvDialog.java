package cn.samo.tv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** A remote-friendly action sheet with a complete key press required for activation. */
public final class TvDialog extends Dialog {
    public static final class Action {
        final String label;
        final Runnable callback;
        final boolean destructive;

        public Action(String label, Runnable callback) {
            this(label, callback, false);
        }

        public Action(String label, Runnable callback, boolean destructive) {
            this.label = label;
            this.callback = callback;
            this.destructive = destructive;
        }
    }

    private final Activity activity;
    private final float scale;
    private final String title;
    private final String message;
    private final Action[] actions;
    private final List<TextView> rows = new ArrayList<>();
    private int armedKey = -1;
    private long armedDownTime;
    private TextView armedRow;
    private boolean actionTaken;

    public TvDialog(Activity activity, float scale, String title, String message, Action... actions) {
        super(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        this.activity = activity;
        this.scale = Math.max(0.5f, scale);
        this.title = title;
        this.message = message;
        this.actions = actions == null ? new Action[0] : actions.clone();
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    private int d(float value) {
        return Math.round(value * scale * activity.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable surface(int color, float radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(d(radius));
        return result;
    }

    private TextView text(String value, float size, int color) {
        TextView result = new TextView(activity);
        result.setText(value);
        result.setTextSize(size * scale);
        result.setTextColor(color);
        result.setIncludeFontPadding(false);
        result.setFontFeatureSettings("kern");
        return result;
    }

    private int sheetWidth() {
        return Math.min(d(450), activity.getResources().getDisplayMetrics().widthPixels - d(48));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.CENTER);
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(surface(0xF0222836, 20));
        sheet.setClipToOutline(true);
        sheet.setPadding(d(10), 0, d(10), d(10));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(d(18), d(26), d(18), d(22));
        TextView heading = text(title, 24, 0xFFF6F7FA);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (message != null && !message.trim().isEmpty()) {
            TextView body = text(message, 14, 0xFFB5BBC8);
            body.setLineSpacing(d(3), 1f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = d(10);
            header.addView(body, bodyParams);
        }
        sheet.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setFocusable(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        for (int i = 0; i < actions.length; i++) {
            final Action action = actions[i];
            if (action == null) continue;
            if (!rows.isEmpty()) {
                View divider = new View(activity);
                divider.setBackgroundColor(0x20FFFFFF);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.max(1, d(0.5f)));
                dividerParams.setMargins(d(18), 0, d(18), 0);
                list.addView(divider, dividerParams);
            }
            TextView row = text(action.label, 16, action.destructive ? 0xFFFF898C : 0xFFF2F4F8);
            row.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(d(18), 0, d(18), 0);
            row.setId(View.generateViewId());
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnFocusChangeListener((view, focused) -> {
                clearArmedKey();
                row.setBackground(focused ? surface(0xFFF3F5FA, 8) : null);
                row.setTextColor(focused ? (action.destructive ? 0xFFAF2736 : 0xFF202530)
                        : (action.destructive ? 0xFFFF898C : 0xFFF2F4F8));
            });
            row.setOnClickListener(view -> {
                if (actionTaken || !isShowing()) return;
                actionTaken = true;
                dismiss();
                if (action.callback != null) action.callback.run();
            });
            rows.add(row);
            list.addView(row, new LinearLayout.LayoutParams(-1, d(50)));
        }

        // Keep short action sheets compact; longer lists scroll inside the same surface.
        int width = sheetWidth();
        header.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, width - d(20)), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int available = Math.max(d(50), metrics.heightPixels - d(64) - header.getMeasuredHeight() - d(10));
        int listHeight = rows.size() * d(50) + Math.max(0, rows.size() - 1) * Math.max(1, d(0.5f));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(listHeight, available)));
        setContentView(sheet);

        for (int i = 0; i < rows.size(); i++) {
            TextView row = rows.get(i);
            row.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            row.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
        }
    }

    @Override protected void onStart() {
        super.onStart();
        actionTaken = false;
        clearArmedKey();
        Window window = getWindow();
        if (window != null) window.setLayout(sheetWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    private void clearArmedKey() {
        armedKey = -1;
        armedDownTime = 0;
        armedRow = null;
    }

    private TextView focusedRow() {
        for (TextView row : rows) if (row.hasFocus()) return row;
        return null;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0 && !event.isCanceled() && !event.isLongPress()) {
                    armedKey = key;
                    armedDownTime = event.getDownTime();
                    armedRow = focusedRow();
                } else clearArmedKey();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                TextView target = armedRow;
                boolean activate = armedKey == key && armedDownTime == event.getDownTime()
                        && target != null && target.hasFocus() && !event.isCanceled();
                clearArmedKey();
                if (activate) target.performClick();
            }
            // A key-UP from the long press that opened this sheet must never click a row.
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            clearArmedKey();
            if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled()
                    && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)
                    && !rows.isEmpty()) {
                int current = rows.indexOf(focusedRow());
                int next = current < 0 ? 0 : Math.max(0, Math.min(rows.size() - 1,
                        current + (key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
                rows.get(next).requestFocus();
            }
            return true;
        }
        // Dialog's native BACK handling cancels normally. HOME remains owned by the system.
        if (key == KeyEvent.KEYCODE_BACK) clearArmedKey();
        return super.dispatchKeyEvent(event);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) clearArmedKey();
    }

    @Override protected void onStop() {
        clearArmedKey();
        super.onStop();
    }
}
