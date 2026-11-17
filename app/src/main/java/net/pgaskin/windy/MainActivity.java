// SPDX-FileCopyrightText: 2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_SET_WALLPAPER = 1;

    private WindyWallpaperView preview;
    private LinearLayout bottomBar;
    private EditorPanel editor;
    private int themeIndex;

    private CustomThemeView customTheme; // hosts the editor's dialogs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        preview = findViewById(R.id.preview);
        bottomBar = findViewById(R.id.bottom_bar);
        editor = findViewById(R.id.editor_panel);
        customTheme = findViewById(R.id.custom_theme);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main);
        toolbar.getMenu().findItem(R.id.action_customize).setVisible(false); // everything is customizable now
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        buildBottomBar();
        applyInsets(toolbar);
        applySystemBarAppearance();

        // the old fixed themes are now starting points in the presets, so the
        // app always shows (and sets) the editable custom theme
        themeIndex = Themes.CUSTOM;
        Prefs.setThemeIndex(this, themeIndex);
        preview.setThemeIndex(themeIndex);
    }

    private void buildBottomBar() {
        addBottomButton(R.drawable.ic_tune, R.string.bar_edit, false, v -> editor.open(customTheme, EditorPanel.TAB_LINES, -1));
        addBottomButton(R.drawable.ic_effects, R.string.tab_effects, false, v -> editor.open(customTheme, EditorPanel.TAB_EFFECTS, -1));
        addBottomButton(R.drawable.ic_presets, R.string.tab_presets, false, v -> editor.open(customTheme, EditorPanel.TAB_PRESETS, -1));
        addBottomButton(R.drawable.ic_set_wallpaper, R.string.bar_set_wallpaper, true, v -> setAsWallpaper());
    }

    private void addBottomButton(int icon, int label, boolean bold, View.OnClickListener listener) {
        final float density = getResources().getDisplayMetrics().density;
        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER);
        item.setMinimumHeight(Math.round(56 * density));
        item.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));
        final android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        item.setBackgroundResource(tv.resourceId);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(getString(label));
        item.setOnClickListener(listener);
        final ImageView image = new ImageView(this);
        image.setImageResource(icon);
        item.addView(image, new LinearLayout.LayoutParams(Math.round(24 * density), Math.round(24 * density)));
        final TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        text.setPadding(0, Math.round(3 * density), 0, 0);
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        item.addView(text);
        bottomBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        preview.setPaused(false);
        // ask about the wind data first, then about the location (like the
        // wallpaper does when it's started from the picker)
        if (Prefs.dataConsentPending(this)) {
            WindFieldConsentActivity.request(this);
        } else {
            LocationConsentActivity.request(this);
            Location.update(this);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // api 33+ uses the editor's OnBackInvokedCallback instead
        final EditorPanel editor = findViewById(R.id.editor_panel);
        if (editor != null && editor.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.setPaused(true);
    }

    private void applyInsets(Toolbar toolbar) {
        // preview is edge-to-edge, toolbars are inset
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            final Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            toolbar.setPadding(bars.left, bars.top, bars.right, 0);
            // keep the bar above the navigation buttons (3-button or gesture)
            bottomBar.setPadding(bars.left, bottomBar.getPaddingTop(), bars.right, bars.bottom);
            editor.setPadding(bars.left, 0, bars.right, 0);
            editor.setNavInset(bars.bottom);
            return insets;
        });
    }

    private void applySystemBarAppearance() {
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) {
            return;
        }
        // the status bar is over the preview, the navigation bar is over the theme selector
        final boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        controller.setSystemBarsAppearance(
                night ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }

    private void setAsWallpaper() {
        final Themes.Entry theme = Themes.get(themeIndex);
        final Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, theme.service));
        try {
            startActivityForResult(intent, REQUEST_SET_WALLPAPER);
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "no live wallpaper preview activity, opening the chooser: " + ex);
            try {
                startActivityForResult(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), REQUEST_SET_WALLPAPER);
            } catch (ActivityNotFoundException ex1) {
                Log.e(TAG, "no live wallpaper chooser either: " + ex1);
                Toast.makeText(this, R.string.set_wallpaper_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SET_WALLPAPER && resultCode == RESULT_OK) {
            Log.i(TAG, "wallpaper was set, closing");
            finish();
        }
    }
}
