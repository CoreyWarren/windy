// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The custom theme editor: a resizable, see-through panel over the bottom of
 * the live preview, with tabs, big sliders (with nudge buttons, typed values,
 * and a fine mode when held still), undo, and hold-to-compare.
 *
 * Changes apply live and are saved as they're made.
 */
public final class EditorPanel extends LinearLayout {
    public static final int TAB_COLORS = 0;
    public static final int TAB_MOTION = 1;
    public static final int TAB_TRAILS = 2;
    public static final int TAB_EFFECTS = 3;
    public static final int TAB_PRESETS = 4;

    private static final int[] TAB_LABELS = {
            R.string.tab_colors, R.string.tab_motion, R.string.tab_trails, R.string.tab_effects, R.string.tab_presets,
    };

    static final int[] EFFECT_ICONS = { // [CustomTheme.EFFECT_*]
            R.drawable.ic_fx_speed, R.drawable.ic_fx_firefly, R.drawable.ic_fx_snow,
            R.drawable.ic_fx_comet, R.drawable.ic_fx_ink, R.drawable.ic_fx_neon,
    };

    private static final int[] MOTION_PARAMS = {
            CustomTheme.PARAM_WIND_SPEED, CustomTheme.PARAM_PARTICLE_COUNT, CustomTheme.PARAM_PARTICLE_LIFE,
            CustomTheme.PARAM_LINE_HALF_WIDTH, CustomTheme.PARAM_PARTICLE_OPACITY,
    };

    private static final int MAX_UNDO = 20;
    private static final float DEFAULT_HEIGHT = 0.55f; // of the screen
    private static final float MIN_HEIGHT = 0.2f;
    private static final float MAX_HEIGHT = 0.92f;
    private static final float PEEK_ALPHA = 0.06f;
    private static final long HOLD_MS = 450; // to peek, or for fine slider control
    private static final float FINE_FACTOR = 10f; // fine slider movement is this much slower

    private final float density;
    private final int touchSlop;
    private final TextView[] tabViews = new TextView[TAB_LABELS.length];
    private final LinearLayout content;
    private final ScrollView scroll;
    private final Button undoButton;
    private final ArrayDeque<CustomTheme.Snapshot> undo = new ArrayDeque<>();
    private final List<Runnable> refreshers = new ArrayList<>(); // for the current page, on outside changes
    private final Runnable themeListener = this::refreshPage;

    private CustomThemeView host;
    private CustomTheme.Snapshot before; // when the editor was opened
    private int tab = TAB_MOTION;
    private int effectPage = -1; // the effect being edited, if any
    private float heightFraction = DEFAULT_HEIGHT;
    private boolean open;
    private Object backCallback; // OnBackInvokedCallback on api 33+
    private View outside; // catches taps above the panel to close it

    public EditorPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setClickable(true); // don't let touches through to the theme list
        setFocusable(true);

        // see-through background, opaque controls
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(getResources().getColor(R.color.nav_background, context.getTheme()));
        final float r = dp(20);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        setBackground(bg);

