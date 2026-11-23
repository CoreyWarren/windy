// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

const PI: f32 = 3.1415926535897932;

struct Globals {
    vector_field_bounds: vec4<f32>, // region of wind texture (u, v_top, du, dv)
    bg_color1: vec4<f32>,
    bg_color2: vec4<f32>,
    color_slow: vec4<f32>,
    color_fast: vec4<f32>,
    // effects: _a.._d are the gradient stops (alpha is the strength), _p/_q the
    // params (q.z is the number of stops, q.w the cycle speed)
    fx_speed_a: vec4<f32>,
    fx_speed_b: vec4<f32>,
    fx_speed_c: vec4<f32>,
    fx_speed_d: vec4<f32>,
    fx_speed_p: vec4<f32>,
    fx_speed_q: vec4<f32>,
    fx_firefly_a: vec4<f32>,
    fx_firefly_b: vec4<f32>,
    fx_firefly_c: vec4<f32>,
    fx_firefly_d: vec4<f32>,
    fx_firefly_p: vec4<f32>,
    fx_firefly_q: vec4<f32>,
    fx_snow_a: vec4<f32>,
    fx_snow_b: vec4<f32>,
    fx_snow_c: vec4<f32>,
    fx_snow_d: vec4<f32>,
    fx_snow_p: vec4<f32>,
    fx_snow_q: vec4<f32>,
    fx_comet_a: vec4<f32>,
    fx_comet_b: vec4<f32>,
    fx_comet_c: vec4<f32>,
    fx_comet_d: vec4<f32>,
    fx_comet_p: vec4<f32>,
    fx_comet_q: vec4<f32>,
    fx_ink_a: vec4<f32>,
    fx_ink_b: vec4<f32>,
    fx_ink_c: vec4<f32>,
    fx_ink_d: vec4<f32>,
    fx_ink_p: vec4<f32>,
    fx_ink_q: vec4<f32>,
    fx_neon_a: vec4<f32>,
    fx_neon_b: vec4<f32>,
    fx_neon_c: vec4<f32>,
    fx_neon_d: vec4<f32>,
    fx_neon_p: vec4<f32>,
    fx_neon_q: vec4<f32>,
    flow_a: vec4<f32>,      // (variation, clumping, real wind, turbulence)
    flow_b: vec4<f32>,      // (swirl size, flow change speed, warp, momentum)
    flow_c: vec4<f32>,      // (float/sink, size variation, size style, smooth flow: 1 = paths don't cross)
    flow_d: vec4<f32>,      // (glide offset x, y, overlap brightening off: 1, fade in in age units)
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
    flow_time: f32,         // flow noise time, wraps at FLOW_PERIOD
};

