// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

#[derive(Clone, Debug)]
pub struct Config {
    /// Degrees of longitude shown at once.
    pub window_size: f32,

    /// Parallax overscan (trail buffer is `screen * scale`).
    pub scale: [f32; 2],

    /// Number of simulated particles.
    pub particle_count: u32,

    /// Base speed of simulated particles.
    pub wind_speed: f32,

    // Lifetime of simulated particles.
    pub particle_life: f32,

    pub slow_wind_color: [f32; 4], // rgba [0,1]
    pub fast_wind_color: [f32; 4], // rgba [0,1]
    pub bg_color1: [f32; 4],       // rgba [0,1]
    pub bg_color2: [f32; 4],       // rgba [0,1]

    /// Trail fade per 1/60s step.
    pub alpha_decay: f32,

    /// Scale the trail fade with the wind speed (relative to
    /// `Config::TRAIL_REFERENCE_WIND_SPEED`), so streak length stays about the
    /// same at any speed.
    pub trail_follow_speed: bool,

    /// Faster trail fade applied briefly after the wind field or location
    /// changes, to clear old streamlines.
    pub alpha_decay_changed: f32,

    /// Streamline opacity (i.e., how much color is accumulated each step).
    pub particle_opacity: f32,

    /// Streamline core half-width (real pixels). Should be scaled by the real
    /// display density for consistency across devices.
    pub line_half_width: f32,

    /// Optional rendering effects.
    pub effects: Effects,

    /// How the particles move.
    pub flow: Flow,
}

/// How the particles move, on top of the wind field. The defaults behave like
/// the original.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Flow {
    /// Per-particle width, speed, opacity, and lifetime differences [0,1].
    pub variation: f32,
    /// How unevenly particles spawn (dense currents and empty patches) [0,1].
    pub clumping: f32,
    /// How much of the real wind field is used [0,1].
    pub real_wind: f32,
    /// How much evolving curl-noise turbulence is added [0,2].
    pub turbulence: f32,
    /// Turbulence (and warp and clumping) feature size multiplier.
    pub swirl_size: f32,
    /// How fast the turbulence, warp, and clumping change.
    pub evolution: f32,
    /// How much the real wind field is slowly warped [0,1].
    pub warp: f32,
    /// How slowly particles change direction [0,1] (0 = instantly).
    pub momentum: f32,
    /// Per-particle drift up or down [0,1].
    pub buoyancy: f32,
}

impl Flow {
    pub const DEFAULT: Flow = Flow {
        variation: 0.0,
        clumping: 0.0,
        real_wind: 1.0,
        turbulence: 0.0,
        swirl_size: 1.0,
        evolution: 0.3,
        warp: 0.0,
        momentum: 0.0,
        buoyancy: 0.0,
    };

    /// Flow noise time wraps at this (must match FLOW_PERIOD in the shaders).
    pub const PERIOD: f32 = 256.0;
}

impl Default for Flow {
    fn default() -> Self {
        Self::DEFAULT
    }
}

/// Optional rendering effects, each with a gradient of 2-4 color stops which
/// can cycle over time (the alpha is the effect strength) and params.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Effects {
    /// Bitmask of `1 << Effects::*`.
    pub enabled: u32,
    /// The gradient stops, indexed by `Effects::*` then the stop.
    pub colors: [[[f32; 4]; Effects::MAX_STOPS]; Effects::COUNT],
    /// The params, indexed by `Effects::*`, see each effect for the meaning.
    /// The last two of every effect are the number of gradient stops (2-4)
    /// and the gradient cycle speed in Hz (0 to not cycle, negative to
    /// reverse).
    pub params: [[f32; Effects::PARAM_COUNT]; Effects::COUNT],
}

