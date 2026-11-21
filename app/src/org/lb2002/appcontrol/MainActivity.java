// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 Yicons

package org.lb2002.appcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Button> filterButtons = new ArrayList<>();
    private TextView status;
    private ListView list;
    private ArrayAdapter<Entry> adapter;
    private int filter;
    private boolean busy;
    private EditText search;
    private static final int TEXT = Color.rgb(236, 243, 255);
    private static final int MUTED = Color.rgb(146, 165, 190);

    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
        "android", "org.lb2002.appcontrol", "app.orchard.tv",
        "com.android.systemui", "com.android.settings", "com.android.tv.settings",
        "com.droidlogic.tv.settings", "com.android.shell",
        "com.android.externalstorage", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.providers.settings",
        "com.google.android.tungsten.setupwraith", "com.topjohnwu.magisk"));

    private static final class Entry {
        final String name;
        final String pkg;
        final boolean enabled;
        final boolean protectedApp;
        final boolean system;
        final Drawable icon;

        Entry(ApplicationInfo info, PackageManager pm, String home) {
            name = String.valueOf(info.loadLabel(pm));
            pkg = info.packageName;
            enabled = info.enabled;
            system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Drawable loaded;
            try { loaded = info.loadIcon(pm); }
            catch (RuntimeException e) { loaded = pm.getDefaultActivityIcon(); }
            icon = loaded;
            protectedApp = PROTECTED.contains(pkg) || pkg.equals(home)
                || info.uid < 10000 || (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(42), dp(24), dp(42), dp(16));
        root.setBackgroundColor(Color.rgb(10, 18, 31));
        TextView title = new TextView(this);
        title.setText("应用冻结管理");
        title.setTextSize(28);
        title.setTextColor(TEXT);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("选择应用后按确定键，即可冻结、解冻或查看详情");
        help.setTextColor(MUTED);
        help.setTextSize(15);
        root.addView(help);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(18);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("搜索应用名称或包名");
        search.setContentDescription("搜索应用名称或包名");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(focusBackground(Color.rgb(23, 36, 55)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.setMargins(0, dp(16), 0, dp(10));
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                list.requestFocus();
                if (!entries.isEmpty()) list.setSelection(0);
                return true;
            }
            return false;
        });
        LinearLayout filters = new LinearLayout(this);
        String[] labels = {"全部应用", "已冻结", "已启用", "刷新", "权限"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextColor(TEXT);
            button.setTextSize(16);
            button.setAllCaps(false);
            button.setBackground(focusBackground(Color.rgb(23, 36, 55)));
            button.setOnClickListener(v -> {
                if (busy) return;
                if (index < 3) { filter = index; applyFilter(); }
                else if (index == 3) { reload(); }
                else org.lb2002.shared.RootAccess.show(this,
                    Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, "冻结与解冻需要系统权限或 Root 授权。查看应用列表无需 Root。");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
            if (i > 0) params.leftMargin = dp(10);
            filters.addView(button, params);
            filterButtons.add(button);
        }
        root.addView(filters);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(MUTED);
        status.setPadding(0, dp(10), 0, dp(8));
        root.addView(status);
        list = new ListView(this);
        list.setItemsCanFocus(false);
        list.setDividerHeight(dp(5));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setSelector(focusBackground(Color.rgb(18, 29, 45)));
        adapter = new ArrayAdapter<Entry>(this, android.R.layout.simple_list_item_1, entries) {
            @Override public View getView(int pos, View old, ViewGroup parent) {
                LinearLayout row;
                ImageView icon;
                TextView name;
                TextView detail;
                TextView badge;
                TextView frozenMark;
                if (old == null) {
                    row = new LinearLayout(MainActivity.this);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(16), dp(10), dp(16), dp(10));
                    icon = new ImageView(MainActivity.this);
                    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    FrameLayout iconFrame = new FrameLayout(MainActivity.this);
                    iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, -1));
                    frozenMark = new TextView(MainActivity.this);
                    frozenMark.setText("Ⅱ");
                    frozenMark.setTextSize(12);
                    frozenMark.setTypeface(null, Typeface.BOLD);
                    frozenMark.setTextColor(0xFF192334);
                    frozenMark.setGravity(android.view.Gravity.CENTER);
                    frozenMark.setBackground(shape(0xFFFFCD6C, 0));
                    FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(dp(21), dp(21),
                        android.view.Gravity.BOTTOM | android.view.Gravity.END);
                    iconFrame.addView(frozenMark, markParams);
                    row.addView(iconFrame, new LinearLayout.LayoutParams(dp(44), dp(44)));
                    LinearLayout words = new LinearLayout(MainActivity.this);
                    words.setOrientation(LinearLayout.VERTICAL);
                    words.setPadding(dp(16), 0, dp(12), 0);
                    name = new TextView(MainActivity.this);
                    name.setTextSize(19);
                    name.setTextColor(TEXT);
                    name.setSingleLine();
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    detail = new TextView(MainActivity.this);
                    detail.setTextSize(13);
                    detail.setTextColor(MUTED);
                    detail.setSingleLine();
                    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    words.addView(name);
                    words.addView(detail);
                    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                    badge = new TextView(MainActivity.this);
                    badge.setTextSize(14);
                    badge.setGravity(android.view.Gravity.CENTER);
                    badge.setPadding(dp(12), dp(6), dp(12), dp(6));
                    row.addView(badge);
                    row.setTag(new View[]{icon, name, detail, badge, frozenMark});
                } else {
                    row = (LinearLayout) old;
                    View[] views = (View[]) row.getTag();
                    icon = (ImageView) views[0];
                    name = (TextView) views[1];
                    detail = (TextView) views[2];
                    badge = (TextView) views[3];
                    frozenMark = (TextView) views[4];
                }
                Entry entry = getItem(pos);
                icon.setImageDrawable(entry.icon);
                if (entry.enabled) {
                    icon.clearColorFilter();
                    icon.setAlpha(1f);
                } else {
                    ColorMatrix grayscale = new ColorMatrix();
                    grayscale.setSaturation(0f);
                    icon.setColorFilter(new ColorMatrixColorFilter(grayscale));
                    icon.setAlpha(0.45f);
                }
                frozenMark.setVisibility(entry.enabled ? View.GONE : View.VISIBLE);
                name.setText(entry.name);
                detail.setText(entry.pkg + (entry.protectedApp ? "  · 关键组件"
                    : entry.system ? "  · 系统应用" : ""));
                badge.setText(entry.enabled ? "已启用" : "已冻结");
                badge.setTextColor(entry.enabled ? Color.rgb(110, 224, 202) : Color.rgb(255, 205, 108));
                badge.setBackground(shape(entry.enabled ? 0xFF183C3A : 0xFF493822, 0));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> showActions(entries.get(pos)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        filterButtons.get(0).requestFocus();
    }

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(10));
        if (stroke != 0) shape.setStroke(dp(2), stroke);
        return shape;
    }

    private Drawable focusBackground(int normal) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xFF264B65, 0xFF72DEDC));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(0xFF264B65, 0xFF72DEDC));
        states.addState(new int[]{android.R.attr.state_selected}, shape(0xFF1B394E, 0));
        states.addState(new int[]{}, shape(normal, 0));
        return states;
    }

    private void applyFilter() {
        if (adapter == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        entries.clear();
        int frozen = 0;
        for (Entry entry : allEntries) {
            if (!entry.enabled) frozen++;
            if (filter == 1 && entry.enabled || filter == 2 && !entry.enabled) continue;
            if (!query.isEmpty() && !entry.name.toLowerCase(Locale.ROOT).contains(query)
                    && !entry.pkg.toLowerCase(Locale.ROOT).contains(query)) continue;
            entries.add(entry);
        }
        adapter.notifyDataSetChanged();
        LauncherIcon.update(this, frozen > 0);
        for (int i = 0; i < 3; i++) filterButtons.get(i).setSelected(i == filter);
        status.setText("全部 " + allEntries.size() + "   ·   已冻结 " + frozen
            + "   ·   " + (entries.isEmpty() ? "没有匹配的应用" : "显示 " + entries.size() + " 项"));
    }

    @Override public void onResume() {
        super.onResume();
        if (!busy) reload();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void reload() {
        busy = true;
        status.setText("读取应用列表…");
        worker.execute(() -> {
            try {
                PackageManager pm = getPackageManager();
                android.content.pm.ResolveInfo resolved = pm.resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
                String home = resolved == null ? "" : resolved.activityInfo.packageName;
                List<Entry> result = new ArrayList<>();
                for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                    Entry entry = new Entry(info, pm, home);
                    result.add(entry);
                }
                Collator collator = Collator.getInstance();
                result.sort((a, b) -> {
                    int state = Boolean.compare(a.enabled, b.enabled);
                    return state != 0 ? state : collator.compare(a.name, b.name);
                });
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    allEntries.clear();
                    allEntries.addAll(result);
                    busy = false;
                    applyFilter();
                });
            } catch (Exception e) { failed(e); }
        });
    }

    private void showActions(Entry entry) {
        if (busy) return;
        showAppDialog(entry, false);
    }

    private TextView dialogText(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button dialogButton(String title, int background, int textColor, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(18);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setBackground(focusBackground(background));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void showAppDialog(Entry entry, boolean confirmFreeze) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(26), dp(28), dp(24));
        card.setBackground(shape(0xFF142237, 0xFF2D435C));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(entry.icon);
        if (!entry.enabled) {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0);
            icon.setColorFilter(new ColorMatrixColorFilter(matrix));
            icon.setAlpha(0.45f);
        }
        heading.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(18), 0, 0, 0);
        TextView name = dialogText(entry.name, 24, TEXT);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        names.addView(name);
        TextView pkg = dialogText(entry.pkg, 13, MUTED);
        pkg.setSingleLine();
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        names.addView(pkg);
        heading.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(heading);
        TextView state = dialogText(entry.enabled ? "已启用" : "Ⅱ  已冻结", 14,
            entry.enabled ? 0xFF6EE0CA : 0xFFFFCD6C);
        state.setPadding(dp(12), dp(5), dp(12), dp(5));
        state.setBackground(shape(entry.enabled ? 0xFF183C3A : 0xFF493822, 0));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.setMargins(0, dp(18), 0, dp(18));
        card.addView(state, stateParams);
        Button primary;
        if (confirmFreeze) {
            TextView prompt = dialogText("冻结此应用？", 22, TEXT);
            prompt.setTypeface(null, Typeface.BOLD);
            card.addView(prompt);
            TextView note = dialogText("应用将停止运行，并从桌面隐藏。\n之后可在“已冻结”列表中解冻。", 16, MUTED);
            note.setPadding(0, dp(8), 0, dp(24));
            note.setLineSpacing(dp(4), 1);
            card.addView(note);
            LinearLayout buttons = new LinearLayout(this);
            primary = dialogButton("确认冻结", 0xFF493822, 0xFFFFCD6C,
                () -> { dialog.dismiss(); changeState(entry); });
            buttons.addView(primary, new LinearLayout.LayoutParams(0, dp(50), 1));
            Button cancel = dialogButton("取消", 0xFF21334C, TEXT, dialog::dismiss);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(50), 1);
            cancelParams.leftMargin = dp(12);
            buttons.addView(cancel, cancelParams);
            card.addView(buttons);
        } else {
            String title = entry.enabled ? "冻结应用" : "解冻应用";
            primary = dialogButton(title, entry.enabled ? 0xFF493822 : 0xFF183C3A,
                entry.enabled ? 0xFFFFCD6C : 0xFF6EE0CA, () -> {
                    dialog.dismiss();
                    if (entry.enabled) showAppDialog(entry, true);
                    else changeState(entry);
                });
            if (entry.enabled && entry.protectedApp) {
                primary.setText("关键组件 · 已保护");
                primary.setEnabled(false);
                primary.setAlpha(0.5f);
                TextView protectedNote = dialogText("用于系统启动、桌面或权限管理，限制冻结。", 15, MUTED);
                protectedNote.setPadding(0, 0, 0, dp(12));
                card.addView(protectedNote);
            }
            addDialogButton(card, primary);
            Intent open = getPackageManager().getLeanbackLaunchIntentForPackage(entry.pkg);
            if (open == null) open = getPackageManager().getLaunchIntentForPackage(entry.pkg);
            final Intent launch = open;
            if (entry.enabled && launch != null) {
                addDialogButton(card, dialogButton("打开应用", 0xFF21334C, TEXT,
                    () -> { dialog.dismiss(); startActivity(launch); }));
            }
            addDialogButton(card, dialogButton("系统应用详情", 0xFF21334C, TEXT, () -> {
                dialog.dismiss();
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + entry.pkg)));
            }));
            addDialogButton(card, dialogButton("返回", 0xFF1A2A40, MUTED, dialog::dismiss));
        }
        dialog.setContentView(card);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.72f;
        window.setAttributes(params);
        dialog.show();
        window.setLayout(dp(560), -2);
        if (primary.isEnabled()) primary.requestFocus();
    }

    private void addDialogButton(LinearLayout card, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(10);
        card.addView(button, params);
    }

    private void changeState(Entry entry) {
        busy = true;
        status.setText("正在" + (entry.enabled ? "冻结 " : "解冻 ") + entry.name + "…");
        worker.execute(() -> {
            try {
                boolean enable = !entry.enabled;
                PackageManager pm = getPackageManager();
                if (checkSelfPermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
                        == PackageManager.PERMISSION_GRANTED) {
                    pm.setApplicationEnabledSetting(entry.pkg, enable
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
                } else {
                    if (!entry.pkg.matches("[A-Za-z0-9_.]+")) throw new Exception("包名无效");
                    org.lb2002.shared.RootAccess.run(this, "pm "
                        + (enable ? "enable" : "disable-user") + " --user "
                        + org.lb2002.shared.RootAccess.userId() + " " + entry.pkg);
                }
                if (pm.getApplicationInfo(entry.pkg, 0).enabled != enable) {
                    throw new Exception("系统未保存新的应用状态");
                }
                if (!getSharedPreferences("application_states", MODE_PRIVATE)
                        .edit().putBoolean(entry.pkg, enable).commit()) {
                    throw new Exception("应用状态已更改，但保存重启恢复配置失败，请重试");
                }
                runOnUiThread(() -> { busy = false; reload(); });
            } catch (Exception e) { failed(e); }
        });
    }

    private void failed(Exception e) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            busy = false;
            status.setText("操作失败 · " + e.getMessage());
        });
    }

    @Override public void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }
}
