// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.content.Context;

import static net.pgaskin.windy.CustomTheme.*;

/**
 * Built-in looks to start from (replacing the old fixed themes in the app).
 * Applying one replaces the current look (undo brings it back).
 */
final class StartingPoints {
    private StartingPoints() {
    }

    static final class Look {
        final String name;
        private final int[] colors = new int[COLOR_COUNT];
        private final float[][] params = new float[PARAM_COUNT][]; // null = default
        private int effects;
        private final Integer[] effectColors = new Integer[EFFECT_COUNT * MAX_STOPS]; // null = default
        private final Float[] effectParams = new Float[EFFECT_COUNT * EFFECT_PARAM_COUNT]; // null = default

        private Look(String name) {
            this.name = name;
        }

        /** Streak colors (slow and fast wind, the alpha is the strength), then the background (calm and windy areas). */
        Look colors(int slow, int fast, int bg1, int bg2) {
            colors[COLOR_SLOW] = slow;
            colors[COLOR_FAST] = fast;
            colors[COLOR_BG1] = bg1;
            colors[COLOR_BG2] = bg2;
            return this;
        }

        Look p(int param, float value) {
            params[param] = new float[]{value};
            return this;
        }

        /** Turns an effect on with its gradient stops (2-4 of them). */
        Look fx(int effect, int... stops) {
            effects |= 1 << effect;
            for (int i = 0; i < stops.length; i++) {
                effectColors[effectColorIndex(effect, i)] = stops[i];
            }
            if (stops.length >= MIN_STOPS) {
                effectParams[effectParamIndex(effect, EFFECT_PARAM_STOPS)] = (float) stops.length;
            }
            return this;
        }

        Look fp(int effect, int param, float value) {
            effectParams[effectParamIndex(effect, param)] = value;
            return this;
        }

        CustomTheme.Snapshot build() {
            final float[] p = themeParams(Themes.CUSTOM);
            for (int i = 0; i < PARAM_COUNT; i++) {
                if (params[i] != null) {
                    p[i] = params[i][0];
                }
            }
            final int[] ec = defaultEffectColors();
            for (int i = 0; i < ec.length; i++) {
                if (effectColors[i] != null) {
                    ec[i] = effectColors[i];
                }
            }
            final float[] ep = defaultEffectParams();
            for (int i = 0; i < ep.length; i++) {
                if (effectParams[i] != null) {
                    ep[i] = effectParams[i];
                }
            }
            return snapshotOf(colors.clone(), p, effects, ec, ep);
        }
    }

    private static Look look(String name) {
        return new Look(name);
    }