impl Effects {
    // do not change the order (it must match the shaders, the Android app, and
    // saved prefs)
    /// Gusts take on the color, from the start color for gusts to the end
    /// color for the fastest wind. Params: (threshold, range, opacity, -).
    pub const SPEED: usize = 0;
    /// Some particles glow and flicker, each a random color in the gradient.
    /// Params: (amount, size, pulse rate in Hz, pulse sharpness).
    pub const FIREFLY: usize = 1;
    /// Some particles are drifting dots instead of streaks, each a random color
    /// in the gradient. Params: (size, size variation, fall speed, softness,
    /// amount).
    pub const SNOW: usize = 2;
    /// A bright head on each streak in the start color, with a halo in the end
    /// color. Params: (head size, white core, tail length, halo).
    pub const COMET: usize = 3;
    /// Wide, soft, slow-fading strokes, from the start color in calm areas to
    /// the end color in fast ones. Params: (width, linger, softness, density).
    pub const INK: usize = 4;
    /// A glow around the streaks, from the start color in calm areas to the
    /// end color in fast ones. Params: (radius, brightness, quality, pulse).
    pub const NEON: usize = 5;
    pub const COUNT: usize = 6;
    /// Params per effect (unused ones are zero).
    pub const PARAM_COUNT: usize = 8;
    /// The param with the number of gradient stops.
    pub const PARAM_STOPS: usize = 6;
    /// The param with the gradient cycle speed.
    pub const PARAM_CYCLE: usize = 7;
    /// Gradient stops per effect.
    pub const MAX_STOPS: usize = 4;

    pub const DEFAULT: Effects = Effects {
        enabled: 0,
        colors: [
            [
                [1.00, 0.55, 0.15, 0.85],
                [1.00, 0.15, 0.10, 0.95],
                [0.90, 0.10, 0.60, 0.95],
                [1.00, 0.90, 0.80, 1.00],
            ], // speed
            [
                [1.00, 0.88, 0.35, 1.00],
                [0.55, 1.00, 0.40, 1.00],
                [0.30, 0.90, 1.00, 1.00],
                [1.00, 0.50, 0.80, 1.00],
            ], // firefly
            [
                [1.00, 1.00, 1.00, 0.85],
                [0.75, 0.88, 1.00, 0.85],
                [0.85, 0.80, 1.00, 0.85],
                [0.70, 1.00, 1.00, 0.85],
            ], // snow
            [
                [0.85, 0.95, 1.00, 0.90],
                [0.40, 0.60, 1.00, 0.80],
                [0.70, 0.40, 1.00, 0.90],
                [1.00, 0.85, 0.40, 0.90],
            ], // comet
            [
                [0.55, 0.80, 1.00, 0.80],
                [1.00, 0.60, 0.85, 0.80],
                [1.00, 0.85, 0.40, 0.80],
                [0.50, 1.00, 0.75, 0.80],
            ], // ink
            [
                [0.00, 1.00, 0.85, 0.80],
                [1.00, 0.20, 0.80, 0.80],
                [1.00, 0.95, 0.20, 0.80],
                [0.20, 0.40, 1.00, 0.80],
            ], // neon
        ],
        params: [
            [0.15, 0.45, 0.75, 0.0, 0.0, 0.0, 2.0, 0.0], // speed
            [0.35, 8.0, 0.2, 3.0, 0.0, 0.0, 2.0, 0.0],   // firefly
            [2.5, 0.6, 0.0, 0.45, 0.3, 0.0, 2.0, 0.0],   // snow
            [3.0, 0.6, 1.0, 0.3, 0.0, 0.0, 2.0, 0.0],    // comet
            [2.5, 3.3, 0.6, 0.85, 0.0, 0.0, 2.0, 0.0],   // ink
            [1.0, 1.6, 1.0, 0.0, 0.0, 0.0, 2.0, 0.0],    // neon
        ],
    };

    pub fn is_enabled(&self, effect: usize) -> bool {
        effect < Self::COUNT && self.enabled & (1 << effect) != 0
    }

