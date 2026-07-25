package dev.linjian.peek;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LockActivity extends Activity {
    private String pkg;
    private TextView titleView, remainView, reasonView, messageView;
    private EditText requestReasonInput;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() { @Override public void run() { refresh(); handler.postDelayed(this, 1000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pkg = getIntent() == null ? "" : getIntent().getStringExtra("package");
        if (pkg == null) pkg = "";
        buildUi();
        refresh();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getStringExtra("package") != null) pkg = intent.getStringExtra("package");
        refresh();
    }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFFFF8F1);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView tag = text("应用门禁 App Gate", 13, 0xFF8B7468, false);
        tag.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(tag, lp(-1, -2, 0, 0, 0, 8));

        titleView = text("App 已被char锁定", 25, 0xFF2F403B, true);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(titleView, lp(-1, -2, 0, 0, 0, 12));

        remainView = text("剩余时间计算中…", 20, 0xFF2E9D72, true);
        remainView.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(remainView, lp(-1, -2, 0, 0, 0, 20));

        reasonView = card("锁定理由", "读取中…");
        root.addView(reasonView, lp(-1, -2, 0, 0, 0, 12));

        messageView = card("char说", "先回来找我。");
        root.addView(messageView, lp(-1, -2, 0, 0, 0, 18));

        requestReasonInput = new EditText(this);
        requestReasonInput.setHint("申请解锁理由，例如：我要发映屿小红书，不是乱刷");
        requestReasonInput.setTextSize(14);
        requestReasonInput.setSingleLine(false);
        requestReasonInput.setMinLines(2);
        root.addView(requestReasonInput, lp(-1, -2, 0, 0, 0, 10));

        Button request = button("找char申请解锁");
        request.setOnClickListener(v -> {
            String reason = requestReasonInput.getText().toString().trim();
            if (reason.length() == 0) { Toast.makeText(this, "先写一句解锁理由", Toast.LENGTH_SHORT).show(); return; }
            AppGate.submitUnlockRequest(this, pkg, reason);
            Toast.makeText(this, "已把解锁申请交给char", Toast.LENGTH_LONG).show();
        });
        root.addView(request, lp(-1, dp(48), 0, 0, 0, 8));

        Button home = button("返回桌面");
        home.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); if (svc != null) svc.doHome(); finish(); });
        root.addView(home, lp(-1, dp(46), 0, 0, 0, 8));

        Button emergency = button("紧急解锁（按住 5 秒后输入口令）");
        final Runnable emergencyRunnable = () -> showEmergencyDialog();
        emergency.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(emergencyRunnable, 5000);
                Toast.makeText(this, "继续按住 5 秒，才会打开紧急解锁", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(emergencyRunnable);
                return true;
            }
            return true;
        });
        root.addView(emergency, lp(-1, dp(46), 0, 0, 0, 8));

        TextView foot = text("这不是锁死。时间到了会自动打开；真的有事可以用紧急口令。", 12, 0xFF8B7468, false);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(foot, lp(-1, -2, 0, 10, 0, 0));
        setContentView(scroll);
    }

    private void refresh() {
        JSONObject lock = AppGate.currentLock(this, pkg);
        if (lock == null) { finish(); return; }
        long now = System.currentTimeMillis(); long until = lock.optLong("locked_until_ms", 0); long remain = Math.max(0, until - now);
        titleView.setText(lock.optString("app_name", AppGate.labelOf(this, pkg)) + " 已被char锁定");
        remainView.setText("剩余时间：" + remainText(remain));
        reasonView.setText("锁定理由\n" + lock.optString("reason", "char先把这扇门关一会儿。"));
        messageView.setText("char说\n" + lock.optString("message", "先回来找我，不准一个人刷太久。"));
    }

    private void showEmergencyDialog() {
        final EditText input = new EditText(this);
        input.setHint("输入char告诉你的紧急口令");
        new AlertDialog.Builder(this)
                .setTitle("紧急解锁")
                .setMessage("确认是紧急情况再用。通过后会临时放行几分钟，并写入日志。")
                .setView(input)
                .setPositiveButton("解锁", (d, which) -> {
                    boolean ok = AppGate.tryEmergencyUnlock(this, pkg, input.getText().toString());
                    Toast.makeText(this, ok ? "紧急解锁成功，临时放行" : "口令不对", Toast.LENGTH_LONG).show();
                    if (ok) finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setLineSpacing(4, 1f); if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return t;
    }
    private TextView card(String title, String body) { TextView t = text(title + "\n" + body, 15, 0xFF3E514B, false); t.setPadding(dp(16), dp(14), dp(16), dp(14)); t.setBackgroundColor(0xFFFFEFE4); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(14); b.setTextColor(0xFFFFFFFF); b.setBackgroundColor(0xFF7AC7B7); return b; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(l,t,r,b); return p; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String remainText(long ms) {
        long sec = ms / 1000; long h = sec / 3600; long m = (sec % 3600) / 60; long s = sec % 60;
        if (h > 0) return h + " 小时 " + m + " 分钟 " + s + " 秒";
        if (m > 0) return m + " 分钟 " + s + " 秒";
        return s + " 秒";
    }
}
