// SPDX-License-Identifier: AGPL-3.0-or-later

// Per-frame particle sprites drawn over the composited frame for the snow,
// comet, and firefly effects. Unlike the trails, these don't persist.

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var<storage, read> particles: array<Particle>;

struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) local: vec2<f32>,  // px relative to the particle
    @location(1) radii: vec3<f32>,  // (snow, comet, firefly) px, 0 if unused
    @location(2) weight: vec3<f32>, // (snow, comet, firefly) opacity
};

@vertex
fn vs_main(@builtin(vertex_index) vidx: u32, @builtin(instance_index) iidx: u32) -> VsOut {
    let p = particles[iidx];
    let base = max(g.line_half_width, 1.0);

    // fade in on spawn, out on death (same as the trails)
    let life = smoothstep(0.0, 0.05, 1.0 - p.age) * smoothstep(0.0, 0.25, p.age);

    var radii = vec3<f32>(0.0);
    var weight = vec3<f32>(0.0);

    if (has_fx(g.effects, FX_SNOW)) {
        radii.x = base * (1.4 + 2.2 * rand(p.uv * 3.1 + vec2<f32>(0.17))); // mixed flake sizes
        weight.x = life * g.fx_snow.a;
    }
    if (has_fx(g.effects, FX_COMET)) {
        radii.y = base * 3.0;
        weight.y = life * g.fx_comet.a;
    }
    if (has_fx(g.effects, FX_FIREFLY) && rand(p.uv * 7.7 + vec2<f32>(0.31)) < 0.35) {
        // integer cycles per 64s so the wrapped time doesn't cause a jump
        let freq = (2.0 * PI / 64.0) * (6.0 + floor(rand(p.uv * 5.3) * 14.0));
        let phase = rand(p.uv * 9.1) * 2.0 * PI;
        let flicker = pow(max(sin(g.time_acc * freq + phase), 0.0), 3.0);
        radii.z = base * 8.0;
        weight.z = life * flicker * g.fx_firefly.a;
    }

    let radius = max(max(radii.x, radii.y), radii.z) + 1.0;

    // trail position to screen position (inverse of the composite mapping)
    let parallax = vec2<f32>(g.offset_x * (g.size.x - 1.0) * 0.5, 0.0);
    let uv = (p.pos - 0.5 - parallax) * g.size + 0.5;
    let center = uv * g.screen_size;

    var corners = array<vec2<f32>, 6>(
        vec2<f32>(-1.0, -1.0), vec2<f32>(1.0, -1.0), vec2<f32>(1.0, 1.0),
        vec2<f32>(-1.0, -1.0), vec2<f32>(1.0, 1.0), vec2<f32>(-1.0, 1.0),
    );
    var offset = corners[vidx] * radius;
    if (max(max(weight.x, weight.y), weight.z) <= 0.0) {
        offset = vec2<f32>(0.0); // degenerate, nothing to draw
    }
    let px = center + offset;

    var out: VsOut;
    out.clip = vec4<f32>(px.x / g.screen_size.x * 2.0 - 1.0, 1.0 - px.y / g.screen_size.y * 2.0, 0.0, 1.0);
    out.local = offset;
    out.radii = radii;
    out.weight = weight;
    return out;
}

@fragment
fn fs_main(in: VsOut) -> @location(0) vec4<f32> {
    let d = length(in.local);
    var rgb = vec3<f32>(0.0); // premultiplied
    var a = 0.0;

    // snow: soft round flakes
    if (in.radii.x > 0.0) {
        let k = (1.0 - smoothstep(in.radii.x * 0.55, in.radii.x, d)) * in.weight.x;
        rgb = rgb + g.fx_snow.rgb * k;
        a = a + k;
    }

    // comet: bright head with a hot core
    if (in.radii.y > 0.0) {
        let n = d / in.radii.y;
        let k = (exp(-4.0 * n * n) + 0.6 * exp(-30.0 * n * n)) * in.weight.y;
        let head = mix(g.fx_comet.rgb, vec3<f32>(1.0), exp(-30.0 * n * n) * 0.6);
        rgb = rgb + head * k;
        a = a + k;
    }

    // firefly: wide flickering glow
    if (in.radii.z > 0.0) {
        let n = d / in.radii.z;
        let k = (exp(-6.0 * n * n) + 0.8 * exp(-60.0 * n * n)) * in.weight.z;
        rgb = rgb + g.fx_firefly.rgb * k;
        a = a + k * 0.5; // mostly additive, so it glows
    }

    a = clamp(a, 0.0, 1.0);
    rgb = clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0));
    if (g.srgb_output != 0u) {
        rgb = srgb_to_linear(rgb);
    }
    return vec4<f32>(rgb, a);
}