    /// How much slower the trails fade (ink linger and comet tail length).
    pub fn trail_linger(&self) -> f32 {
        let mut linger: Option<f32> = None;
        if self.is_enabled(Self::INK) {
            linger = Some(self.params[Self::INK][1]);
        }
        if self.is_enabled(Self::COMET) {
            let tail = self.params[Self::COMET][2];
            linger = Some(linger.map_or(tail, |l| l.max(tail)));
        }
        linger.unwrap_or(1.0).clamp(0.05, 100.0)
    }
}

impl Default for Effects {
    fn default() -> Self {
        Self::DEFAULT
    }
}

impl Config {
    /// A [`Config`] using a named [`Theme`]'s colors, and optionally additional
    /// [`ThemeParams`].
    pub fn with_theme(theme: &Theme) -> Self {
        let mut config = Self {
            slow_wind_color: theme.colors.slow_wind_color,
            fast_wind_color: theme.colors.fast_wind_color,
            bg_color1: theme.colors.bg_color1,
            bg_color2: theme.colors.bg_color2,
            ..Self::default()
        };
        if let Some(params) = theme.params {
            config.line_half_width = params.line_half_width;
            config.particle_opacity = params.particle_opacity;
            config.alpha_decay = params.alpha_decay;
            config.wind_speed = params.wind_speed;
        }
        config
    }

    /// The `(west, north, east, south)` degrees shown for a location on a
    /// screen with the provided aspect ratio (width/height).
    pub fn coordinate_bounds(&self, lng: f32, lat: f32, aspect: f32) -> [f32; 4] {
        // like the original wind field region code
        let wnd_lng = self.window_size * aspect;
        let wnd_lat = self.window_size;
        let bound_l = (lng - wnd_lng / 2.0).clamp(-180.0, 180.0);
        let bound_t = (lat + wnd_lat / 2.0).clamp(-90.0, 90.0);
        let bound_r = (bound_l + wnd_lng).clamp(-180.0, 180.0);
        let bound_b = (bound_t - wnd_lat).clamp(-90.0, 90.0);
        [bound_l, bound_t, bound_r, bound_b]
    }
}

#[derive(Clone, Copy, Debug)]
pub struct Theme {
    pub name: &'static str,
    pub colors: ThemeColors,
    pub params: Option<ThemeParams>, // param overrides, None uses the defaults
}

/// The four colors a theme is made of.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ThemeColors {
    pub slow_wind_color: [f32; 4], // rgba [0,1]
    pub fast_wind_color: [f32; 4], // rgba [0,1]
    pub bg_color1: [f32; 4],       // rgba [0,1]
    pub bg_color2: [f32; 4],       // rgba [0,1]
}

/// Additional [`Config`] params a theme can override.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ThemeParams {
    pub line_half_width: f32,
    pub particle_opacity: f32,
    pub alpha_decay: f32,
    pub wind_speed: f32,
}