    static final Look[] ALL = {
            // 1: deep blue night, slow drifting snow over soft swirling streaks
            look("Snow Globe")
                    .colors(0x4D9FB8D8, 0x59E8F1FF, 0xFF0B1426, 0xFF1B2A44)
                    .p(PARAM_PARTICLE_OPACITY, 0.55f).p(PARAM_ALPHA_DECAY, 0.97f).p(PARAM_WIND_SPEED, 0.12f)
                    .p(PARAM_PARTICLE_COUNT, 3072).p(PARAM_VARIATION, 0.5f).p(PARAM_TURBULENCE, 0.6f)
                    .p(PARAM_MOMENTUM, 0.6f).p(PARAM_BUOYANCY, 0.3f).p(PARAM_SIZE_VARIATION, 0.4f).p(PARAM_WARP, 0.3f)
                    .fx(EFFECT_SNOW, 0xE6FFFFFF, 0xD9C8DCFF, 0xCCD8CCFF)
                    .fp(EFFECT_SNOW, 0, 2.5f).fp(EFFECT_SNOW, 2, 0.25f).fp(EFFECT_SNOW, 3, 0.6f)
                    .fp(EFFECT_SNOW, 4, 0.7f).fp(EFFECT_SNOW, 5, 0.5f),

            // 2: wide soft ink strokes slowly shifting teal, pink, and gold
            look("Ink in Water")
                    .colors(0x40408C96, 0x4DA0E0E0, 0xFF061A1F, 0xFF0D2B33)
                    .p(PARAM_PARTICLE_COUNT, 1536).p(PARAM_ALPHA_DECAY, 0.995f).p(PARAM_VARIATION, 0.6f)
                    .p(PARAM_SIZE_VARIATION, 0.5f).p(PARAM_TURBULENCE, 0.5f).p(PARAM_WARP, 0.4f).p(PARAM_MOMENTUM, 0.5f)
                    .fx(EFFECT_INK, 0xCC5CC8D2, 0xCCF08CB4, 0xCCF0C864)
                    .fp(EFFECT_INK, 0, 3.5f).fp(EFFECT_INK, 1, 4.0f).fp(EFFECT_INK, 2, 0.7f).fp(EFFECT_INK, 3, 0.8f)
                    .fp(EFFECT_INK, EFFECT_PARAM_CYCLE, 0.03f),

            // 3: dim green grass blowing, with gold and lime fireflies floating up
            look("Firefly Meadow")
                    .colors(0x4D2E5A35, 0x596FA35A, 0xFF050D07, 0xFF0E1F12)
                    .p(PARAM_PARTICLE_OPACITY, 0.4f).p(PARAM_REAL_WIND, 0.6f).p(PARAM_TURBULENCE, 0.4f)
                    .p(PARAM_MOMENTUM, 0.5f).p(PARAM_BUOYANCY, 0.5f).p(PARAM_VARIATION, 0.6f).p(PARAM_SIZE_VARIATION, 0.3f)
                    .fx(EFFECT_FIREFLY, 0xFFFFD45A, 0xFFB4F05A)
                    .fp(EFFECT_FIREFLY, 0, 0.3f).fp(EFFECT_FIREFLY, 1, 10f).fp(EFFECT_FIREFLY, 2, 0.25f).fp(EFFECT_FIREFLY, 3, 4f),

            // 4: big lazy curtains of teal, green, violet, and pink glow
            look("Aurora")
                    .colors(0x591E8F7A, 0x668A5CF0, 0xFF02040A, 0xFF0A1030)
                    .p(PARAM_ALPHA_DECAY, 0.99f).p(PARAM_TURBULENCE, 0.7f).p(PARAM_SWIRL_SIZE, 2.0f).p(PARAM_WARP, 0.5f)
                    .p(PARAM_MOMENTUM, 0.4f).p(PARAM_SIZE_VARIATION, 0.3f).p(PARAM_VARIATION, 0.4f)
                    .fx(EFFECT_NEON, 0xCC28E6B4, 0xCC5AF078, 0xCC9B6BF0, 0xCCF06BC8)
                    .fp(EFFECT_NEON, 0, 1.6f).fp(EFFECT_NEON, 1, 2.2f).fp(EFFECT_NEON, 3, 0.4f)
                    .fp(EFFECT_NEON, EFFECT_PARAM_CYCLE, 0.05f),

            // 5: fast white-blue comets with long tails over deep violet
            look("Comet Rain")
                    .colors(0x404060C0, 0x4DA0B4FF, 0xFF050510, 0xFF120A24)
                    .p(PARAM_WIND_SPEED, 0.3f).p(PARAM_TRAIL_FOLLOW_SPEED, 1f).p(PARAM_ALPHA_DECAY, 0.93f)
                    .p(PARAM_VARIATION, 0.5f).p(PARAM_SIZE_VARIATION, 0.5f).p(PARAM_SIZE_STYLE, 1f)
                    .fx(EFFECT_COMET, 0xF2FFFFFF, 0xE68CAAFF, 0xE6B48CFF)
                    .fp(EFFECT_COMET, 0, 3f).fp(EFFECT_COMET, 1, 0.8f).fp(EFFECT_COMET, 2, 2.5f).fp(EFFECT_COMET, 3, 0.4f),

            // 6: warm dusk, gusts flare orange, red, and magenta
            look("Sunset Gusts")
                    .colors(0x66FFB26B, 0x73FF5E7A, 0xFF2A0E1E, 0xFF4A1E2A)
                    .p(PARAM_PARTICLE_OPACITY, 0.6f).p(PARAM_TURBULENCE, 0.3f).p(PARAM_MOMENTUM, 0.3f).p(PARAM_VARIATION, 0.5f)
                    .fx(EFFECT_SPEED, 0xD9FF9A3C, 0xF2FF3C3C, 0xF2E63CA0, 0xFFFFE6C8)
                    .fp(EFFECT_SPEED, 0, 0.2f).fp(EFFECT_SPEED, 1, 0.4f),

            // 7: thin grey and white silk threads, very mixed widths, no effects
            look("Monochrome Silk")
                    .colors(0x599A9A9A, 0x73FFFFFF, 0xFF0A0A0A, 0xFF151515)
                    .p(PARAM_PARTICLE_OPACITY, 0.7f).p(PARAM_LINE_HALF_WIDTH, 0.6f).p(PARAM_ALPHA_DECAY, 0.985f)
                    .p(PARAM_SIZE_VARIATION, 0.7f).p(PARAM_VARIATION, 0.7f).p(PARAM_MOMENTUM, 0.6f)
                    .p(PARAM_TURBULENCE, 0.4f).p(PARAM_SWIRL_SIZE, 1.6f),

            // 8: slow, heavy currents, dense in some places, with faint ink
            look("Deep Ocean")
                    .colors(0x4D0E5A7A, 0x665FD3F0, 0xFF001018, 0xFF00243A)
                    .p(PARAM_REAL_WIND, 0.7f).p(PARAM_TURBULENCE, 0.8f).p(PARAM_SWIRL_SIZE, 2.5f).p(PARAM_MOMENTUM, 0.7f)
                    .p(PARAM_BUOYANCY, 0.2f).p(PARAM_CLUMPING, 0.5f).p(PARAM_VARIATION, 0.4f)
                    .fx(EFFECT_INK, 0x8C0A3C78, 0x993CC8F0)
                    .fp(EFFECT_INK, 0, 2f).fp(EFFECT_INK, 1, 2f).fp(EFFECT_INK, 3, 0.5f),

            // 9: rising sparks swirling up from a dark fire
            look("Ember Storm")
                    .colors(0x598A2A0A, 0x73FF8A2A, 0xFF120502, 0xFF2A0A04)
                    .p(PARAM_WIND_SPEED, 0.2f).p(PARAM_ALPHA_DECAY, 0.95f).p(PARAM_BUOYANCY, 0.8f).p(PARAM_MOMENTUM, 0.3f)
                    .p(PARAM_TURBULENCE, 1.0f).p(PARAM_SWIRL_SIZE, 0.6f).p(PARAM_SIZE_VARIATION, 0.6f).p(PARAM_SIZE_STYLE, 1f)
                    .fx(EFFECT_FIREFLY, 0xFFFF8C28, 0xFFFFDC50)
                    .fp(EFFECT_FIREFLY, 0, 0.2f).fp(EFFECT_FIREFLY, 1, 6f).fp(EFFECT_FIREFLY, 2, 1.2f).fp(EFFECT_FIREFLY, 3, 6f),

            // 10: soft, wide lavender smoke with a gentle glow
            look("Lavender Haze")
                    .colors(0x598E7BC8, 0x66E6D9FF, 0xFF1A1428, 0xFF2E2440)
                    .p(PARAM_PARTICLE_OPACITY, 0.45f).p(PARAM_LINE_HALF_WIDTH, 1.5f).p(PARAM_ALPHA_DECAY, 0.992f)
                    .p(PARAM_TURBULENCE, 0.5f).p(PARAM_SWIRL_SIZE, 3f).p(PARAM_EVOLUTION, 0.15f).p(PARAM_MOMENTUM, 0.8f)
                    .p(PARAM_CLUMPING, 0.6f)
                    .fx(EFFECT_NEON, 0x99B48CFF, 0x99FFA0D2)
                    .fp(EFFECT_NEON, 0, 2.5f).fp(EFFECT_NEON, 1, 1.0f).fp(EFFECT_NEON, 2, 0f),

            // 11: black, electric blue streaks with a cycling neon glow
            look("Electric Night")
                    .colors(0x662040FF, 0x8000F0FF, 0xFF000000, 0xFF06061A)
                    .p(PARAM_WIND_SPEED, 0.25f).p(PARAM_TRAIL_FOLLOW_SPEED, 1f).p(PARAM_VARIATION, 0.4f)
                    .fx(EFFECT_NEON, 0xCC00F0FF, 0xCCFF28DC)
                    .fp(EFFECT_NEON, 1, 2.5f).fp(EFFECT_NEON, EFFECT_PARAM_CYCLE, 0.1f)
                    .fx(EFFECT_SPEED, 0xE6FFFFFF, 0xFFC8F0FF),

            // 12: light sand background, dark ink-like threads, drifting gold dust
            look("Dust & Light")
                    .colors(0x666B5A48, 0x802A2018, 0xFFE9E2D6, 0xFFCFC4B2)
                    .p(PARAM_PARTICLE_OPACITY, 0.5f).p(PARAM_SIZE_VARIATION, 0.8f).p(PARAM_SIZE_STYLE, 2f)
                    .p(PARAM_TURBULENCE, 0.4f).p(PARAM_MOMENTUM, 0.5f).p(PARAM_CLUMPING, 0.4f)
                    .fx(EFFECT_SNOW, 0xCCC89628, 0xCCE6B450)
                    .fp(EFFECT_SNOW, 0, 1.5f).fp(EFFECT_SNOW, 2, 0f).fp(EFFECT_SNOW, 4, 0.25f).fp(EFFECT_SNOW, 5, 0.6f),
    };
}
