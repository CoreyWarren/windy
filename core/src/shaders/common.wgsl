// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

const PI: f32 = 3.1415926535897932;

struct Globals {
    vector_field_bounds: vec4<f32>, // region of wind texture (u, v_top, du, dv)
    bg_color1: vec4<f32>,
    bg_color2: vec4<f32>,
    color_slow: vec4<f32>,
    color_fast: vec4<f32>,
    // effects: _a/_b are the gradient colors (alpha is the strength), _p the params
    fx_speed_a: vec4<f32>,
    fx_speed_b: vec4<f32>,
    fx_speed_p: vec4<f32>,
    fx_firefly_a: vec4<f32>,
    fx_firefly_b: vec4<f32>,
    fx_firefly_p: vec4<f32>,
    fx_snow_a: vec4<f32>,
    fx_snow_b: vec4<f32>,
    fx_snow_p: vec4<f32>,
    fx_comet_a: vec4<f32>,
    fx_comet_b: vec4<f32>,
    fx_comet_p: vec4<f32>,
    fx_ink_a: vec4<f32>,
    fx_ink_b: vec4<f32>,
    fx_ink_p: vec4<f32>,
    fx_neon_a: vec4<f32>,
    fx_neon_b: vec4<f32>,
    fx_neon_p: vec4<f32>,
    size: vec2<f32>,        // android home screen parallax scale (x, y)
    resolution: vec2<f32>,  // aspect-ratio correction for simulation (h/w, 1)
    trail_size: vec2<f32>,  // trail texture dimensions (real px)
    screen_size: vec2<f32>, // output target dimensions (real px)
    time_delta: f32,        // seconds since last frame
    time_acc: f32,          // wrapped time accumulator (used as a seed)
    wind_speed: f32,
    particle_life: f32,
    particle_opacity: f32,
    line_half_width: f32,   // streamline half-width (real px)
    offset_x: f32,          // android home screen parallax offset [-1, 1]
    fade_decay: f32,        // per-frame trail fade multiplier
    particle_count: u32,
    srgb_output: u32,
    effects: u32,           // FX_* bitmask
    _pad1: u32,
};

struct Particle {
    pos: vec2<f32>,   // current position, normalized to [0,1]
    prev: vec2<f32>,  // previous position (for drawing line)
    uv: vec2<f32>,    // stable per-particle seed coordinate
    age: f32,         // remaining life, ~[0,1]
    speed: f32,       // normalized wind speed at pos [0,1]
};

// effect bits (must match Effects in config.rs)
const FX_SPEED: u32 = 1u;
const FX_FIREFLY: u32 = 2u;
const FX_SNOW: u32 = 4u;
const FX_COMET: u32 = 8u;
const FX_INK: u32 = 16u;
const FX_NEON: u32 = 32u;

fn has_fx(effects: u32, bit: u32) -> bool {
    return (effects & bit) != 0u;
}

// same as original glsl rand
fn rand(co: vec2<f32>) -> f32 {
    let dt = dot(co, vec2<f32>(12.9898, 78.233));
    let sn = dt % 3.14;
    return fract(sin(sn) * 43758.5453);
}

// latitude equirectangular to mercator
fn equirect_to_mercator(uv: vec2<f32>) -> vec2<f32> {
    let latitude = (uv.y * 2.0 - 1.0) * PI * 0.5;
    var y = atan(sinh(latitude)) / (PI * 0.5);
    y = y * 0.5 + 0.5;
    return vec2<f32>(uv.x, y);
}

fn srgb_to_linear(c: vec3<f32>) -> vec3<f32> {
    let low = c / 12.92;
    let high = pow((c + 0.055) / 1.055, vec3<f32>(2.4));
    return select(high, low, c <= vec3<f32>(0.04045));
}