impl Theme {
    pub const BLUE: Theme = Theme {
        name: "Blue",
        colors: ThemeColors {
            slow_wind_color: [0.498_039_22, 0.819_607_85, 0.584_313_75, 0.30],
            fast_wind_color: [0.980_392_16, 0.941_176_5, 0.823_529_4, 0.25],
            bg_color1: rgba8(0x044866FF),
            bg_color2: rgba8(0x0085AAFF),
        },
        params: None,
    };
    pub const GREEN: Theme = Theme {
        name: "Green",
        colors: ThemeColors {
            slow_wind_color: [0.50, 0.82, 0.18, 0.30],
            fast_wind_color: [0.98, 0.94, 0.12, 0.25],
            bg_color1: rgba8(0x044822FF),
            bg_color2: rgba8(0x008533FF),
        },
        params: None,
    };
    pub const BLUSH: Theme = Theme {
        name: "Blush",
        colors: ThemeColors {
            slow_wind_color: [0.850_980_4, 0.690_196_1, 0.917_647_06, 0.30],
            fast_wind_color: [0.862_745_1, 0.964_705_9, 1.0, 0.50],
            bg_color1: rgba8(0x4078C8FF),
            bg_color2: rgba8(0xD9B0EAFF),
        },
        params: None,
    };
    pub const MIDNIGHT: Theme = Theme {
        name: "Midnight",
        colors: ThemeColors {
            slow_wind_color: [0.215_686_28, 0.219_607_84, 0.215_686_28, 0.25],
            fast_wind_color: [0.729_411_8, 0.741_176_5, 0.737_254_9, 0.30],
            bg_color1: rgba8(0x000000AF),
            bg_color2: rgba8(0x464749FF),
        },
        params: None,
    };
    pub const DEEP_BLUE: Theme = Theme {
        name: "DeepBlue",
        colors: ThemeColors {
            slow_wind_color: [0.2824, 0.3176, 0.4902, 0.1840],
            fast_wind_color: [0.1725, 0.5843, 0.5843, 0.2750],
            bg_color1: [0.0510, 0.0275, 0.0275, 1.0000],
            bg_color2: [0.0039, 0.0039, 0.1255, 1.0000],
        },
        params: None,
    };
    pub const MAROON: Theme = Theme {
        name: "Maroon",
        colors: ThemeColors {
            slow_wind_color: [0.576, 0.192, 0.192, 0.25],
            fast_wind_color: [0.792, 0.376, 0.376, 0.30],
            bg_color1: rgba8(0x1A0909FF),
            bg_color2: rgba8(0x451717FF),
        },
        params: None,
    };
    pub const SEPIA: Theme = Theme {
        name: "Sepia",
        colors: ThemeColors {
            slow_wind_color: [0.26, 0.16, 0.05, 0.25],
            fast_wind_color: [0.44, 0.28, 0.11, 0.30],
            bg_color1: rgba8(0xBDA682FF),
            bg_color2: rgba8(0xC49F64FF),
        },
        params: None,
    };
    pub const SUNSET_WHIRLED: Theme = Theme {
        name: "SunsetWhirled",
        colors: ThemeColors {
            slow_wind_color: [0.976_470_6, 0.862_745_1, 0.647_058_84, 0.60],
            fast_wind_color: [1.0, 1.0, 1.0, 0.70],
            bg_color1: rgba8(0xE58186DF),
            bg_color2: rgba8(0xF7B38DDF),
        },
        params: None,
    };
    pub const TURQUOISE_WHIRLED: Theme = Theme {
        name: "TurquoiseWhirled",
        colors: ThemeColors {
            slow_wind_color: [0.498_039_22, 0.819_607_85, 0.584_313_75, 0.60],
            fast_wind_color: [1.0, 1.0, 1.0, 0.50],
            bg_color1: rgba8(0x0093B9DF),
            bg_color2: rgba8(0xEFDD81DF),
        },
        params: None,
    };
    pub const SKY_BLUE_WHIRLED: Theme = Theme {
        name: "SkyBlueWhirled",
        colors: ThemeColors {
            slow_wind_color: [1.0, 1.0, 1.0, 0.50],
            fast_wind_color: [0.956_862_75, 1.0, 0.529_411_8, 0.25],
            bg_color1: rgba8(0x75AAFAFF),
            bg_color2: rgba8(0xF4FF87FF),
        },
        params: None,
    };
    pub const SPARK_WHIRLED: Theme = Theme {
        name: "SparkWhirled",
        colors: ThemeColors {
            slow_wind_color: [0.25, 0.00, 0.50, 0.85],
            fast_wind_color: [1.00, 0.50, 0.00, 0.65],
            bg_color1: rgba8(0x270D03FF),
            bg_color2: rgba8(0x031A27FF),
        },
        params: None,
    };
    pub const MATRIX: Theme = Theme {
        name: "Matrix",
        colors: ThemeColors {
            slow_wind_color: [0.200, 0.384, 0.631, 0.137],
            fast_wind_color: [0.016, 1.000, 0.000, 1.000],
            bg_color1: rgba8(0x090E15FF),
            bg_color2: rgba8(0x031D10FF),
        },
        params: Some(ThemeParams {
            line_half_width: 1.55,
            particle_opacity: 0.66,
            alpha_decay: 0.95,
            wind_speed: 0.16,
        }),
    };

