// SPDX-FileCopyrightText: 2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CustomTheme {
    private static final String TAG = "CustomTheme";

    public static final class ColorSpec {
        public final int label; // human-readable
        public final boolean hasAlpha;

        private ColorSpec(int label, boolean hasAlpha) {
            this.label = label;
            this.hasAlpha = hasAlpha;
        }
    }

    // do not change the order or add more values (it must match native and saved prefs)
    public static final int COLOR_SLOW = 0;
    public static final int COLOR_FAST = 1;
    public static final int COLOR_BG1 = 2;
    public static final int COLOR_BG2 = 3;

    public static final ColorSpec[] COLORS = { // [COLOR_*]
            new ColorSpec(R.string.custom_color_slow, true),
            new ColorSpec(R.string.custom_color_fast, true),
            new ColorSpec(R.string.custom_color_bg1, false), // alpha doesn't affect the other colors
            new ColorSpec(R.string.custom_color_bg2, false),
    };

    public static final int COLOR_COUNT = COLORS.length;

    public static final class ParamSpec {
        public final String key; // pref key name
        public final int label; // human-readable
        public final float min, max;
        public final int steps;
        public final int decimals;
        public final boolean logScale; // trail decay only really matters near 1.0
        public final boolean expScale; // multipliers, so 1/2x and 2x are equally far from 1x

        private ParamSpec(String key, int label, float min, float max, int steps, int decimals, boolean logScale) {
            this(key, label, min, max, steps, decimals, logScale, false);
        }

        private ParamSpec(String key, int label, float min, float max, int steps, int decimals, boolean logScale, boolean expScale) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.steps = steps;
            this.decimals = decimals;
            this.logScale = logScale;
            this.expScale = expScale;
        }

        /** The value at a slider position. */
        public float value(int progress) {
            final float fraction = progress / (float) steps;
            if (expScale) {
                return (float) (min * Math.pow(max / min, fraction));
            }
            if (logScale) {
                final double lo = Math.log1p(-min);
                final double hi = Math.log1p(-max);
                return (float) (1.0 - Math.exp(lo + (hi - lo) * fraction));
            }
            return min + (max - min) * fraction;
        }

        /** The slider position for a value. */
        public int progress(float value) {
            final double fraction;
            if (expScale) {
                fraction = Math.log(Math.max(value, min) / min) / Math.log(max / min);
            } else if (logScale) {
                final double lo = Math.log1p(-min);
                final double hi = Math.log1p(-max);
                fraction = (Math.log1p(-Math.min(value, 0.999999)) - lo) / (hi - lo);
            } else {
                fraction = (value - min) / (max - min);
            }
            return Math.round((float) (Math.max(0.0, Math.min(fraction, 1.0)) * steps));
        }

        /** The value text. */
        public String text(float value) {
            return String.format(Locale.getDefault(), "%." + decimals + "f", value);
        }
    }

    // do not change the order or the keys (it must match native and saved prefs)
    public static final int PARAM_LINE_HALF_WIDTH = 0;
    public static final int PARAM_PARTICLE_OPACITY = 1;
    public static final int PARAM_ALPHA_DECAY = 2;
    public static final int PARAM_WIND_SPEED = 3;
    public static final int PARAM_PARTICLE_COUNT = 4;
    public static final int PARAM_PARTICLE_LIFE = 5;

    public static final ParamSpec[] PARAMS = { // [PARAM_*]
            new ParamSpec("line_half_width", R.string.param_line_width, 0.25f, 4.0f, 75, 2, false),
            new ParamSpec("particle_opacity", R.string.param_opacity, 0.0f, 2.0f, 100, 2, false),
            new ParamSpec("alpha_decay", R.string.param_trail_decay, 0.95f, 0.9999f, 500, 4, true),
            new ParamSpec("wind_speed", R.string.param_wind_speed, 0.0f, 2.0f, 200, 3, false),
            new ParamSpec("particle_count", R.string.param_particle_count, 256, 16384, 63, 0, false),
            new ParamSpec("particle_life", R.string.param_particle_life, 1.0f, 30.0f, 58, 1, false),
    };

    public static final int PARAM_COUNT = PARAMS.length;

    public static final class EffectSpec {
        public final String key; // preset key name
        public final int label; // human-readable
        public final int summary; // human-readable
        public final int gradient; // human-readable, what the gradient follows
        public final ParamSpec[] params; // [EFFECT_PARAM_COUNT max]

        private EffectSpec(String key, int label, int summary, int gradient, ParamSpec... params) {
            this.key = key;
            this.label = label;
            this.summary = summary;
            this.gradient = gradient;
            this.params = params;
        }
    }

    private static ParamSpec lin(int label, float min, float max, int steps, int decimals) {
        return new ParamSpec("", label, min, max, steps, decimals, false, false);
    }

    private static ParamSpec exp(int label, float min, float max, int steps, int decimals) {
        return new ParamSpec("", label, min, max, steps, decimals, false, true);
    }

    public static final int EFFECT_PARAM_COUNT = 8; // per effect (must match native)
    public static final int EFFECT_PARAM_STOPS = 6; // number of gradient stops (must match native)
    public static final int EFFECT_PARAM_CYCLE = 7; // gradient cycle speed in Hz (must match native)
    public static final int MIN_STOPS = 2;
    public static final int MAX_STOPS = 4; // must match native

    /** The gradient cycle speed (negative reverses, whole cycles per 64s). */
    public static final ParamSpec CYCLE_SPEC = lin(R.string.fx_cycle, -1.0f, 1.0f, 128, 2);
    private static final int EFFECT_PARAM_COUNT_V1 = 4; // in older saves

    // do not change the order (it must match native and saved prefs)
    public static final int EFFECT_SPEED = 0;
    public static final int EFFECT_FIREFLY = 1;
    public static final int EFFECT_SNOW = 2;
    public static final int EFFECT_COMET = 3;
    public static final int EFFECT_INK = 4;
    public static final int EFFECT_NEON = 5;

    public static final EffectSpec[] EFFECTS = { // [EFFECT_*]
            new EffectSpec("speed", R.string.effect_speed, R.string.effect_speed_summary, R.string.gradient_gusts,
                    lin(R.string.fx_threshold, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_range, 0.01f, 1.0f, 99, 2),
                    lin(R.string.fx_opacity, 0.0f, 1.0f, 100, 2)),
            new EffectSpec("firefly", R.string.effect_firefly, R.string.effect_firefly_summary, R.string.gradient_random,
                    lin(R.string.fx_amount, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_size, 2.0f, 30.0f, 56, 1),
                    exp(R.string.fx_pulse_rate, 0.02f, 3.0f, 100, 2),
                    lin(R.string.fx_pulse_sharpness, 0.5f, 10.0f, 95, 1)),
            new EffectSpec("snow", R.string.effect_snow, R.string.effect_snow_summary, R.string.gradient_random,
                    lin(R.string.fx_size, 0.5f, 8.0f, 75, 1),
                    lin(R.string.fx_size_variation, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_fall, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_softness, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_snow_amount, 0.0f, 1.0f, 100, 2)),
            new EffectSpec("comet", R.string.effect_comet, R.string.effect_comet_summary, R.string.gradient_comet,
                    lin(R.string.fx_head_size, 1.0f, 10.0f, 90, 1),
                    lin(R.string.fx_white_core, 0.0f, 1.0f, 100, 2),
                    exp(R.string.fx_tail_length, 0.25f, 8.0f, 100, 2),
                    lin(R.string.fx_halo, 0.0f, 1.0f, 100, 2)),
            new EffectSpec("ink", R.string.effect_ink, R.string.effect_ink_summary, R.string.gradient_calm_fast,
                    lin(R.string.fx_width, 1.0f, 8.0f, 70, 1),
                    exp(R.string.fx_linger, 0.25f, 10.0f, 100, 2),
                    lin(R.string.fx_softness, 0.0f, 1.0f, 100, 2),
                    lin(R.string.fx_density, 0.0f, 1.0f, 100, 2)),
            new EffectSpec("neon", R.string.effect_neon, R.string.effect_neon_summary, R.string.gradient_calm_fast,
                    lin(R.string.fx_radius, 0.5f, 4.0f, 70, 2),
                    lin(R.string.fx_brightness, 0.0f, 5.0f, 100, 2),
                    lin(R.string.fx_quality, 0.0f, 1.0f, 1, 0),
                    lin(R.string.fx_pulse, 0.0f, 1.0f, 100, 2)),
    };

    public static final int EFFECT_COUNT = EFFECTS.length;
    private static final int EFFECT_MASK = (1 << EFFECT_COUNT) - 1;

    private static final int MAX_PRESETS = 32;
    private static final int MAX_NAME_LENGTH = 40;

    private static final AtomicInteger seq = new AtomicInteger();
    private static final Set<Runnable> listeners = ConcurrentHashMap.newKeySet();

    private static volatile int[] colors; // array values immutable once set
    private static volatile float[] params;
    private static volatile int effects = -1; // bitmask of 1 << EFFECT_*, -1 if not loaded
    private static volatile int[] effectColors; // [EFFECT_COUNT starts, then EFFECT_COUNT ends]
    private static volatile float[] effectParams; // [effect * EFFECT_PARAM_COUNT + param]

    private CustomTheme() {
    }

    public static int currentSeq() {
        return seq.get();
    }

    public static int[] colors(Context context) {
        int[] current = colors;
        if (current == null) {
            synchronized (CustomTheme.class) {
                current = colors;
                if (current == null) {
                    current = parseColors(Prefs.get(context).getString(Prefs.KEY_CUSTOM_COLORS, null));
                    colors = current;
                }
            }
        }
        return current.clone();
    }

    public static int color(Context context, int component) {
        return colors(context)[component];
    }

    public static int[] themeColors(int themeIndex) {
        final int[] result = new int[COLOR_COUNT];
        for (int i = 0; i < COLOR_COUNT; i++) {
            result[i] = WindyWallpaperNative.themeColor(themeIndex, i);
        }
        return result;
    }

    public static float[] params(Context context) {
        float[] current = params;
        if (current == null) {
            synchronized (CustomTheme.class) {
                current = params;
                if (current == null) {
                    current = loadParams(Prefs.get(context));
                    params = current;
                }
            }
        }
        return current.clone();
    }

    public static float param(Context context, int param) {
        return params(context)[param];
    }

    public static float[] themeParams(int themeIndex) {
        final float[] result = new float[PARAM_COUNT];
        for (int i = 0; i < PARAM_COUNT; i++) {
            result[i] = WindyWallpaperNative.themeParam(themeIndex, i);
        }
        return result;
    }

    /** The enabled effects, as a bitmask of {@code 1 << EFFECT_*}. */
    public static int effects(Context context) {
        int current = effects;
        if (current < 0) {
            synchronized (CustomTheme.class) {
                current = effects;
                if (current < 0) {
                    current = Prefs.get(context).getInt(Prefs.KEY_CUSTOM_EFFECTS, 0) & EFFECT_MASK;
                    effects = current;
                }
            }
        }
        return current;
    }

    public static boolean effectEnabled(Context context, int effect) {
        return (effects(context) & (1 << effect)) != 0;
    }

    /**
     * The effect gradient stops (packed 0xAARRGGBB, the alpha is the
     * strength), see {@link #effectColorIndex}.
     */
    public static int[] effectColors(Context context) {
        int[] current = effectColors;
        if (current == null) {
            synchronized (CustomTheme.class) {
                current = effectColors;
                if (current == null) {
                    current = parseEffectColors(Prefs.get(context).getString(Prefs.KEY_CUSTOM_EFFECT_COLORS, null));
                    effectColors = current;
                }
            }
        }
        return current.clone();
    }

    // stops first so older saves (with only the first two) still line up
    public static int effectColorIndex(int effect, int stop) {
        return stop * EFFECT_COUNT + effect;
    }

    public static int effectColor(Context context, int effect, int stop) {
        return effectColors(context)[effectColorIndex(effect, stop)];
    }

    /** The colors of the gradient stops in use. */
    public static int[] effectGradient(Context context, int effect) {
        final int[] colors = effectColors(context);
        final int[] result = new int[effectStops(context, effect)];
        for (int i = 0; i < result.length; i++) {
            result[i] = colors[effectColorIndex(effect, i)];
        }
        return result;
    }

    /** The number of gradient stops in use. */
    public static int effectStops(Context context, int effect) {
        final int stops = Math.round(effectParam(context, effect, EFFECT_PARAM_STOPS));
        return Math.max(MIN_STOPS, Math.min(MAX_STOPS, stops)); // older saves have 0
    }

    public static void setEffectStops(Context context, int effect, int stops, boolean persist) {
        setEffectParam(context, effect, EFFECT_PARAM_STOPS, Math.max(MIN_STOPS, Math.min(MAX_STOPS, stops)), persist);
    }

    public static int[] defaultEffectColors() {
        final int[] result = new int[EFFECT_COUNT * MAX_STOPS];
        for (int i = 0; i < EFFECT_COUNT; i++) {
            for (int s = 0; s < MAX_STOPS; s++) {
                result[effectColorIndex(i, s)] = WindyWallpaperNative.effectColor(i, s);
            }
        }
        return result;
    }

    /** The effect params, see {@link #effectParamIndex}. */
    public static float[] effectParams(Context context) {
        float[] current = effectParams;
        if (current == null) {
            synchronized (CustomTheme.class) {
                current = effectParams;
                if (current == null) {
                    current = parseEffectParams(Prefs.get(context).getString(Prefs.KEY_CUSTOM_EFFECT_PARAMS, null));
                    effectParams = current;
                }
            }
        }
        return current.clone();
    }

    public static int effectParamIndex(int effect, int param) {
        return effect * EFFECT_PARAM_COUNT + param;
    }

    public static float effectParam(Context context, int effect, int param) {
        return effectParams(context)[effectParamIndex(effect, param)];
    }

    public static float[] defaultEffectParams() {
        final float[] result = new float[EFFECT_COUNT * EFFECT_PARAM_COUNT];
        for (int i = 0; i < EFFECT_COUNT; i++) {
            for (int p = 0; p < EFFECT_PARAM_COUNT; p++) {
                result[effectParamIndex(i, p)] = WindyWallpaperNative.effectParam(i, p);
            }
        }
        return result;
    }

    /**
     * Updates the effects, applying them to the active renderers. The colors
     * and params may be null to preserve the current value. See {@link #set}
     * for persist.
     */
    public static void setEffects(Context context, int nextEffects, int[] nextColors, float[] nextParams, boolean persist) {
        if (nextColors != null) {
            if (nextColors.length != EFFECT_COUNT * MAX_STOPS) {
                throw new IllegalArgumentException("expected " + EFFECT_COUNT * MAX_STOPS + " effect colors");
            }
            effectColors = nextColors.clone();
        }
        if (nextParams != null) {
            if (nextParams.length != EFFECT_COUNT * EFFECT_PARAM_COUNT) {
                throw new IllegalArgumentException("expected " + EFFECT_COUNT * EFFECT_PARAM_COUNT + " effect params");
            }
            effectParams = nextParams.clone();
        }
        effects = nextEffects & EFFECT_MASK;
        if (persist) {
            persist(context);
        }
        notifyChanged();
    }

    public static void setEffectEnabled(Context context, int effect, boolean enabled, boolean persist) {
        final int current = effects(context);
        final int next = enabled ? current | (1 << effect) : current & ~(1 << effect);
        if (next != current) {
            setEffects(context, next, null, null, persist);
        }
    }

    public static void setEffectColor(Context context, int effect, int stop, int argb, boolean persist) {
        final int[] next = effectColors(context);
        final int index = effectColorIndex(effect, stop);
        if (next[index] == argb) {
            return;
        }
        next[index] = argb;
        setEffects(context, effects(context), next, null, persist);
    }

    public static void setEffectParam(Context context, int effect, int param, float value, boolean persist) {
        final float[] next = effectParams(context);
        final int index = effectParamIndex(effect, param);
        if (next[index] == value) {
            return;
        }
        next[index] = value;
        setEffects(context, effects(context), null, next, persist);
    }

    /** Resets one effect's colors and params to the defaults (keeping it on or off). */
    public static void resetEffect(Context context, int effect, boolean persist) {
        final int[] colors = effectColors(context);
        final int[] defaultColors = defaultEffectColors();
        for (int s = 0; s < MAX_STOPS; s++) {
            colors[effectColorIndex(effect, s)] = defaultColors[effectColorIndex(effect, s)];
        }
        final float[] params = effectParams(context);
        final float[] defaultParams = defaultEffectParams();
        for (int p = 0; p < EFFECT_PARAM_COUNT; p++) {
            params[effectParamIndex(effect, p)] = defaultParams[effectParamIndex(effect, p)];
        }
        setEffects(context, effects(context), colors, params, persist);
    }

    /**
     * Updates the colors and params, applying them to the active renderers.
     * Either may be null to preserve the current value. When persist is false,
     * the change is only kept in memory (for live updates while a color is
     * being picked), call {@link #persist} to save it.
     */
    public static void set(Context context, int[] nextColors, float[] nextParams, boolean persist) {
        if (nextColors != null) {
            if (nextColors.length != COLOR_COUNT) {
                throw new IllegalArgumentException("expected " + COLOR_COUNT + " colors");
            }
            colors = nextColors.clone();
        }
        if (nextParams != null) {
            if (nextParams.length != PARAM_COUNT) {
                throw new IllegalArgumentException("expected " + PARAM_COUNT + " params");
            }
            params = nextParams.clone();
        }
        if (persist) {
            persist(context);
        }
        notifyChanged();
    }

    public static void setColors(Context context, int[] next, boolean persist) {
        set(context, next, null, persist);
    }

    public static void setColor(Context context, int color, int argb, boolean persist) {
        final int[] next = colors(context);
        if (next[color] == argb) {
            return;
        }
        next[color] = argb;
        setColors(context, next, persist);
    }

    public static void setParams(Context context, float[] next, boolean persist) {
        set(context, null, next, persist);
    }

    public static void setParam(Context context, int param, float value, boolean persist) {
        final float[] next = params(context);
        if (next[param] == value) {
            return;
        }
        next[param] = value;
        setParams(context, next, persist);
    }

    public static void persist(Context context) {
        final SharedPreferences.Editor edit = Prefs.get(context).edit();
        edit.putString(Prefs.KEY_CUSTOM_COLORS, formatColors(colors(context)));
        final float[] current = params(context);
        for (int i = 0; i < PARAM_COUNT; i++) {
            edit.putFloat(paramKey(i), current[i]);
        }
        edit.putInt(Prefs.KEY_CUSTOM_EFFECTS, effects(context));
        edit.putString(Prefs.KEY_CUSTOM_EFFECT_COLORS, formatColors(effectColors(context)));
        edit.putString(Prefs.KEY_CUSTOM_EFFECT_PARAMS, formatParams(effectParams(context)));
        edit.apply();
    }

    private static String paramKey(int param) {
        return Prefs.KEY_CUSTOM_PARAM_PREFIX + PARAMS[param].key;
    }

    private static float[] loadParams(SharedPreferences prefs) {
        final float[] result = themeParams(Themes.CUSTOM);
        for (int i = 0; i < PARAM_COUNT; i++) {
            result[i] = prefs.getFloat(paramKey(i), result[i]);
        }
        return result;
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyChanged() {
        seq.incrementAndGet();
        WindyWallpaperRenderer.wakeAll();
        if (!listeners.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> {
                for (final Runnable listener : listeners) {
                    listener.run();
                }
            });
        }
    }

    public static final class Preset {
        public final String name;
        public final int[] colors;
        public final float[] params;
        public final int effects;
        public final int[] effectColors;
        public final float[] effectParams;

        Preset(String name, int[] colors, float[] params, int effects, int[] effectColors, float[] effectParams) {
            this.name = name;
            this.colors = colors;
            this.params = params;
            this.effects = effects;
            this.effectColors = effectColors;
            this.effectParams = effectParams;
        }

        /**
         * Whether the current colors and params still match this preset.
         */
        boolean sameLook(Preset other) {
            return Arrays.equals(colors, other.colors) && Arrays.equals(params, other.params)
                    && effects == other.effects && Arrays.equals(effectColors, other.effectColors)
                    && Arrays.equals(effectParams, other.effectParams);
        }

        public boolean matches(Context context) {
            return Arrays.equals(colors, colors(context)) && Arrays.equals(params, params(context))
                    && effects == effects(context) && Arrays.equals(effectColors, effectColors(context))
                    && Arrays.equals(effectParams, effectParams(context));
        }
    }

    public static List<Preset> presets(Context context) {
        final List<Preset> presets = new ArrayList<>();
        final String saved = Prefs.get(context).getString(Prefs.KEY_CUSTOM_PRESETS, null);
        if (saved == null || saved.isEmpty()) {
            return presets;
        }
        try {
            final JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                final Preset preset = presetFromJson(arr.getJSONObject(i), "");
                if (!preset.name.isEmpty()) {
                    presets.add(preset);
                }
            }
        } catch (JSONException ex) {
            Log.w(TAG, "ignoring corrupt presets: " + ex);
        }
        return presets;
    }

    private static Preset presetFromJson(JSONObject obj, String fallbackName) {
        String name = normalizeName(obj.optString("name", ""));
        if (name.isEmpty()) {
            name = normalizeName(fallbackName);
        }
        // presets saved before the params existed use the defaults
        final float[] params = themeParams(Themes.CUSTOM);
        for (int p = 0; p < PARAM_COUNT; p++) {
            params[p] = (float) obj.optDouble(PARAMS[p].key, params[p]);
        }
        // presets saved before the effects existed have none
        final int effects = obj.optInt("effects", 0) & EFFECT_MASK;
        final int[] effectColors = parseEffectColors(obj.optString("effect_colors", null));
        final float[] effectParams = parseEffectParams(obj.optString("effect_params", null));
        return new Preset(name, parseColors(obj.optString("colors", null)), params, effects, effectColors, effectParams);
    }

    private static JSONObject presetToJson(Preset preset) throws JSONException {
        final JSONObject obj = new JSONObject();
        obj.put("name", preset.name);
        obj.put("colors", formatColors(preset.colors));
        for (int p = 0; p < PARAM_COUNT; p++) {
            obj.put(PARAMS[p].key, (double) preset.params[p]);
        }
        obj.put("effects", preset.effects);
        obj.put("effect_colors", formatColors(preset.effectColors));
        obj.put("effect_params", formatParams(preset.effectParams));
        return obj;
    }

    private static final String EXPORT_KEY = "windyfx"; // marks (and versions) exported codes

    /**
     * Exports the current look (named after the selected preset, if any) or
     * all saved presets as a text code for {@link #importCode}.
     */
    public static String exportCode(Context context, boolean all) {
        final List<Preset> presets = new ArrayList<>();
        if (all) {
            presets.addAll(presets(context));
        } else {
            presets.add(new Preset(presetName(context), colors(context), params(context), effects(context), effectColors(context), effectParams(context)));
        }
        try {
            final JSONArray arr = new JSONArray();
            for (final Preset preset : presets) {
                arr.put(presetToJson(preset));
            }
            final JSONObject obj = new JSONObject();
            obj.put(EXPORT_KEY, 1);
            obj.put("presets", arr);
            return obj.toString();
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Imports a code from {@link #exportCode} (surrounding text is ignored),
     * saving each look as a preset (renaming it if a different one has the
     * same name) and switching to the first. Returns the number imported, or
     * -1 if the code isn't valid.
     */
    public static int importCode(Context context, String code) {
        if (code == null) {
            return -1;
        }
        final int start = code.indexOf('{');
        final int end = code.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return -1;
        }
        final List<Preset> imported = new ArrayList<>();
        try {
            final JSONObject obj = new JSONObject(code.substring(start, end + 1));
            final JSONArray arr = obj.optJSONArray("presets");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    imported.add(presetFromJson(arr.getJSONObject(i), "Imported"));
                }
            } else if (obj.has("colors")) {
                imported.add(presetFromJson(obj, "Imported"));
            }
        } catch (JSONException ex) {
            Log.w(TAG, "invalid import code: " + ex);
            return -1;
        }
        if (imported.isEmpty()) {
            return -1;
        }

        final List<Preset> presets = presets(context);
        final List<String> names = new ArrayList<>();
        for (final Preset preset : imported) {
            final String base = preset.name.length() > MAX_NAME_LENGTH - 6
                    ? preset.name.substring(0, MAX_NAME_LENGTH - 6).trim()
                    : preset.name;
            String name = preset.name;
            for (int n = 2; n < 1000; n++) {
                final Preset existing = findPreset(presets, name);
                if (existing == null || existing.sameLook(preset)) {
                    break;
                }
                name = normalizeName(base + " (" + n + ")");
            }
            final Preset renamed = new Preset(name, preset.colors, preset.params, preset.effects, preset.effectColors, preset.effectParams);
            final Preset existing = findPreset(presets, name);
            if (existing != null) {
                presets.set(presets.indexOf(existing), renamed);
            } else {
                presets.add(renamed);
            }
            names.add(name);
        }
        while (presets.size() > MAX_PRESETS) {
            final Preset oldest = presets.get(0);
            if (names.contains(oldest.name)) {
                break; // don't drop what was just imported
            }
            presets.remove(0);
        }
        writePresets(context, presets, presetName(context));
        loadPreset(context, names.get(0));
        return imported.size();
    }

    private static Preset findPreset(List<Preset> presets, String name) {
        for (final Preset preset : presets) {
            if (preset.name.equals(name)) {
                return preset;
            }
        }
        return null;
    }

    public static Preset preset(Context context, String name) {
        for (final Preset preset : presets(context)) {
            if (preset.name.equals(name)) {
                return preset;
            }
        }
        return null;
    }

    public static void savePreset(Context context, String name) {
        name = normalizeName(name);
        if (name.isEmpty()) {
            return;
        }
        final Preset current = new Preset(name, colors(context), params(context), effects(context), effectColors(context), effectParams(context));
        final List<Preset> presets = presets(context);
        boolean replaced = false;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equals(name)) {
                presets.set(i, current);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            while (presets.size() >= MAX_PRESETS) {
                presets.remove(0);
            }
            presets.add(current);
        }
        writePresets(context, presets, name);
    }

    public static void deletePreset(Context context, String name) {
        final List<Preset> presets = presets(context);
        boolean removed = false;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equals(name)) {
                presets.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            writePresets(context, presets, name.equals(presetName(context)) ? "" : presetName(context));
        }
    }

    public static void loadPreset(Context context, String name) {
        final Preset preset = preset(context, name);
        if (preset == null) {
            return;
        }
        Prefs.get(context).edit().putString(Prefs.KEY_CUSTOM_PRESET, preset.name).apply();
        effects = preset.effects;
        effectColors = preset.effectColors.clone();
        effectParams = preset.effectParams.clone();
        set(context, preset.colors, preset.params, true); // also persists the effects
    }

    public static String presetName(Context context) {
        return Prefs.get(context).getString(Prefs.KEY_CUSTOM_PRESET, "");
    }

    public static void setPresetName(Context context, String name) {
        Prefs.get(context).edit().putString(Prefs.KEY_CUSTOM_PRESET, normalizeName(name)).apply();
    }

    public static String normalizeName(String name) {
        name = name == null ? "" : name.trim();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).trim() : name;
    }

    private static void writePresets(Context context, List<Preset> presets, String selected) {
        final JSONArray arr = new JSONArray();
        try {
            for (final Preset preset : presets) {
                arr.put(presetToJson(preset));
            }
        } catch (JSONException ex) {
            Log.e(TAG, "failed to save presets", ex);
            return;
        }
        final SharedPreferences.Editor edit = Prefs.get(context).edit();
        edit.putString(Prefs.KEY_CUSTOM_PRESETS, arr.toString());
        edit.putString(Prefs.KEY_CUSTOM_PRESET, selected);
        edit.apply();
    }

    /**
     * Renders the current colors and params as a {@code Theme} for
     * core/src/config.rs, named after the selected preset, if any.
     */
    public static String toRustCode(Context context) {
        return WindyWallpaperNative.themeSource(presetName(context), colors(context), params(context));
    }

    private static String formatColors(int[] colors) {
        final StringBuilder sb = new StringBuilder();
        for (final int color : colors) {
            if (sb.length() != 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%08x", color));
        }
        return sb.toString();
    }

    private static int[] parseEffectColors(String saved) {
        final int[] result = defaultEffectColors();
        if (saved == null) {
            return result;
        }
        final String[] parts = saved.split(",", -1);
        // older saves only have the starts, which come first
        for (int i = 0; i < result.length && i < parts.length; i++) {
            try {
                result[i] = (int) Long.parseLong(parts[i].trim(), 16);
            } catch (NumberFormatException ex) {
                Log.w(TAG, "ignoring invalid saved effect color " + parts[i]);
            }
        }
        return result;
    }

    private static float[] parseEffectParams(String saved) {
        final float[] result = defaultEffectParams();
        if (saved == null || saved.isEmpty()) {
            return result;
        }
        final String[] parts = saved.split(",", -1);
        final boolean v1 = parts.length == EFFECT_COUNT * EFFECT_PARAM_COUNT_V1; // fewer params per effect
        for (int i = 0; i < parts.length; i++) {
            final int index = v1 ? effectParamIndex(i / EFFECT_PARAM_COUNT_V1, i % EFFECT_PARAM_COUNT_V1) : i;
            if (index >= result.length) {
                break;
            }
            try {
                result[index] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException ex) {
                Log.w(TAG, "ignoring invalid saved effect param " + parts[i]);
            }
        }
        return result;
    }

    private static String formatParams(float[] params) {
        final StringBuilder sb = new StringBuilder();
        for (final float param : params) {
            if (sb.length() != 0) {
                sb.append(',');
            }
            sb.append(Float.toString(param)); // locale-independent
        }
        return sb.toString();
    }

    private static int[] parseColors(String saved) {
        final int[] result = themeColors(Themes.CUSTOM);
        if (saved == null) {
            return result;
        }
        final String[] parts = saved.split(",", -1);
        for (int i = 0; i < COLOR_COUNT && i < parts.length; i++) {
            try {
                result[i] = (int) Long.parseLong(parts[i].trim(), 16);
            } catch (NumberFormatException ex) {
                Log.w(TAG, "ignoring invalid saved color " + parts[i]);
            }
        }
        return result;
    }
}
