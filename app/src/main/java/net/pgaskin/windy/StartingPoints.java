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
        private String code; // an exported look instead of the above, or null

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
            if (code != null) {
                return snapshotFromCode(code);
            }
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

    /** A look from an exported code (see {@link CustomTheme#exportCode}). */
    private static Look fromCode(String name, String code) {
        final Look look = new Look(name);
        look.code = code;
        return look;
    }

    static final Look[] ALL = {
            // 1: dim green grass blowing, with gold and lime fireflies floating up
            look("Firefly Meadow")
                    .colors(0x4D2E5A35, 0x596FA35A, 0xFF050D07, 0xFF0E1F12)
                    .p(PARAM_PARTICLE_OPACITY, 0.4f).p(PARAM_REAL_WIND, 0.6f).p(PARAM_TURBULENCE, 0.4f)
                    .p(PARAM_MOMENTUM, 0.5f).p(PARAM_BUOYANCY, 0.5f).p(PARAM_VARIATION, 0.6f).p(PARAM_SIZE_VARIATION, 0.3f)
                    .fx(EFFECT_FIREFLY, 0xFFFFD45A, 0xFFB4F05A)
                    .fp(EFFECT_FIREFLY, 0, 0.3f).fp(EFFECT_FIREFLY, 1, 10f).fp(EFFECT_FIREFLY, 2, 0.25f).fp(EFFECT_FIREFLY, 3, 4f),
            // 2: rising sparks swirling up from a dark fire
            look("Ember Storm")
                    .colors(0x598A2A0A, 0x73FF8A2A, 0xFF120502, 0xFF2A0A04)
                    .p(PARAM_WIND_SPEED, 0.2f).p(PARAM_ALPHA_DECAY, 0.95f).p(PARAM_BUOYANCY, 0.8f).p(PARAM_MOMENTUM, 0.3f)
                    .p(PARAM_TURBULENCE, 1.0f).p(PARAM_SWIRL_SIZE, 0.6f).p(PARAM_SIZE_VARIATION, 0.6f).p(PARAM_SIZE_STYLE, 1f)
                    .fx(EFFECT_FIREFLY, 0xFFFF8C28, 0xFFFFDC50)
                    .fp(EFFECT_FIREFLY, 0, 0.2f).fp(EFFECT_FIREFLY, 1, 6f).fp(EFFECT_FIREFLY, 2, 1.2f).fp(EFFECT_FIREFLY, 3, 6f),
            // 3: thin grey and white silk threads, very mixed widths, no effects
            look("Monochrome Silk")
                    .colors(0x599A9A9A, 0x73FFFFFF, 0xFF0A0A0A, 0xFF151515)
                    .p(PARAM_PARTICLE_OPACITY, 0.7f).p(PARAM_LINE_HALF_WIDTH, 0.6f).p(PARAM_ALPHA_DECAY, 0.985f)
                    .p(PARAM_SIZE_VARIATION, 0.7f).p(PARAM_VARIATION, 0.7f).p(PARAM_MOMENTUM, 0.6f)
                    .p(PARAM_TURBULENCE, 0.4f).p(PARAM_SWIRL_SIZE, 1.6f),
            // 4: black, electric blue streaks with a cycling neon glow
            look("Electric Night")
                    .colors(0x662040FF, 0x8000F0FF, 0xFF000000, 0xFF06061A)
                    .p(PARAM_WIND_SPEED, 0.25f).p(PARAM_TRAIL_FOLLOW_SPEED, 1f).p(PARAM_VARIATION, 0.4f)
                    .fx(EFFECT_NEON, 0xCC00F0FF, 0xCCFF28DC)
                    .fp(EFFECT_NEON, 1, 2.5f).fp(EFFECT_NEON, EFFECT_PARAM_CYCLE, 0.1f)
                    .fx(EFFECT_SPEED, 0xE6FFFFFF, 0xFFC8F0FF),
            // 5: fast white-blue comets with long tails over deep violet
            look("Comet Rain")
                    .colors(0x404060C0, 0x4DA0B4FF, 0xFF050510, 0xFF120A24)
                    .p(PARAM_WIND_SPEED, 0.3f).p(PARAM_TRAIL_FOLLOW_SPEED, 1f).p(PARAM_ALPHA_DECAY, 0.93f)
                    .p(PARAM_VARIATION, 0.5f).p(PARAM_SIZE_VARIATION, 0.5f).p(PARAM_SIZE_STYLE, 1f)
                    .fx(EFFECT_COMET, 0xF2FFFFFF, 0xE68CAAFF, 0xE6B48CFF)
                    .fp(EFFECT_COMET, 0, 3f).fp(EFFECT_COMET, 1, 0.8f).fp(EFFECT_COMET, 2, 2.5f).fp(EFFECT_COMET, 3, 0.4f),

            // 6: by Coco: sparse crystal shards with every effect on, sharp short trails
            fromCode("Coco's Crystals", "{\"windyfx\":1,\"presets\":[{\"name\":\"Coco's Crystals\",\"colors\":\"86003c45,827cff52,ff000000,ff000000\",\"line_half_width\":1.7999999523162842,\"particle_opacity\":0.5400000214576721,\"alpha_decay\":0.6499999761581421,\"wind_speed\":0.17000000178813934,\"particle_count\":768,\"particle_life\":6,\"trail_follow_speed\":1,\"variation\":0.9900000095367432,\"clumping\":0.949999988079071,\"real_wind\":0.8899999856948853,\"turbulence\":0.550000011920929,\"swirl_size\":1.5583292245864868,\"evolution\":0.23000000417232513,\"warp\":0.6399999856948853,\"momentum\":0.10999999940395355,\"buoyancy\":0,\"size_variation\":0.8199999928474426,\"size_style\":1,\"effects\":63,\"effect_colors\":\"fe3e4400,0d003791,6affea00,23ffcd00,52ffe300,0b876e1a,ff000000,62a49f04,19000000,ff00567a,14140c48,78cfb62c,f2e61a99,ff4de6ff,52d9ccff,e6b366ff,49ffecb3,58007a15,ffffe6cc,ffff80cc,56b3ffff,e6ffd966,5a80ffbf,300f7354\",\"effect_params\":\"0.03,0.25,0.91,0.0,0.0,0.0,4.0,-0.375,1.0,15.0,0.03139628,10.0,0.0,0.0,2.0,0.203125,5.7999997,1.0,0.06,0.1,0.06,0.16,4.0,-0.3125,2.1,0.0,0.63728034,0.74,0.0,0.0,2.0,-0.21875,1.4,4.4416747,0.31,0.53,0.0,0.0,4.0,-0.421875,3.1499999,2.1,0.0,0.4,0.0,0.0,4.0,0.296875\"}]}"),
    };
}
