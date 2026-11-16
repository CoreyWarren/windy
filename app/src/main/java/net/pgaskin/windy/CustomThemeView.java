// SPDX-FileCopyrightText: 2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class CustomThemeView extends LinearLayout {
    private static final long SLIDE_DURATION = 150;

    private final ColorSwatchView[] swatches = new ColorSwatchView[CustomTheme.COLOR_COUNT];
    private final Runnable customThemeListener = this::refreshColors;

    private Spinner presetSpinner;
    private View presetDelete;
    private View presetCode;

    private boolean active; // not the visibility, which lags the slide out
    private ValueAnimator slide;

    public CustomThemeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        inflate(context, R.layout.custom_theme, this); // so the children are always ours

        presetSpinner = findViewById(R.id.preset_spinner);
        presetDelete = findViewById(R.id.preset_delete);

        final LinearLayout swatchList = findViewById(R.id.swatch_list);
        final LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < CustomTheme.COLOR_COUNT; i++) {
            final int color = i;
            final View item = inflater.inflate(R.layout.custom_swatch, swatchList, false);
            final String label = getContext().getString(CustomTheme.COLORS[color].label);
            swatches[color] = item.findViewById(R.id.swatch_color);
            ((TextView) item.findViewById(R.id.swatch_label)).setText(label);
            item.setContentDescription(label);
            item.setOnClickListener(v -> pickColor(color, label));
            swatchList.addView(item);
        }

        final View generate = inflater.inflate(R.layout.custom_generate, swatchList, false);
        generate.setOnClickListener(v -> showGenerateDialog());
        swatchList.addView(generate);

        final View advanced = inflater.inflate(R.layout.custom_advanced, swatchList, false);
        advanced.setOnClickListener(v -> showAdvancedDialog());
        swatchList.addView(advanced);

        final View effects = inflater.inflate(R.layout.custom_effects, swatchList, false);
        effects.setOnClickListener(v -> showEffectsDialog());
        swatchList.addView(effects);

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final Object item = parent.getItemAtPosition(position);
                final String name = item == null ? "" : item.toString();
                // ignore the placeholder and the selection we just applied
                // (setSelection notifies again when the spinner is laid out)
                if (!name.isEmpty() && !name.equals(CustomTheme.presetName(getContext()))
                        && CustomTheme.preset(getContext(), name) != null) {
                    CustomTheme.loadPreset(getContext(), name);
                    refresh();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.preset_save).setOnClickListener(v -> showSavePresetDialog());
        presetDelete.setOnClickListener(v -> showDeletePresetDialog());

        presetCode = findViewById(R.id.preset_code);
        presetCode.setOnClickListener(v -> showCodeDialog());
        findViewById(R.id.preset_share).setOnClickListener(v -> showShareDialog());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CustomTheme.addListener(customThemeListener); // the colors also change from the dialogs
        refreshColors();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CustomTheme.removeListener(customThemeListener);
    }

    public void setActive(boolean active, boolean animate) {
        if (active) {
            refresh();
        }
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (slide != null) {
            slide.cancel(); // leaves it at whatever height it got to
            slide = null;
        }
        if (!animate) {
            setVisibility(active ? VISIBLE : GONE);
            setFixedHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            return;
        }
        setVisibility(VISIBLE); // it can only have a height while it's shown
        slide = ValueAnimator.ofInt(getHeight(), active ? expandedHeight() : 0);
        slide.setDuration(SLIDE_DURATION);
        slide.setInterpolator(new DecelerateInterpolator());
        slide.addUpdateListener(a -> setFixedHeight((int) a.getAnimatedValue()));
        slide.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (cancelled) {
                    return;
                }
                slide = null;
                if (!CustomThemeView.this.active) {
                    setVisibility(GONE);
                }
                setFixedHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        slide.start();
    }

    private int expandedHeight() {
        final View parent = (View) getParent();
        final int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        measure(MeasureSpec.makeMeasureSpec(Math.max(width, 0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return getMeasuredHeight();
    }

    private void setFixedHeight(int height) {
        final ViewGroup.LayoutParams params = getLayoutParams();
        if (params.height != height) {
            params.height = height;
            setLayoutParams(params); // requests a layout, which moves the button
        }
    }

    public void refresh() {
        refreshColors();
        refreshPresets();
    }

    private void refreshColors() {
        final int[] colors = CustomTheme.colors(getContext());
        for (int i = 0; i < swatches.length; i++) {
            swatches[i].setColor(colors[i]);
        }
    }

    private void refreshPresets() {
        final List<CustomTheme.Preset> presets = CustomTheme.presets(getContext());
        final String selected = CustomTheme.presetName(getContext());
        final List<String> items = new ArrayList<>();
        if (selected.isEmpty()) {
            items.add(getContext().getString(presets.isEmpty() ? R.string.preset_none : R.string.preset_unsaved));
        }
        for (final CustomTheme.Preset preset : presets) {
            items.add(preset.name);
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setSelection(Math.max(items.indexOf(selected), 0));
        presetDelete.setEnabled(!selected.isEmpty());
        presetCode.setVisibility(Prefs.developerMode(getContext()) ? VISIBLE : GONE);
    }

    private void updatePresetSelection() {
        final String name = CustomTheme.presetName(getContext());
        if (!name.isEmpty()) {
            final CustomTheme.Preset preset = CustomTheme.preset(getContext(), name);
            if (preset == null || !preset.matches(getContext())) {
                CustomTheme.setPresetName(getContext(), "");
            }
        }
        refreshPresets();
    }

    private void pickColor(int color, String label) {
        final int initial = CustomTheme.color(getContext(), color);
        ColorPickerDialog.show(getContext(), label, initial, CustomTheme.COLORS[color].hasAlpha,
                picked -> CustomTheme.setColor(getContext(), color, picked, false), // live preview
                picked -> {
                    CustomTheme.setColor(getContext(), color, picked, true);
                    updatePresetSelection();
                });
    }

    private void showAdvancedDialog() {
        final float[] initial = CustomTheme.params(getContext());
        final SeekBar[] sliders = new SeekBar[CustomTheme.PARAM_COUNT];
        final TextView[] values = new TextView[CustomTheme.PARAM_COUNT];

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final LinearLayout container = (LinearLayout) inflater.inflate(R.layout.dialog_advanced, null);

        final View title = inflater.inflate(R.layout.dialog_advanced_title, null);
        title.findViewById(R.id.param_restart).setOnClickListener(v -> WindyWallpaperRenderer.restartAll());

        for (int i = 0; i < CustomTheme.PARAM_COUNT; i++) {
            final int param = i;
            final View row = inflater.inflate(R.layout.custom_param, container, false);
            final CustomTheme.ParamSpec spec = CustomTheme.PARAMS[param];
            ((TextView) row.findViewById(R.id.param_label)).setText(spec.label);
            values[param] = row.findViewById(R.id.param_value);
            sliders[param] = row.findViewById(R.id.param_slider);
            sliders[param].setMax(spec.steps);
            sliders[param].setProgress(spec.progress(initial[param]));
            values[param].setText(spec.text(initial[param]));
            sliders[param].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    final float value = spec.value(progress);
                    values[param].setText(spec.text(value));
                    if (fromUser) {
                        CustomTheme.setParam(getContext(), param, value, false); // live preview
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            container.addView(row);
        }

        final ScrollView advancedScroll = new ScrollView(getContext()); // more params than fit on small screens
        advancedScroll.addView(container);

        final boolean[] accepted = {false};
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setCustomTitle(title)
                .setView(advancedScroll)
                .setPositiveButton(R.string.save, (d, which) -> {
                    accepted[0] = true;
                    CustomTheme.persist(getContext());
                    updatePresetSelection();
                })
                .setNeutralButton(R.string.reset, null) // set below so it doesn't dismiss it
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!accepted[0]) {
                        CustomTheme.setParams(getContext(), initial, false); // undo the live preview
                    }
                })
                .create();

        final Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); // don't dim the wallpaper being previewed
        }
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
            final float[] defaults = CustomTheme.themeParams(Themes.CUSTOM);
            CustomTheme.setParams(getContext(), defaults, false);
            for (int i = 0; i < CustomTheme.PARAM_COUNT; i++) {
                sliders[i].setProgress(CustomTheme.PARAMS[i].progress(defaults[i]));
            }
        });
    }

    private void showEffectsDialog() {
        final int initialEffects = CustomTheme.effects(getContext());
        final int[] initialColors = CustomTheme.effectColors(getContext());
        final float[] initialParams = CustomTheme.effectParams(getContext());
        final Switch[] effectSwitches = new Switch[CustomTheme.EFFECT_COUNT];
        final ColorSwatchView[] effectSwatches = new ColorSwatchView[CustomTheme.EFFECT_COUNT];

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final LinearLayout container = (LinearLayout) inflater.inflate(R.layout.dialog_advanced, null);

        final View title = inflater.inflate(R.layout.dialog_effects_title, null);
        title.findViewById(R.id.effect_restart).setOnClickListener(v -> WindyWallpaperRenderer.restartAll());

        final Runnable refreshRows = () -> {
            final int enabled = CustomTheme.effects(getContext());
            for (int i = 0; i < CustomTheme.EFFECT_COUNT; i++) {
                final boolean on = (enabled & (1 << i)) != 0;
                effectSwitches[i].setChecked(on);
                effectSwatches[i].setGradient(CustomTheme.effectGradient(getContext(), i));
                effectSwatches[i].setAlpha(on ? 1.0f : 0.4f);
            }
        };

        for (int i = 0; i < CustomTheme.EFFECT_COUNT; i++) {
            final int effect = i;
            final CustomTheme.EffectSpec spec = CustomTheme.EFFECTS[effect];
            final String label = getContext().getString(spec.label);
            final View row = inflater.inflate(R.layout.custom_effect, container, false);
            ((TextView) row.findViewById(R.id.effect_label)).setText(label);
            ((TextView) row.findViewById(R.id.effect_summary)).setText(spec.summary);
            effectSwitches[effect] = row.findViewById(R.id.effect_switch);
            effectSwatches[effect] = row.findViewById(R.id.effect_color);
            effectSwitches[effect].setContentDescription(label);
            effectSwatches[effect].setContentDescription(getContext().getString(R.string.effect_tune, label));

            effectSwitches[effect].setOnClickListener(v -> { // only user changes, after toggling
                final boolean checked = effectSwitches[effect].isChecked();
                CustomTheme.setEffectEnabled(getContext(), effect, checked, false); // live preview
                effectSwatches[effect].setAlpha(checked ? 1.0f : 0.4f);
            });

            // the name and the swatch open the effect's own settings
            row.findViewById(R.id.effect_text).setOnClickListener(v -> showEffectDialog(effect, refreshRows));
            effectSwatches[effect].setOnClickListener(v -> showEffectDialog(effect, refreshRows));
            container.addView(row);
        }
        refreshRows.run();

        final ScrollView scroll = new ScrollView(getContext());
        scroll.addView(container);

        final boolean[] accepted = {false};
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setCustomTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.save, (d, which) -> {
                    accepted[0] = true;
                    CustomTheme.persist(getContext());
                    updatePresetSelection();
                })
                .setNeutralButton(R.string.reset, null) // set below so it doesn't dismiss it
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!accepted[0]) {
                        CustomTheme.setEffects(getContext(), initialEffects, initialColors, initialParams, false); // undo the live preview
                    }
                })
                .create();

        final Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); // don't dim the wallpaper being previewed
        }
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
            CustomTheme.setEffects(getContext(), 0, CustomTheme.defaultEffectColors(), CustomTheme.defaultEffectParams(), false);
            refreshRows.run();
        });
    }

    /**
     * One effect's switch, gradient, and sliders. Changes are live and kept in
     * memory, the effects dialog underneath saves or undoes them.
     */
    private void showEffectDialog(int effect, Runnable onChanged) {
        final CustomTheme.EffectSpec spec = CustomTheme.EFFECTS[effect];
        final String label = getContext().getString(spec.label);
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final LinearLayout container = (LinearLayout) inflater.inflate(R.layout.dialog_advanced, null);

        // on/off
        final Switch enabled = new Switch(getContext());
        enabled.setText(R.string.effect_enabled);
        enabled.setChecked(CustomTheme.effectEnabled(getContext(), effect));
        enabled.setOnClickListener(v -> {
            CustomTheme.setEffectEnabled(getContext(), effect, enabled.isChecked(), false); // live preview
            onChanged.run();
        });
        container.addView(enabled);

        // gradient stops, with -/+ to change how many
        final float density = getResources().getDisplayMetrics().density;
        final LinearLayout stopsRow = new LinearLayout(getContext());
        stopsRow.setOrientation(LinearLayout.HORIZONTAL);
        stopsRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView stopsLabel = new TextView(getContext());
        stopsLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stopsRow.addView(stopsLabel);
        final Button fewer = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        final Button more = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        fewer.setText("\u2212");
        more.setText("+");
        fewer.setContentDescription(getContext().getString(R.string.effect_fewer_colors));
        more.setContentDescription(getContext().getString(R.string.effect_more_colors));
        for (final Button b : new Button[]{fewer, more}) {
            b.setTextSize(20);
            b.setMinWidth(Math.round(48 * density));
            b.setMinimumWidth(Math.round(48 * density));
            stopsRow.addView(b);
        }
        container.addView(stopsRow);

        final LinearLayout colors = new LinearLayout(getContext());
        colors.setOrientation(LinearLayout.HORIZONTAL);
        final HorizontalScrollView colorsScroll = new HorizontalScrollView(getContext());
        colorsScroll.addView(colors);
        container.addView(colorsScroll);

        final Runnable rebuildColors = () -> {
            final int stops = CustomTheme.effectStops(getContext(), effect);
            stopsLabel.setText(getContext().getString(R.string.effect_colors_count, stops));
            fewer.setEnabled(stops > CustomTheme.MIN_STOPS);
            more.setEnabled(stops < CustomTheme.MAX_STOPS);
            colors.removeAllViews();
            for (int i = 0; i < stops; i++) {
                final int stop = i;
                final String colorLabel = getContext().getString(R.string.effect_color_n, label, stop + 1);
                final View item = inflater.inflate(R.layout.custom_swatch, colors, false);
                final ColorSwatchView swatch = item.findViewById(R.id.swatch_color);
                swatch.setColor(CustomTheme.effectColor(getContext(), effect, stop));
                ((TextView) item.findViewById(R.id.swatch_label)).setText(String.valueOf(stop + 1));
                item.setContentDescription(colorLabel);
                item.setOnClickListener(v -> ColorPickerDialog.show(getContext(), colorLabel,
                        CustomTheme.effectColor(getContext(), effect, stop), true,
                        picked -> {
                            CustomTheme.setEffectColor(getContext(), effect, stop, picked, false); // live preview
                            swatch.setColor(picked);
                            onChanged.run();
                        },
                        picked -> {
                            // picking a color turns the effect on
                            CustomTheme.setEffectColor(getContext(), effect, stop, picked, false);
                            CustomTheme.setEffectEnabled(getContext(), effect, true, false);
                            enabled.setChecked(true);
                            swatch.setColor(picked);
                            onChanged.run();
                        }));
                colors.addView(item);
            }
        };
        fewer.setOnClickListener(v -> {
            CustomTheme.setEffectStops(getContext(), effect, CustomTheme.effectStops(getContext(), effect) - 1, false);
            rebuildColors.run();
            onChanged.run();
        });
        more.setOnClickListener(v -> {
            CustomTheme.setEffectStops(getContext(), effect, CustomTheme.effectStops(getContext(), effect) + 1, false);
            rebuildColors.run();
            onChanged.run();
        });
        rebuildColors.run();

        final TextView gradient = new TextView(getContext());
        gradient.setText(spec.gradient);
        gradient.setTextSize(12);
        container.addView(gradient);

        // sliders (the effect's own, then the gradient cycle speed)
        final int sliderCount = spec.params.length + 1;
        final SeekBar[] sliders = new SeekBar[sliderCount];
        final int[] sliderParams = new int[sliderCount];
        final CustomTheme.ParamSpec[] sliderSpecs = new CustomTheme.ParamSpec[sliderCount];
        for (int i = 0; i < spec.params.length; i++) {
            sliderParams[i] = i;
            sliderSpecs[i] = spec.params[i];
        }
        sliderParams[spec.params.length] = CustomTheme.EFFECT_PARAM_CYCLE;
        sliderSpecs[spec.params.length] = CustomTheme.CYCLE_SPEC;
        for (int i = 0; i < sliderCount; i++) {
            final int slider = i;
            final int param = sliderParams[slider];
            final CustomTheme.ParamSpec ps = sliderSpecs[slider];
            final float initial = CustomTheme.effectParam(getContext(), effect, param);
            final View row = inflater.inflate(R.layout.custom_param, container, false);
            final TextView value = row.findViewById(R.id.param_value);
            ((TextView) row.findViewById(R.id.param_label)).setText(ps.label);
            sliders[slider] = row.findViewById(R.id.param_slider);
            sliders[slider].setMax(ps.steps);
            sliders[slider].setProgress(ps.progress(initial));
            value.setText(ps.text(initial));
            sliders[slider].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    final float v = ps.value(progress);
                    value.setText(ps.text(v));
                    if (fromUser) {
                        CustomTheme.setEffectParam(getContext(), effect, param, v, false); // live preview
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            container.addView(row);
        }

        final ScrollView scroll = new ScrollView(getContext());
        scroll.addView(container);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(label)
                .setView(scroll)
                .setPositiveButton(R.string.done, null)
                .setNeutralButton(R.string.reset, null) // set below so it doesn't dismiss it
                .setOnDismissListener(d -> onChanged.run())
                .create();

        final Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); // don't dim the wallpaper being previewed
        }
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
            CustomTheme.resetEffect(getContext(), effect, false);
            rebuildColors.run();
            for (int i = 0; i < sliderCount; i++) {
                final float reset = CustomTheme.effectParam(getContext(), effect, sliderParams[i]);
                sliders[i].setProgress(sliderSpecs[i].progress(reset)); // updates the value text
            }
            onChanged.run();
        });
    }

    private void showGenerateDialog() {
        final int[] initial = CustomTheme.colors(getContext());

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final View container = inflater.inflate(R.layout.dialog_generate, null);
        final ColorPickerView picker = container.findViewById(R.id.generate_color);
        final Spinner style = container.findViewById(R.id.generate_style);

        final ArrayAdapter<String> styles = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, Styles.ALL);
        styles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        style.setAdapter(styles);

        picker.setAlphaEnabled(false);
        picker.setColor(WindyWallpaperNative.customTint(initial)); // the current colors' own tint

        final Runnable generate = () -> {
            final int[] colors = WindyWallpaperNative.generateColors(picker.getColor(), style.getSelectedItemPosition());
            if (colors != null) {
                CustomTheme.setColors(getContext(), colors, false); // live preview
            }
        };
        picker.setOnColorChangedListener(color -> generate.run());
        style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                generate.run(); // also runs when the spinner is first added, showing the theme
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final boolean[] accepted = {false};
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.generate_title)
                .setView(container)
                .setPositiveButton(R.string.save, (d, which) -> {
                    accepted[0] = true;
                    CustomTheme.persist(getContext());
                    updatePresetSelection();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!accepted[0]) {
                        CustomTheme.setColors(getContext(), initial, false); // undo the live preview
                    }
                })
                .create();

        final Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); // don't dim the wallpaper being previewed
        }
        dialog.show();
    }

    private void showCodeDialog() {
        final String code = CustomTheme.toRustCode(getContext());

        final int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        final TextView text = new TextView(getContext());
        text.setText(code);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setHorizontallyScrolling(true);
        text.setPadding(padding, padding / 2, padding, 0);

        final HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.addView(text);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.code)
                .setView(scroll)
                .setPositiveButton(R.string.copy, (dialog, which) -> {
                    final ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
                    clipboard.setPrimaryClip(ClipData.newPlainText(getContext().getString(R.string.code), code));
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(getContext(), R.string.copied, Toast.LENGTH_SHORT).show(); // it's shown by the system after this
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showShareDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.share_title)
                .setItems(new CharSequence[]{
                        getContext().getString(R.string.share_copy_current),
                        getContext().getString(R.string.share_copy_all),
                        getContext().getString(R.string.share_import),
                }, (d, which) -> {
                    if (which == 2) {
                        showImportDialog();
                        return;
                    }
                    final boolean all = which == 1;
                    if (all && CustomTheme.presets(getContext()).isEmpty()) {
                        Toast.makeText(getContext(), R.string.share_no_presets, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    final String code = CustomTheme.exportCode(getContext(), all);
                    final ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
                    clipboard.setPrimaryClip(ClipData.newPlainText(getContext().getString(R.string.share_title), code));
                    Toast.makeText(getContext(), R.string.share_copied, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showImportDialog() {
        final EditText input = new EditText(getContext());
        input.setHint(R.string.share_import_hint);
        input.setMaxLines(6);
        final ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
        final ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            final CharSequence text = clip.getItemAt(0).coerceToText(getContext());
            if (text != null && text.toString().contains("windyfx")) {
                input.setText(text); // prefill a copied code
            }
        }

        final int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        final LinearLayout container = new LinearLayout(getContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.share_import)
                .setView(container)
                .setPositiveButton(R.string.share_import_button, (d, which) -> {
                    final int count = CustomTheme.importCode(getContext(), input.getText().toString());
                    if (count < 0) {
                        Toast.makeText(getContext(), R.string.share_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    refreshPresets();
                    Toast.makeText(getContext(), getResources().getQuantityString(R.plurals.share_imported, count, count), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSavePresetDialog() {
        final EditText input = new EditText(getContext());
        input.setSingleLine();
        input.setHint(R.string.preset_name);
        final String current = CustomTheme.presetName(getContext());
        input.setText(current.isEmpty()
                ? getContext().getString(R.string.preset_default_name, CustomTheme.presets(getContext()).size() + 1)
                : current);
        input.selectAll();

        final int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        final LinearLayout container = new LinearLayout(getContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.save_preset_title)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    final String name = CustomTheme.normalizeName(input.getText().toString());
                    if (!name.isEmpty()) {
                        CustomTheme.savePreset(getContext(), name);
                        refreshPresets();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeletePresetDialog() {
        final String name = CustomTheme.presetName(getContext());
        if (name.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete_preset)
                .setMessage(getContext().getString(R.string.delete_preset_message, name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    CustomTheme.deletePreset(getContext(), name);
                    refreshPresets();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