        // drag handle (drag to resize, hold to peek)
        final FrameLayout handle = new FrameLayout(context);
        final View pill = new View(context);
        final GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(themeColor(android.R.attr.textColorSecondary));
        pillBg.setCornerRadius(dp(3));
        pill.setBackground(pillBg);
        handle.addView(pill, new FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER));
        handle.setContentDescription(context.getString(R.string.editor_handle));
        addView(handle, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        setupHandle(handle);

        // toolbar
        final LinearLayout toolbar = row();
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        undoButton = textButton(R.string.undo);
        undoButton.setOnClickListener(v -> undo());
        toolbar.addView(undoButton);
        final Button beforeButton = textButton(R.string.before);
        holdToCompare(beforeButton, () -> {
            final CustomTheme.Snapshot now = CustomTheme.snapshot(getContext());
            if (before != null) {
                CustomTheme.restore(getContext(), before, false);
            }
            return () -> CustomTheme.restore(getContext(), now, false);
        });
        toolbar.addView(beforeButton);
        toolbar.addView(new View(context), new LayoutParams(0, 1, 1));
        final Button done = textButton(R.string.done);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setOnClickListener(v -> close());
        toolbar.addView(done);
        addView(toolbar);

        // tabs
        final LinearLayout tabRow = row();
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int t = i;
            final TextView tv = new TextView(context);
            tv.setText(TAB_LABELS[t]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(4), dp(12), dp(4), dp(12));
            tv.setBackground(selectable());
            tv.setOnClickListener(v -> showTab(t));
            tabViews[t] = tv;
            tabRow.addView(tv, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        addView(tabRow);
        final View divider = new View(context);
        divider.setBackgroundColor(themeColor(android.R.attr.textColorSecondary));
        divider.setAlpha(0.3f);
        addView(divider, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));

        // page
        scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(16));
        scroll.addView(content);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    // --- opening and closing

    public boolean isOpen() {
        return open;
    }

    /** Opens the editor at a tab (or the effects tab at an effect if effect >= 0). */
    public void open(CustomThemeView host, int tab, int effect) {
        this.host = host;
        if (!open) {
            open = true;
            before = CustomTheme.snapshot(getContext());
            undo.clear();
            CustomTheme.addListener(themeListener);
            registerBack();
            showOutside();
            setAlpha(1f);
            setTranslationY(dp(2000)); // off screen until it's sized
            setVisibility(VISIBLE);
            post(() -> {
                applyHeight();
                post(() -> {
                    setTranslationY(getHeight());
                    animate().translationY(0).setDuration(200).start();
                });
            });
        }
        this.tab = tab;
        this.effectPage = effect;
        rebuild();
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        CustomTheme.persist(getContext());
        CustomTheme.removeListener(themeListener);
        unregisterBack();
        hideOutside();
        if (host != null) {
            host.onEdited();
        }
        animate().translationY(getHeight()).setDuration(180).withEndAction(() -> {
            if (!open) {
                setVisibility(GONE);
            }
        }).start();
    }

    /** A clear layer behind the panel, so tapping above it closes it. */
    private void showOutside() {
        if (!(getParent() instanceof ViewGroup)) {
            return;
        }
        final ViewGroup parent = (ViewGroup) getParent();
        if (outside == null) {
            outside = new View(getContext());
            outside.setContentDescription(getContext().getString(R.string.editor_close));
            outside.setOnClickListener(v -> close());
        }
        if (outside.getParent() == null) {
            parent.addView(outside, parent.indexOfChild(this), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void hideOutside() {
        if (outside != null && outside.getParent() instanceof ViewGroup) {
            ((ViewGroup) outside.getParent()).removeView(outside);
        }
    }

    /** Handles back. Returns false if the editor isn't open. */
    public boolean handleBack() {
        if (!open) {
            return false;
        }
        if (effectPage >= 0) {
            effectPage = -1;
            rebuild();
        } else {
            close();
        }
        return true;
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= 33 && getContext() instanceof Activity) {
            final OnBackInvokedCallback cb = this::handleBack;
            ((Activity) getContext()).getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCallback = cb;
        }
    }

    private void unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null && getContext() instanceof Activity) {
            ((Activity) getContext()).getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
        }
        backCallback = null;
    }

    /** The navigation bar height, so the bottom of the page isn't under it. */
    public void setNavInset(int inset) {
        content.setPadding(dp(16), dp(8), dp(16), dp(16) + inset);
    }

    // --- size and peeking

    private void applyHeight() {
        final View parent = (View) getParent();
        if (parent == null || parent.getHeight() == 0) {
            return;
        }
        final ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = Math.round(parent.getHeight() * heightFraction);
        setLayoutParams(lp);
    }

    private void setupHandle(View handle) {
        final float[] down = new float[2]; // raw y, height
        final boolean[] state = new boolean[2]; // resizing, peeking
        final Runnable peek = () -> {
            state[1] = true;
            handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            animate().alpha(PEEK_ALPHA).setDuration(150).start();
        };
        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawY();
                    down[1] = getHeight();
                    state[0] = false;
                    state[1] = false;
                    v.postDelayed(peek, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    final float dy = e.getRawY() - down[0];
                    if (!state[1] && (state[0] || Math.abs(dy) > touchSlop)) {
                        state[0] = true;
                        v.removeCallbacks(peek);
                        final View parent = (View) getParent();
                        if (parent != null && parent.getHeight() > 0) {
                            final float h = Math.max(MIN_HEIGHT * parent.getHeight(), Math.min(MAX_HEIGHT * parent.getHeight(), down[1] - dy));
                            heightFraction = h / parent.getHeight();
                            applyHeight();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(peek);
                    if (state[1]) {
                        animate().alpha(1f).setDuration(150).start();
                    }
                    return true;
            }
            return false;
        });
    }

    // --- undo and comparing

    /** Remembers the current state before a change. */
    private void pushUndo() {
        final CustomTheme.Snapshot now = CustomTheme.snapshot(getContext());
        if (!now.same(undo.peekLast())) {
            undo.addLast(now);
            while (undo.size() > MAX_UNDO) {
                undo.removeFirst();
            }
        }
        undoButton.setEnabled(true);
    }

    private void undo() {
        final CustomTheme.Snapshot now = CustomTheme.snapshot(getContext());
        while (!undo.isEmpty()) {
            final CustomTheme.Snapshot prev = undo.removeLast();
            if (!prev.same(now)) {
                CustomTheme.restore(getContext(), prev, true);
                committed();
                break;
            }
        }
        rebuild();
    }

    private interface Compare {
        /** Shows the comparison, returning how to undo it. */
        Runnable start();
    }

    /** While held, shows the comparison, without saving anything. */
    private void holdToCompare(View button, Compare compare) {
        final Runnable[] restore = new Runnable[1];
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    restore[0] = compare.start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (restore[0] != null) {
                        restore[0].run();
                        restore[0] = null;
                    }
                    return true;
            }
            return true;
        });
    }

    /** After a saved change. */
    private void committed() {
        if (host != null) {
            host.onEdited();
        }
    }

    // --- pages

    private void showTab(int t) {
        tab = t;
        effectPage = -1;
        rebuild();
        scroll.scrollTo(0, 0);
    }

    private void refreshPage() {
        for (final Runnable r : refreshers) {
            r.run();
        }
    }

    /** For changes to the presets made from the theme card's dialogs. */
    public void onPresetsChanged() {
        if (open && tab == TAB_PRESETS) {
            rebuild();
        }
    }

    private void rebuild() {
        refreshers.clear();
        content.removeAllViews();
        undoButton.setEnabled(!undo.isEmpty());
        for (int i = 0; i < tabViews.length; i++) {
            final boolean selected = i == tab;
            tabViews[i].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabViews[i].setAlpha(selected ? 1f : 0.65f);
            tabViews[i].setSelected(selected);
        }
        switch (tab) {
            case TAB_COLORS:
                buildColors();
                break;
            case TAB_MOTION:
                for (final int p : MOTION_PARAMS) {
                    content.addView(themeParamSlider(p));
                }
                break;
            case TAB_TRAILS:
                buildTrails();
                break;
            case TAB_EFFECTS:
                if (effectPage >= 0) {
                    buildEffect(effectPage);
                } else {
                    buildEffects();
                }
                break;
            case TAB_PRESETS:
                buildPresets();
                break;
        }
    }

    private void buildColors() {
        final LinearLayout grid = row();
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ColorSwatchView[] swatches = new ColorSwatchView[CustomTheme.COLOR_COUNT];
        for (int i = 0; i < CustomTheme.COLOR_COUNT; i++) {
            final int color = i;
            final String label = getContext().getString(CustomTheme.COLORS[color].label);
            final View item = inflater.inflate(R.layout.custom_swatch, grid, false);
            swatches[color] = item.findViewById(R.id.swatch_color);
            swatches[color].setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(46)));
            final TextView tv = item.findViewById(R.id.swatch_label);
            tv.setText(label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setLayoutParams(new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setContentDescription(label);
            item.setOnClickListener(v -> {
                pushUndo();
                host.pickColor(color, label);
            });
            grid.addView(item);
        }
        final HorizontalScrollView gridScroll = new HorizontalScrollView(getContext());
        gridScroll.addView(grid);
        content.addView(gridScroll);
        final Runnable refresh = () -> {
            final int[] colors = CustomTheme.colors(getContext());
            for (int i = 0; i < swatches.length; i++) {
                swatches[i].setColor(colors[i]);
            }
        };
        refresh.run();
        refreshers.add(refresh);

        final Button generate = outlinedButton(R.string.generate_colors);
        generate.setOnClickListener(v -> {
            pushUndo();
            host.showGenerateDialog();
        });
        content.addView(generate, spaced(ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildTrails() {
        content.addView(themeParamSlider(CustomTheme.PARAM_ALPHA_DECAY));
        content.addView(hint(R.string.trail_decay_hint));
        content.addView(toggle(getContext().getString(R.string.param_trail_follow_speed),
                getContext().getString(R.string.param_trail_follow_speed_summary), 0,
                () -> CustomTheme.param(getContext(), CustomTheme.PARAM_TRAIL_FOLLOW_SPEED) >= 0.5f,
                on -> CustomTheme.setParam(getContext(), CustomTheme.PARAM_TRAIL_FOLLOW_SPEED, on ? 1f : 0f, true)));
    }

    private void buildEffects() {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final List<Runnable> rows = new ArrayList<>();
        for (int i = 0; i < CustomTheme.EFFECT_COUNT; i++) {
            final int effect = i;
            final CustomTheme.EffectSpec spec = CustomTheme.EFFECTS[effect];
            final String label = getContext().getString(spec.label);
            final View row = inflater.inflate(R.layout.custom_effect, content, false);
            row.setMinimumHeight(dp(72));
            final ImageView icon = row.findViewById(R.id.effect_icon);
            icon.setImageResource(EFFECT_ICONS[effect]);
            final TextView name = row.findViewById(R.id.effect_label);
            name.setText(label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            final TextView summary = row.findViewById(R.id.effect_summary);
            summary.setText(spec.summary);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            final Switch sw = row.findViewById(R.id.effect_switch);
            final ColorSwatchView swatch = row.findViewById(R.id.effect_color);
            sw.setContentDescription(label);
            swatch.setContentDescription(getContext().getString(R.string.effect_tune, label));
            sw.setOnClickListener(v -> {
                pushUndo();
                CustomTheme.setEffectEnabled(getContext(), effect, sw.isChecked(), true);
                committed();
            });
            final View.OnClickListener openPage = v -> {
                effectPage = effect;
                rebuild();
                scroll.scrollTo(0, 0);
            };
            row.findViewById(R.id.effect_text).setOnClickListener(openPage);
            swatch.setOnClickListener(openPage);
            final Runnable refresh = () -> {
                final boolean on = CustomTheme.effectEnabled(getContext(), effect);
                sw.setChecked(on);
                swatch.setGradient(CustomTheme.effectGradient(getContext(), effect));
                swatch.setAlpha(on ? 1f : 0.4f);
                icon.setAlpha(on ? 1f : 0.5f);
            };
            refresh.run();
            rows.add(refresh);
            content.addView(row);
        }
        refreshers.addAll(rows);
        content.addView(hint(R.string.effects_hint));
    }

    private void buildEffect(int effect) {
        final CustomTheme.EffectSpec spec = CustomTheme.EFFECTS[effect];
        final String label = getContext().getString(spec.label);

        // header: back, title, compare
        final LinearLayout header = row();
        final Button back = textButton(R.string.back_to_effects);
        back.setOnClickListener(v -> handleBack());
        header.addView(back);
        final ImageView icon = new ImageView(getContext());
        icon.setImageResource(EFFECT_ICONS[effect]);
        header.addView(icon, new LayoutParams(dp(26), dp(26)));
        final TextView title = new TextView(getContext());
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Button compare = outlinedButton(R.string.compare);
        compare.setContentDescription(getContext().getString(R.string.compare_hint, label));
        holdToCompare(compare, () -> {
            final boolean wasOn = CustomTheme.effectEnabled(getContext(), effect);
            CustomTheme.setEffectEnabled(getContext(), effect, !wasOn, false);
            return () -> CustomTheme.setEffectEnabled(getContext(), effect, wasOn, false);
        });
        header.addView(compare);
        content.addView(header);

        // on/off
        content.addView(toggle(getContext().getString(R.string.effect_enabled), getContext().getString(spec.summary), EFFECT_ICONS[effect],
                () -> CustomTheme.effectEnabled(getContext(), effect),
                on -> CustomTheme.setEffectEnabled(getContext(), effect, on, true)));

        // gradient stops, with -/+
        final LinearLayout stopsRow = row();
        final TextView stopsLabel = label("");
        stopsRow.addView(stopsLabel, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Button fewer = nudgeButton("−", R.string.effect_fewer_colors);
        final Button more = nudgeButton("+", R.string.effect_more_colors);
        stopsRow.addView(fewer);
        stopsRow.addView(more);
        content.addView(stopsRow, spaced(ViewGroup.LayoutParams.MATCH_PARENT));

        final LinearLayout colors = row();
        final HorizontalScrollView colorsScroll = new HorizontalScrollView(getContext());
        colorsScroll.addView(colors);
        content.addView(colorsScroll);
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final List<ColorSwatchView> swatches = new ArrayList<>();
        final Runnable buildStops = () -> {
            final int stops = CustomTheme.effectStops(getContext(), effect);
            stopsLabel.setText(getContext().getString(R.string.effect_colors_count, stops));
            fewer.setEnabled(stops > CustomTheme.MIN_STOPS);
            more.setEnabled(stops < CustomTheme.MAX_STOPS);
            colors.removeAllViews();
            swatches.clear();
            for (int i = 0; i < stops; i++) {
                final int stop = i;
                final String colorLabel = getContext().getString(R.string.effect_color_n, label, stop + 1);
                final View item = inflater.inflate(R.layout.custom_swatch, colors, false);
                final ColorSwatchView swatch = item.findViewById(R.id.swatch_color);
                swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(44)));
                swatch.setColor(CustomTheme.effectColor(getContext(), effect, stop));
                ((TextView) item.findViewById(R.id.swatch_label)).setText(String.valueOf(stop + 1));
                item.setContentDescription(colorLabel);
                item.setOnClickListener(v -> {
                    pushUndo();
                    ColorPickerDialog.show(getContext(), colorLabel,
                            CustomTheme.effectColor(getContext(), effect, stop), true,
                            picked -> CustomTheme.setEffectColor(getContext(), effect, stop, picked, false), // live preview
                            picked -> {
                                // picking a color turns the effect on
                                CustomTheme.setEffectColor(getContext(), effect, stop, picked, false);
                                CustomTheme.setEffectEnabled(getContext(), effect, true, true);
                                committed();
                            });
                });
                swatches.add(swatch);
                colors.addView(item);
            }
        };
        buildStops.run();
        refreshers.add(() -> {
            if (swatches.size() != CustomTheme.effectStops(getContext(), effect)) {
                buildStops.run();
                return;
            }
            for (int i = 0; i < swatches.size(); i++) {
                swatches.get(i).setColor(CustomTheme.effectColor(getContext(), effect, i));
            }
        });
        fewer.setOnClickListener(v -> {
            pushUndo();
            CustomTheme.setEffectStops(getContext(), effect, CustomTheme.effectStops(getContext(), effect) - 1, true);
            buildStops.run();
            committed();
        });
        more.setOnClickListener(v -> {
            pushUndo();
            CustomTheme.setEffectStops(getContext(), effect, CustomTheme.effectStops(getContext(), effect) + 1, true);
            buildStops.run();
            committed();
        });
        content.addView(hint(spec.gradient));

        // sliders, then the cycle speed
        for (int i = 0; i < spec.params.length; i++) {
            content.addView(effectParamSlider(effect, i, spec.params[i]));
        }
        content.addView(effectParamSlider(effect, CustomTheme.EFFECT_PARAM_CYCLE, CustomTheme.CYCLE_SPEC));

        final Button reset = outlinedButton(R.string.reset_effect);
        reset.setOnClickListener(v -> {
            pushUndo();
            CustomTheme.resetEffect(getContext(), effect, true);
            committed();
            rebuild();
        });
        content.addView(reset, spaced(ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildPresets() {
        final LinearLayout actions = row();
        final Button save = outlinedButton(R.string.save_as_preset);
        save.setOnClickListener(v -> host.showSavePresetDialog());
        actions.addView(save);
        final Button share = outlinedButton(R.string.share);
        share.setOnClickListener(v -> host.showShareDialog());
        actions.addView(share, marginStart(dp(8)));
        final Button delete = outlinedButton(R.string.delete_preset);
        delete.setEnabled(!CustomTheme.presetName(getContext()).isEmpty());
        delete.setOnClickListener(v -> host.showDeletePresetDialog());
        actions.addView(delete, marginStart(dp(8)));
        final HorizontalScrollView actionsScroll = new HorizontalScrollView(getContext());
        actionsScroll.addView(actions);
        content.addView(actionsScroll);

        final List<CustomTheme.Preset> presets = CustomTheme.presets(getContext());
        final String selected = CustomTheme.presetName(getContext());
        if (presets.isEmpty()) {
            content.addView(hint(R.string.preset_none));
        }
        for (final CustomTheme.Preset preset : presets) {
            final TextView item = new TextView(getContext());
            final boolean current = preset.name.equals(selected);
            item.setText(current ? "✓  " + preset.name : preset.name);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            item.setTypeface(current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setMinHeight(dp(52));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(4), 0, dp(4), 0);
            item.setBackground(selectable());
            item.setOnClickListener(v -> {
                pushUndo();
                CustomTheme.loadPreset(getContext(), preset.name);
                committed();
                if (host != null) {
                    host.refresh();
                }
                rebuild();
            });
            content.addView(item);
        }
    }

    // --- controls

    private interface Getter {
        float get();
    }

    private interface Setter {
        void set(float value, boolean persist);
    }

    private interface BoolGetter {
        boolean get();
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private View themeParamSlider(int param) {
        return slider(CustomTheme.PARAMS[param],
                () -> CustomTheme.param(getContext(), param),
                (v, persist) -> CustomTheme.setParam(getContext(), param, v, persist));
    }

    private View effectParamSlider(int effect, int param, CustomTheme.ParamSpec spec) {
        return slider(spec,
                () -> CustomTheme.effectParam(getContext(), effect, param),
                (v, persist) -> CustomTheme.setEffectParam(getContext(), effect, param, v, persist));
    }

    /**
     * A slider with a label, a value (tap to type one), and -/+ nudge buttons
     * (hold to repeat). Holding still on the slider makes it 10x finer.
     */
    private View slider(CustomTheme.ParamSpec spec, Getter getter, Setter setter) {
        final LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setPadding(0, dp(10), 0, dp(6));

        final LinearLayout top = row();
        final TextView name = label(getContext().getString(spec.label));
        top.addView(name, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView fine = new TextView(getContext());
        fine.setText(R.string.fine);
        fine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fine.setTypeface(Typeface.DEFAULT_BOLD);
        fine.setPadding(dp(8), dp(2), dp(8), dp(2));
        final GradientDrawable fineBg = new GradientDrawable();
        fineBg.setCornerRadius(dp(10));
        fineBg.setStroke(Math.max(1, dp(1)), themeColor(android.R.attr.textColorPrimary));
        fine.setBackground(fineBg);
        fine.setVisibility(INVISIBLE);
        top.addView(fine, marginEnd(dp(8)));
        final TextView value = new TextView(getContext());
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setMinWidth(dp(72));
        value.setGravity(Gravity.CENTER);
        value.setPadding(dp(10), dp(8), dp(10), dp(8));
        final GradientDrawable valueBg = new GradientDrawable();
        valueBg.setCornerRadius(dp(8));
        valueBg.setStroke(Math.max(1, dp(1)), themeColor(android.R.attr.textColorSecondary));
        value.setBackground(valueBg);
        value.setContentDescription(getContext().getString(R.string.type_value, getContext().getString(spec.label)));
        top.addView(value);
        box.addView(top);

        final LinearLayout bottom = row();
        final Button minus = nudgeButton("−", R.string.nudge_down);
        final Button plus = nudgeButton("+", R.string.nudge_up);
        final SeekBar seek = new SeekBar(getContext());
        seek.setMax(spec.steps);
        seek.setMinimumHeight(dp(48));
        bottom.addView(minus);
        bottom.addView(seek, new LayoutParams(0, dp(48), 1));
        bottom.addView(plus);
        box.addView(bottom);

        final Runnable sync = () -> {
            final float v = getter.get();
            seek.setProgress(spec.progress(v));
            value.setText(spec.text(v));
        };
        sync.run();

        // all slider changes go through here
        final Runnable[] commit = new Runnable[1];
        commit[0] = () -> {
            setter.set(getter.get(), true);
            committed();
        };
        final java.util.function.IntConsumer applyProgress = p -> {
            final float v = spec.value(p);
            value.setText(spec.text(v));
            setter.set(v, false); // live preview
        };

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    applyProgress.accept(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pushUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                commit[0].run();
            }
        });

        // fine mode: hold still, then movement is 10x finer
        final float[] t = new float[4]; // down x, last x, anchor x, anchor progress
        final boolean[] fineOn = new boolean[1];
        final Runnable enterFine = () -> {
            fineOn[0] = true;
            t[2] = t[1];
            t[3] = seek.getProgress();
            pushUndo();
            seek.getParent().requestDisallowInterceptTouchEvent(true);
            fine.setVisibility(VISIBLE);
            seek.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        };
        seek.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fineOn[0] = false;
                    t[0] = t[1] = e.getX();
                    v.postDelayed(enterFine, HOLD_MS);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    t[1] = e.getX();
                    if (fineOn[0]) {
                        final int track = Math.max(1, seek.getWidth() - seek.getPaddingLeft() - seek.getPaddingRight());
                        final float delta = (e.getX() - t[2]) / track * spec.steps / FINE_FACTOR;
                        final int p = Math.max(0, Math.min(spec.steps, Math.round(t[3] + delta)));
                        if (p != seek.getProgress()) {
                            seek.setProgress(p);
                            applyProgress.accept(p);
                        }
                        return true;
                    }
                    if (Math.abs(e.getX() - t[0]) > touchSlop) {
                        v.removeCallbacks(enterFine);
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(enterFine);
                    if (fineOn[0]) {
                        fineOn[0] = false;
                        fine.setVisibility(INVISIBLE);
                        final MotionEvent cancel = MotionEvent.obtain(e);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        seek.onTouchEvent(cancel); // end the seekbar's own tracking, if any
                        cancel.recycle();
                        commit[0].run();
                        return true;
                    }
                    return false;
            }
            return false;
        });

        // nudges
        repeatOnHold(minus, () -> {
            final int p = Math.max(0, seek.getProgress() - 1);
            seek.setProgress(p);
            applyProgress.accept(p);
        }, commit[0]);
        repeatOnHold(plus, () -> {
            final int p = Math.min(spec.steps, seek.getProgress() + 1);
            seek.setProgress(p);
            applyProgress.accept(p);
        }, commit[0]);

        // typing
        value.setOnClickListener(v -> {
            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | (spec.min < 0 ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
            input.setText(spec.text(getter.get()));
            input.selectAll();
            final FrameLayout wrap = new FrameLayout(getContext());
            wrap.setPadding(dp(24), dp(8), dp(24), 0);
            wrap.addView(input);
            final AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(spec.label)
                    .setMessage(getContext().getString(R.string.type_value_range, spec.text(spec.min), spec.text(spec.max)))
                    .setView(wrap)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        try {
                            float typed = Float.parseFloat(input.getText().toString().trim().replace(',', '.'));
                            typed = Math.max(Math.min(spec.min, spec.max), Math.min(Math.max(spec.min, spec.max), typed));
                            pushUndo();
                            setter.set(typed, true);
                            committed();
                            seek.setProgress(spec.progress(typed));
                            value.setText(spec.text(typed));
                        } catch (NumberFormatException ignored) {
                            // leave it unchanged
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
            dialog.show();
            input.requestFocus();
        });

        refreshers.add(() -> {
            if (!seek.isPressed() && !fineOn[0]) {
                final float v = getter.get();
                if (seek.getProgress() != spec.progress(v)) {
                    seek.setProgress(spec.progress(v));
                }
                value.setText(spec.text(v));
            }
        });
        return box;
    }

    /** Runs step on press, then repeatedly (faster and faster) while held, then done. */
    private void repeatOnHold(View button, Runnable step, Runnable done) {
        final long[] delay = new long[1];
        final Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> {
            step.run();
            delay[0] = Math.max(40, (long) (delay[0] * 0.8));
            button.postDelayed(repeat[0], delay[0]);
        };
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!v.isEnabled()) {
                        return false;
                    }
                    v.setPressed(true);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    pushUndo();
                    step.run();
                    delay[0] = 350;
                    v.postDelayed(repeat[0], delay[0]);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    v.removeCallbacks(repeat[0]);
                    done.run();
                    return true;
            }
            return true;
        });
    }

    /** A labeled switch row (the whole row toggles it). */
    private View toggle(String text, String summary, int icon, BoolGetter getter, BoolSetter setter) {
        final LinearLayout box = row();
        box.setMinimumHeight(dp(64));
        box.setPadding(0, dp(8), 0, dp(8));
        box.setBackground(selectable());
        if (icon != 0) {
            final ImageView iv = new ImageView(getContext());
            iv.setImageResource(icon);
            box.addView(iv, marginEnd(dp(12), dp(28), dp(28)));
        }
        final LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(VERTICAL);
        texts.addView(label(text));
        if (summary != null) {
            final TextView s = new TextView(getContext());
            s.setText(summary);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            s.setAlpha(0.75f);
            texts.addView(s);
        }
        box.addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Switch sw = new Switch(getContext());
        sw.setContentDescription(text);
        box.addView(sw);
        final Runnable refresh = () -> sw.setChecked(getter.get());
        refresh.run();
        refreshers.add(refresh);
        sw.setOnClickListener(v -> {
            pushUndo();
            setter.set(sw.isChecked());
            committed();
        });
        box.setOnClickListener(v -> {
            pushUndo();
            sw.setChecked(!sw.isChecked());
            setter.set(sw.isChecked());
            committed();
        });
        return box;
    }

    // --- small helpers

    private int dp(float v) {
        return Math.round(v * density);
    }

    private int themeColor(int attr) {
        final TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, tv, true);
        if (tv.resourceId != 0) {
            return getResources().getColorStateList(tv.resourceId, getContext().getTheme()).getDefaultColor();
        }
        return tv.data;
    }

    private android.graphics.drawable.Drawable selectable() {
        final TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getContext().getDrawable(tv.resourceId);
    }

    private LinearLayout row() {
        final LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String text) {
        final TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return tv;
    }

    private TextView hint(int text) {
        final TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setAlpha(0.75f);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    private Button textButton(int text) {
        final Button b = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        return b;
    }

    private Button outlinedButton(int text) {
        final Button b = textButton(text);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setStroke(Math.max(1, dp(1)), themeColor(android.R.attr.textColorSecondary));
        b.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, selectable()}));
        b.setPadding(dp(18), 0, dp(18), 0);
        return b;
    }

    private Button nudgeButton(String symbol, int description) {
        final Button b = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        b.setText(symbol);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setContentDescription(getContext().getString(description));
        b.setMinWidth(dp(48));
        b.setMinimumWidth(dp(48));
        b.setPadding(0, 0, 0, 0);
        b.setLayoutParams(new LayoutParams(dp(52), dp(52)));
        return b;
    }

    private LayoutParams spaced(int width) {
        final LayoutParams lp = new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private LayoutParams marginStart(int margin) {
        final LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(margin);
        return lp;
    }

    private LayoutParams marginEnd(int margin) {
        return marginEnd(margin, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LayoutParams marginEnd(int margin, int width, int height) {
        final LayoutParams lp = new LayoutParams(width, height);
        lp.setMarginEnd(margin);
        return lp;
    }
}
