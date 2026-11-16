// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var trail_tex: texture_2d<f32>;
@group(0) @binding(2) var trail_samp: sampler;
@group(0) @binding(3) var wind_tex: texture_2d<f32>;
@group(0) @binding(4) var wind_samp: sampler;

struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vidx: u32) -> VsOut {
    let xy = vec2<f32>(f32((vidx << 1u) & 2u), f32(vidx & 2u));
    var out: VsOut;
    out.clip = vec4<f32>(xy * 2.0 - 1.0, 0.0, 1.0);
    out.uv = vec2<f32>(xy.x, 1.0 - xy.y);
    return out;
}

@fragment
fn fs_main(in: VsOut) -> @location(0) vec4<f32> {
    // screen uv to normalized field position + overscan centering and panning
    let parallax = vec2<f32>(g.offset_x * (g.size.x - 1.0) * 0.5, 0.0);
    let t = (in.uv - 0.5) / g.size + 0.5 + parallax;

    // background tint from wind speed (field blue channel).
    var buv = g.vector_field_bounds.xy + t * g.vector_field_bounds.zw * g.size;
    buv = equirect_to_mercator(buv);
    let speed = textureSample(wind_tex, wind_samp, buv).b;
    let bg = mix(g.bg_color1, g.bg_color2, speed);

    // streamlines
    let trail = textureSample(trail_tex, trail_samp, t);
    var particle_color = mix(g.color_slow, g.color_fast, trail.r);

    // ink effect: the strokes take on the ink color (calm to fast gradient)
    if (has_fx(g.effects, FX_INK)) {
        let ink = mix(g.fx_ink_a, g.fx_ink_b, speed);
        particle_color = vec4<f32>(
            mix(particle_color.rgb, ink.rgb, ink.a),
            mix(particle_color.a, g.fx_ink_p.w, ink.a), // density
        );
    }

    // speed color effect: gusts take on the speed color (gust to max gradient)
    if (has_fx(g.effects, FX_SPEED)) {
        let w = clamp(trail.b / max(trail.g, 1e-4), 0.0, 1.0);
        let tint = mix(g.fx_speed_a, g.fx_speed_b, w);
        let amount = smoothstep(0.0, 0.25, w) * tint.a;
        particle_color = vec4<f32>(
            mix(particle_color.rgb, tint.rgb, amount),
            mix(particle_color.a, max(particle_color.a, g.fx_speed_p.z), amount), // opacity
        );
    }

    // discard the alpha to match how the original one is rendered
    var rgb = mix(bg, particle_color, particle_color.a * trail.g).rgb;

    // neon effect: a soft glow around the streamlines (calm to fast gradient)
    if (has_fx(g.effects, FX_NEON)) {
        let neon = mix(g.fx_neon_a, g.fx_neon_b, speed);
        // pulse: whole cycles per 64s so the wrapped time doesn't jump
        let pulse = 1.0 - g.fx_neon_p.w * 0.5 * (1.0 + sin(g.time_acc * (2.0 * PI / 8.0)));
        rgb = rgb + neon.rgb * neon_glow(t) * neon.a * g.fx_neon_p.y * pulse; // brightness
    }

    var out = vec4<f32>(clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0)), 1.0);

    // fix colors on srgb targets
    if (g.srgb_output != 0u) {
        out = vec4<f32>(srgb_to_linear(out.rgb), out.a);
    }
    return out;
}

// blurred trail coverage around t (rings of taps, 8 on low quality, 16 on high)
fn neon_glow(t: vec2<f32>) -> f32 {
    let texel = 1.0 / g.trail_size;
    let scale = g.fx_neon_p.x; // radius
    let r1 = max(g.line_half_width * 3.0 * scale, 1.5);
    let r2 = max(g.line_half_width * 7.0 * scale, 3.0);
    let high = g.fx_neon_p.z >= 0.5; // quality
    var sum = 0.0;
    for (var i = 0; i < 8; i = i + 1) {
        let a = f32(i) * (PI / 4.0);
        let d = vec2<f32>(cos(a), sin(a));
        if (high) {
            sum = sum + textureSampleLevel(trail_tex, trail_samp, t + d * r1 * texel, 0.0).g * 0.6;
            let d2 = vec2<f32>(cos(a + PI / 8.0), sin(a + PI / 8.0));
            sum = sum + textureSampleLevel(trail_tex, trail_samp, t + d2 * r2 * texel, 0.0).g * 0.4;
        } else {
            sum = sum + textureSampleLevel(trail_tex, trail_samp, t + d * (r1 + r2) * 0.5 * texel, 0.0).g;
        }
    }
    return sum / 8.0;
}
