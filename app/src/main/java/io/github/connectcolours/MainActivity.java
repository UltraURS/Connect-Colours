package io.github.connectcolours;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import android.content.pm.ApplicationInfo;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import android.widget.EditText;
import android.widget.SeekBar;
import io.github.connectcolours.sidebar.SidebarGesture;
import android.text.Editable;
import android.text.TextWatcher;
import io.github.connectcolours.recents.HiddenAppsConfig;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xfff5f6f5;
    private static final int INK = 0xff19251f;
    private static final int MUTED = 0xff526158;
    private static final int ACCENT = 0xff177754;
    private SharedPreferences preferences;
    private LinearLayout body;
    private LinearLayout navigation;
    private String page = "home";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try {
            preferences = getSharedPreferences(Feature.PREFS, Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            preferences = null;
        }
        if (state != null) page = state.getString("page", "home");
        FrameLayout backdrop = new FrameLayout(this);
        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.module_background);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdrop.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(this);
        veil.setBackgroundColor(0xb3f5f6f5);
        backdrop.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout root = column();
        backdrop.addView(root, new FrameLayout.LayoutParams(-1, -1));
        body = column();
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        navigation.setBackgroundColor(0xe6f5f6f5);
        navigation.setPadding(dp(24), dp(8), dp(24), dp(8));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(backdrop);
        showPage(page);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if ("hidden".equals(page)) showPage("settings");
        else if ("settings".equals(page)) showPage("home");
        else super.onBackPressed();
    }

    private void showPage(String selected) {
        page = selected;
        body.removeAllViews();
        navigation.removeAllViews();
        if ("hidden".equals(page)) {
            hiddenApps();
            nav("home", "模块", 0);
            nav("settings", "设置", 1);
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(26), dp(20), dp(24));
        scroll.addView(content);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        TextView title = text("home".equals(page) ? "Connect Colours" : "设置", 30, INK, true);
        content.addView(title);
        TextView subtitle = text("home".equals(page) ? "ColorOS 功能增强" : "按你的习惯，调整每一项功能", 14, MUTED, false);
        subtitle.setPadding(0, dp(7), 0, dp(24));
        content.addView(subtitle);
        if ("home".equals(page)) home(content);
        else settings(content);
        nav("home", "模块", 0);
        nav("settings", "设置", 1);
    }

    private void home(LinearLayout content) {
        boolean active = ModuleStatus.frameworkApi() > 0 || preferences != null;
        boolean supported = Build.VERSION.SDK_INT == 33;
        FrameLayout status = new FrameLayout(this);
        status.setBackground(round(active && supported ? 0x99dff6e5 : 0x80ffffff, 24));
        Symbol mark = new Symbol(this, active && supported ? 2 : 3,
                active && supported ? 0xff32bd67 : 0xffacb2ae);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams art = new FrameLayout.LayoutParams(dp(135), dp(135), Gravity.END | Gravity.BOTTOM);
        art.rightMargin = -dp(15);
        art.bottomMargin = -dp(17);
        status.addView(mark, art);
        LinearLayout labels = column();
        labels.setPadding(dp(22), dp(24), dp(22), dp(24));
        labels.addView(text(!supported ? "系统暂未适配" : active ? "模块已激活" : "等待激活", 25, INK, true));
        TextView detail = text(active ? (preferences != null ? "模块已加载 · 配置可用" : "模块已加载 · 配置不可用")
                : "请在 LSPosed 中启用本模块", 14, INK, false);
        detail.setPadding(0, dp(9), 0, dp(28));
        labels.addView(detail);
        labels.addView(text(!supported ? "当前支持 Android 13" : active ? "模块开关重启生效 · 隐藏名单下次进入生效" : "启用后重新打开程序", 13, INK, false));
        status.addView(labels);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));
        sectionLabel(content, "设备与环境");
        LinearLayout info = card();
        infoRow(info, "ColorOS 版本", colorOsVersion());
        infoRow(info, "系统版本", Build.DISPLAY);
        infoRow(info, "Android 版本", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException error) { version = "未能读取"; }
        infoRow(info, "模块版本", version);
        infoRow(info, "设备型号", Build.MANUFACTURER + " " + Build.MODEL);
        if (ModuleStatus.frameworkApi() > 0) infoRow(info, "框架 API", String.valueOf(ModuleStatus.frameworkApi()));
        content.addView(info);
        TextView note = text("激活状态不代表所有目标应用均已勾选。请在 LSPosed 中确认模块作用域。", 12, MUTED, false);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        content.addView(note);
    }

    private void settings(LinearLayout content) {
        TextView notice = text(preferences == null
                ? "共享配置不可用。请先启用模块并重新打开程序，开关暂不可修改。"
                : "模块开关默认开启，修改后重启手机生效。蓝牙设备的自动连接需在该设备详情页单独开启。", 14, MUTED, false);
        content.addView(notice);
        sectionLabel(content, "功能类");
        LinearLayout functions = card();
        content.addView(functions);
        sectionLabel(content, "隐藏类");
        LinearLayout blocks = card();
        content.addView(blocks);
        for (Feature feature : Feature.values()) {
            featureRow(feature == Feature.SIDEBAR || feature == Feature.SCREENSHOT || feature == Feature.TRAFFIC || feature == Feature.BLUETOOTH
                    || feature == Feature.BLUETOOTH_PROMPTS ? functions : blocks, feature);
        }
        sidebarTimingRow(functions);
        LinearLayout hiding = card();
        infoRow(hiding, "隐藏后台软件", "选择应用，隐藏最近任务卡片及底部图标；下次打开最近任务生效。");
        hiding.setTag("hidden_apps");
        hiding.setEnabled(preferences != null);
        hiding.setAlpha(preferences == null ? .5f : 1f);
        hiding.setOnClickListener(view -> { if (preferences != null) showPage("hidden"); });
        blocks.addView(hiding);
    }

    private void sidebarTimingRow(LinearLayout card) {
        divider(card);
        LinearLayout row = column();
        row.setPadding(dp(18), dp(17), dp(18), dp(17));
        int saved = preferences == null ? SidebarGesture.DEFAULT_HOLD_MILLIS
                : preferences.getInt(SidebarGesture.HOLD_KEY, SidebarGesture.DEFAULT_HOLD_MILLIS);
        int value = Math.max(200, Math.min(1000, saved));
        TextView label = text("", 17, INK, true);
        row.addView(label);
        row.addView(text("滑到最大位移后计时 · 0.2–1 秒 · 重启手机生效", 13, MUTED, false));
        SeekBar slider = new SeekBar(this);
        slider.setTag("sidebar_hold_slider");
        slider.setContentDescription("侧边栏触发时间");
        slider.setMax(8);
        slider.setProgress((value - 200) / 100);
        label.setText(String.format(Locale.ROOT, "侧边栏触发时间：%.1f 秒", (200 + slider.getProgress() * 100) / 1000f));
        slider.setEnabled(preferences != null);
        slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int millis = 200 + progress * 100;
                label.setText(String.format(Locale.ROOT, "侧边栏触发时间：%.1f 秒", millis / 1000f));
                if (fromUser && !preferences.edit().putInt(SidebarGesture.HOLD_KEY, millis).commit()) {
                    Toast.makeText(MainActivity.this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        row.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
        card.addView(row);
    }

    private void hiddenApps() {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(26), dp(20), dp(12));
        body.addView(content, new LinearLayout.LayoutParams(-1, -1));
        TextView back = text("‹ 返回设置", 16, ACCENT, true);
        back.setPadding(0, 0, 0, dp(16));
        back.setOnClickListener(view -> showPage("settings"));
        content.addView(back);
        content.addView(text("隐藏后台软件", 28, INK, true));
        TextView hint = text(preferences == null ? "共享配置不可用，请先启用模块并重新打开程序。"
                : "勾选后隐藏最近任务卡片及底部图标，应用继续运行。首次使用需勾选桌面作用域并重启，后续修改下次进入最近任务生效。", 14, MUTED, false);
        hint.setPadding(0, dp(10), 0, dp(18));
        content.addView(hint);
        if (preferences == null) return;
        EditText search = new EditText(this);
        search.setTag("hidden_apps_search");
        search.setHint("搜索应用名称");
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setSingleLine(true);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView loading = text("正在读取应用…", 14, MUTED, false);
        content.addView(loading);
        ListView list = new ListView(this);
        list.setTag("hidden_apps_list");
        list.setBackground(round(0x80ffffff, 22));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView empty = text("没有匹配的应用", 14, MUTED, false);
        empty.setVisibility(View.GONE);
        content.addView(empty);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (list.getAdapter() instanceof HiddenAppsAdapter) {
                    ((HiddenAppsAdapter) list.getAdapter()).setQuery(s.toString());
                    list.setSelection(0);
                }
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        new Thread(() -> {
            try {
                List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
                apps.sort(new ApplicationInfo.DisplayNameComparator(getPackageManager()));
                runOnUiThread(() -> {
                    if (isDestroyed() || !"hidden".equals(page)) return;
                    loading.setVisibility(View.GONE);
                    HiddenAppsAdapter adapter = new HiddenAppsAdapter(apps);
                    adapter.setQuery(search.getText().toString());
                    list.setEmptyView(empty);
                    list.setAdapter(adapter);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> { if (!isDestroyed()) loading.setText("应用列表读取失败，请返回后重试"); });
            }
        }, "ConnectColoursApps").start();
    }

    private final class HiddenAppsAdapter extends BaseAdapter {
        private final List<ApplicationInfo> allApps;
        private final List<ApplicationInfo> apps = new ArrayList<>();
        private final Map<String, String> labels = new HashMap<>();
        private String query = "";
        HiddenAppsAdapter(List<ApplicationInfo> apps) {
            allApps = new ArrayList<>(apps);
            for (ApplicationInfo app : apps) {
                labels.put(app.packageName, app.loadLabel(getPackageManager()).toString());
            }
            setQuery("");
        }
        void setQuery(String text) {
            query = text.trim().toLowerCase(Locale.ROOT);
            Set<String> selected = HiddenAppsConfig.read(preferences);
            apps.clear();
            for (ApplicationInfo app : allApps) {
                if (labels.get(app.packageName).toLowerCase(Locale.ROOT).contains(query)) apps.add(app);
            }
            // Stable sorting preserves display-name order within each selection group.
            apps.sort((a, b) -> Boolean.compare(selected.contains(b.packageName), selected.contains(a.packageName)));
            notifyDataSetChanged();
        }
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View reusable, ViewGroup parent) {
            LinearLayout row;
            if (reusable instanceof LinearLayout) row = (LinearLayout) reusable;
            else {
                row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.addView(new ImageView(MainActivity.this), new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout labels = column();
                labels.setPadding(dp(12), 0, dp(8), 0);
                labels.addView(text("", 16, INK, true));
                labels.addView(text("", 12, MUTED, false));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                CheckBox box = new CheckBox(MainActivity.this);
                box.setButtonTintList(ColorStateList.valueOf(ACCENT));
                box.setMinimumHeight(dp(48));
                row.addView(box);
            }
            ApplicationInfo app = apps.get(position);
            ((ImageView) row.getChildAt(0)).setImageDrawable(app.loadIcon(getPackageManager()));
            LinearLayout labels = (LinearLayout) row.getChildAt(1);
            CharSequence label = this.labels.get(app.packageName);
            ((TextView) labels.getChildAt(0)).setText(label);
            ((TextView) labels.getChildAt(1)).setText(app.packageName);
            CheckBox box = (CheckBox) row.getChildAt(2);
            box.setOnCheckedChangeListener(null);
            box.setChecked(HiddenAppsConfig.read(preferences).contains(app.packageName));
            box.setContentDescription("隐藏 " + label);
            box.setTag(app.packageName);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (!HiddenAppsConfig.setHidden(preferences, app.packageName, checked)) {
                    Toast.makeText(MainActivity.this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                }
                setQuery(query);
            });
            row.setOnClickListener(view -> box.toggle());
            return row;
        }
    }

    @SuppressWarnings("deprecation")
    private void featureRow(LinearLayout card, Feature feature) {
        divider(card);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(17), dp(18), dp(17));
        LinearLayout labels = column();
        labels.addView(text(feature.title, 17, INK, true));
        TextView description = text(feature.description, 13, MUTED, false);
        description.setPadding(0, dp(6), dp(12), 0);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setTag(feature.key);
        toggle.setContentDescription(feature.title);
        toggle.setButtonTintList(ColorStateList.valueOf(ACCENT));
        toggle.setThumbTintList(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ACCENT, 0xffa2aaa5}));
        toggle.setTrackTintList(new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {0xffb7ddc6, 0xffdce1dd}));
        toggle.setChecked(preferences == null || preferences.getBoolean(feature.key, true));
        toggle.setEnabled(preferences != null);
        toggle.setMinimumHeight(dp(48));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!preferences.edit().putBoolean(feature.key, checked).commit()) {
                button.setEnabled(false);
                Toast.makeText(this, "保存失败，请重新打开程序后重试", Toast.LENGTH_LONG).show();
            }
        });
        row.addView(toggle);
        row.setOnClickListener(view -> { if (toggle.isEnabled()) toggle.toggle(); });
        card.addView(row);
    }

    private void nav(String target, String title, int symbol) {
        boolean selected = page.equals(target) || ("hidden".equals(page) && "settings".equals(target));
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setTag("nav_" + target);
        item.setContentDescription(title);
        item.setSelected(selected);
        item.setBackground(round(selected ? 0xffe1eee5 : Color.TRANSPARENT, 20));
        item.setPadding(dp(12), dp(9), dp(12), dp(8));
        item.addView(new Symbol(this, symbol, selected ? ACCENT : MUTED),
                new LinearLayout.LayoutParams(dp(25), dp(25)));
        TextView label = text(title, 12, selected ? ACCENT : MUTED, selected);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(3), 0, 0);
        item.addView(label);
        item.setOnClickListener(view -> showPage(target));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.setMargins(dp(8), 0, dp(8), 0);
        navigation.addView(item, layout);
    }

    private void infoRow(LinearLayout card, String label, String value) {
        divider(card);
        LinearLayout row = column();
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        row.addView(text(label, 16, INK, true));
        TextView detail = text(value == null || value.isEmpty() ? "未能读取" : value, 14, MUTED, false);
        detail.setPadding(0, dp(6), 0, 0);
        row.addView(detail);
        card.addView(row);
    }

    private void sectionLabel(LinearLayout parent, String title) {
        TextView label = text(title, 13, MUTED, true);
        label.setPadding(dp(4), dp(24), 0, dp(12));
        parent.addView(label);
    }

    private void divider(LinearLayout parent) {
        if (parent.getChildCount() == 0) return;
        View line = new View(this);
        line.setBackgroundColor(0xffeff2ef);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(20), 0, dp(20), 0);
        parent.addView(line, params);
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setBackground(round(0x80ffffff, 22));
        return card;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String colorOsVersion() {
        try {
            java.lang.reflect.Method get = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            String display = (String) get.invoke(null, "ro.build.version.oplusrom.display", "");
            return display.isEmpty() ? (String) get.invoke(null, "ro.build.version.oplusrom", "") : display;
        } catch (ReflectiveOperationException error) {
            return "未能读取";
        }
    }

    private static final class Symbol extends View {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Symbol(Context context, int kind, int color) {
            super(context);
            this.kind = kind;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (kind == 0) {
                path.moveTo(3, 10); path.lineTo(12, 3); path.lineTo(21, 10);
                path.moveTo(5, 9); path.lineTo(5, 21); path.lineTo(10, 21);
                path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 21);
                path.lineTo(19, 21); path.lineTo(19, 9);
            } else if (kind == 2) {
                path.moveTo(6, 12); path.lineTo(10, 16); path.lineTo(18, 8);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 24f, getHeight() / 24f);
            paint.setStrokeWidth(kind == 2 ? 1.8f : 1.7f);
            if (kind == 0) {
                canvas.drawPath(path, paint);
            } else if (kind == 1) {
                canvas.drawCircle(12, 12, 6, paint);
                canvas.drawCircle(12, 12, 2, paint);
                for (int angle = 0; angle < 360; angle += 45) {
                    canvas.save(); canvas.rotate(angle, 12, 12);
                    canvas.drawLine(12, 3, 12, 5, paint); canvas.restore();
                }
            } else if (kind == 2) {
                canvas.drawCircle(12, 12, 10, paint);
                canvas.drawPath(path, paint);
            } else {
                canvas.drawCircle(12, 12, 10, paint);
                canvas.drawLine(12, 6, 12, 13, paint);
                canvas.drawPoint(12, 17, paint);
            }
            canvas.restore();
        }
    }
}