    /// Initial custom colors for user-defined theme.
    pub const CUSTOM: Theme = Theme {
        name: "Custom",
        colors: ThemeColors {
            slow_wind_color: [0.60, 0.75, 0.95, 0.30],
            fast_wind_color: [1.00, 1.00, 1.00, 0.35],
            bg_color1: rgba8(0x1B2735FF),
            bg_color2: rgba8(0x3A5A80FF),
        },
        params: None,
    };

    // The order of this array defines the theme index shared by the renderer
    // and the Android app.
    //
    // The trailing `// Category, Name` comment on each entry is the wallpaper
    // picker label, and is used for the wallpaper service names, the resources,
    // and the manifest.
    //
    // The original Pixel wallpaper contained the "Windy, Blue", "Windy, Blush",
    // "Windy, Midnight", "Your whirled, Sky blue", "Your whirled, Sunset", and
    // "Your whirled, Turquoise" themes, which I've left more or less unchanged.
    //
    // It seems that the "Windy" ones are intended to be simple colors, and the
    // "Your whirled" ones are a pun on "Your world" intended to be ones
    // inspired by scenery.
    //
    // I added a "Otherwhirled" category for themes with more abstract color
    // combinations and/or particle param tweaks.
    pub const ALL: &'static [Theme] = &[
        Theme::BLUE,              // Windy, Blue
        Theme::GREEN,             // Windy, Green
        Theme::BLUSH,             // Windy, Blush
        Theme::MIDNIGHT,          // Windy, Midnight
        Theme::DEEP_BLUE,         // Windy, Deep blue
        Theme::MAROON,            // Windy, Maroon
        Theme::SEPIA,             // Windy, Sepia
        Theme::SUNSET_WHIRLED,    // Your whirled, Sunset
        Theme::TURQUOISE_WHIRLED, // Your whirled, Turquoise
        Theme::SKY_BLUE_WHIRLED,  // Your whirled, Sky blue
        Theme::SPARK_WHIRLED,     // Your whirled, Spark
        Theme::MATRIX,            // Otherwhirled, Matrix
        // custom should be last, even when adding new themes
        Theme::CUSTOM, // Windy, Custom
    ];
}

impl Config {
    /// The wind speed at which `trail_follow_speed` leaves the fade unchanged.
    pub const TRAIL_REFERENCE_WIND_SPEED: f32 = 0.15;
}

impl Default for Config {
    fn default() -> Self {
        Self {
            window_size: 75.0,
            scale: [1.2, 1.15],
            particle_count: 2048,
            wind_speed: 0.1,
            particle_life: 8.0,
            slow_wind_color: [1.0, 0.0, 1.0, 1.0],
            fast_wind_color: [0.0, 1.0, 1.0, 1.0],
            bg_color1: [0.1, 0.0, 0.1, 0.1],
            bg_color2: [0.1, 0.0, 0.1, 0.1],
            alpha_decay: 0.9965,
            alpha_decay_changed: 0.91,
            trail_follow_speed: false,
            particle_opacity: 1.0,
            line_half_width: 1.0,
            effects: Effects::DEFAULT,
            flow: Flow::DEFAULT,
        }
    }
}

impl Default for ThemeParams {
    fn default() -> Self {
        let config = Config::default();
        Self {
            line_half_width: config.line_half_width,
            particle_opacity: config.particle_opacity,
            alpha_decay: config.alpha_decay,
            wind_speed: config.wind_speed,
        }
    }
}

/// Packed `0xRRGGBBAA` to `[r, g, b, a]` from `[0,1]`.
const fn rgba8(hex: u32) -> [f32; 4] {
    [
        ((hex >> 24) & 0xff) as f32 / 255.0,
        ((hex >> 16) & 0xff) as f32 / 255.0,
        ((hex >> 8) & 0xff) as f32 / 255.0,
        (hex & 0xff) as f32 / 255.0,
    ]
}