struct Particle {
    pos: vec2<f32>,   // current position, normalized to [0,1]
    prev: vec2<f32>,  // previous position (for drawing line)
    uv: vec2<f32>,    // stable per-particle seed coordinate
    age: f32,         // remaining life, ~[0,1]
    speed: f32,       // normalized wind speed at pos [0,1]
    vel: vec2<f32>,   // current velocity (for momentum)
    seed: f32,        // random per spawn, for the particle's traits
    life0: f32,       // age when spawned (for fading in)
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

fn fx_stop(c0: vec4<f32>, c1: vec4<f32>, c2: vec4<f32>, c3: vec4<f32>, i: f32) -> vec4<f32> {
    if (i < 0.5) {
        return c0;
    }
    if (i < 1.5) {
        return c1;
    }
    if (i < 2.5) {
        return c2;
    }
    return c3;
}

// effect color at t in [0,1] along the gradient stops c0..c3 (q.z of them).
// with a cycle speed (q.w, in Hz), the stops loop around and scroll over time
// (whole cycles per 64s so the wrapped time doesn't cause a jump).
fn fx_gradient(c0: vec4<f32>, c1: vec4<f32>, c2: vec4<f32>, c3: vec4<f32>, q: vec4<f32>, time: f32, t: f32) -> vec4<f32> {
    let n = clamp(round(q.z), 2.0, 4.0);
    let k = round(q.w * 64.0);
    var x = clamp(t, 0.0, 1.0) * (n - 1.0);
    var looped = false;
    if (k != 0.0) {
        x = fract(t + time * k / 64.0) * n;
        looped = true;
    }
    let i = min(floor(x), n - 1.0);
    var j = i + 1.0;
    if (j > n - 0.5) {
        j = select(n - 1.0, 0.0, looped);
    }
    return mix(fx_stop(c0, c1, c2, c3, i), fx_stop(c0, c1, c2, c3, j), clamp(x - i, 0.0, 1.0));
}

// per-particle traits (variation 0 = all the same)
fn trait_rand(seed: f32, k: f32) -> f32 {
    return rand(vec2<f32>(fract(seed * 13.7 + k * 0.618) * 31.0, k * 3.1 + 0.5));
}

// style 0: even (~0.18x to 5.7x), 1: rare giants (mostly ~0.5x, a few up to
// ~9x), 2: two sizes (80% ~0.5x, 20% ~4x)
fn trait_width(seed: f32, variation: f32, style: f32) -> f32 {
    let r = trait_rand(seed, 1.0);
    if (style > 1.5) {
        return select(exp2(variation * 2.0), exp2(-variation), r < 0.8);
    }
    if (style > 0.5) {
        return exp2(variation * (pow(r, 4.0) * 4.0 - 1.0));
    }
    return exp2(variation * (r - 0.5) * 5.0);
}

// fade in after spawning and out before dying (spans in age units, the life
// starts at life0 and counts down to 0)
fn life_fade(age: f32, life0: f32, fade_in: f32, fade_out: f32) -> f32 {
    return smoothstep(0.0, max(fade_in, 0.001), life0 - age) * smoothstep(0.0, max(fade_out, 0.001), age);
}

// the usual fade for streaks and sprites (fade_in in age units, from the
// fade in seconds)
fn streak_fade(age: f32, life0: f32, fade_in: f32) -> f32 {
    return life_fade(age, life0, fade_in, 0.25);
}

fn trait_opacity(seed: f32, variation: f32) -> f32 {
    return 1.0 - variation * 0.3 * trait_rand(seed, 2.0); // at most 30% dimmer
}

fn trait_speed(seed: f32, variation: f32) -> f32 {
    return 1.0 + variation * (trait_rand(seed, 3.0) - 0.5) * 1.4; // 0.3x to 1.7x
}

fn trait_life(seed: f32, variation: f32) -> f32 {
    return exp2(variation * (trait_rand(seed, 4.0) - 0.5) * 1.6); // how fast it ages
}

// the flow field grid (velocity in screen fractions per second, and the
// normalized wind speed), covering particle positions from -0.1 to 1.1
const FIELD_W: u32 = 64u;
const FIELD_H: u32 = 128u;
const FIELD_MIN: f32 = -0.1;
const FIELD_SPAN: f32 = 1.2;

// flow noise time wraps at this, and the noise is periodic in time to match
const FLOW_PERIOD: f32 = 256.0;

fn hash_u(v: u32) -> u32 {
    let s = v * 747796405u + 2891336453u;
    let w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}

fn hash3(c: vec3<i32>) -> f32 {
    let h = hash_u(bitcast<u32>(c.x) ^ hash_u(bitcast<u32>(c.y) ^ hash_u(bitcast<u32>(c.z))));
    return f32(h) / 4294967295.0;
}

// smooth value noise in [0,1], periodic in z
fn vnoise3(p: vec3<f32>, period: f32) -> f32 {
    let i = floor(p);
    let f = p - i;
    let u = f * f * (3.0 - 2.0 * f);
    let x0 = i32(i.x);
    let y0 = i32(i.y);
    let z0 = i32(i.z - period * floor(i.z / period));
    let z1 = i32((i.z + 1.0) - period * floor((i.z + 1.0) / period));
    let a = mix(hash3(vec3<i32>(x0, y0, z0)), hash3(vec3<i32>(x0 + 1, y0, z0)), u.x);
    let b = mix(hash3(vec3<i32>(x0, y0 + 1, z0)), hash3(vec3<i32>(x0 + 1, y0 + 1, z0)), u.x);
    let c = mix(hash3(vec3<i32>(x0, y0, z1)), hash3(vec3<i32>(x0 + 1, y0, z1)), u.x);
    let d = mix(hash3(vec3<i32>(x0, y0 + 1, z1)), hash3(vec3<i32>(x0 + 1, y0 + 1, z1)), u.x);
    return mix(mix(a, b, u.y), mix(c, d, u.y), u.z);
}

// sprites (flakes, comet heads, fireflies) only come from the first this many
// particles, so their count (and cost) doesn't grow with the particle count
const SPRITE_CAP: u32 = 512u;

// sprites are never bigger than this (px radius), since huge ones are very
// expensive to draw
const MAX_SPRITE_RADIUS: f32 = 72.0;

// streaks are never wider than this (px half-width), for the same reason
const MAX_HALF_WIDTH: f32 = 24.0;

// snow effect: whether a particle is a flake instead of a streak
fn is_flake(uv: vec2<f32>, amount: f32, index: u32) -> bool {
    return index < SPRITE_CAP && rand(uv * 2.9 + vec2<f32>(0.43)) < amount;
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
