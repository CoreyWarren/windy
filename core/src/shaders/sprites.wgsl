// SPDX-License-Identifier: AGPL-3.0-or-later

// Per-frame particle sprites drawn over the composited frame for the snow,
// comet, and firefly effects. Unlike the trails, these don't persist.

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var<storage, read> particles: array<Particle>;

struct VsOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) local: vec2<f32>,      // px relative to the particle
    @location(1) radii: vec4<f32>,      // (snow, comet head, firefly, comet halo) px, 0 if unused
    @location(2) weight: vec3<f32>,     // (snow, comet, firefly) opacity
    @location(3) snow_color: vec4<f32>, // this flake's gradient color
    @location(4) fly_color: vec4<f32>,  // this firefly's gradient color
};

@vertex
fn vs_main(@builtin(vertex_index) vidx: u32, @builtin(instance_index) iidx: u32) -> VsOut {
    let p = particles[iidx];
    let base = max(g.line_half_width, 1.0);

    // fade in on spawn, out on death (same as the trails)
    let life = smoothstep(0.0, 0.05, 1.0 - p.age) * smoothstep(0.0, 0.25, p.age);

    var radii = vec4<f32>(0.0);
    var weight = vec3<f32>(0.0);
    var snow_color = vec4<f32>(0.0);
    var fly_color = vec4<f32>(0.0);

    if (has_fx(g.effects, FX_SNOW) && is_flake(p.uv, g.fx_snow_q.x)) {
        // p: (size, size variation, fall, softness), q: (amount, -, -, -)
        let r = rand(p.uv * 3.1 + vec2<f32>(0.17));
        let variation = 1.0 + g.fx_snow_p.y * (r - 0.5) * 1.6;
        radii.x = base * g.fx_snow_p.x * max(variation, 0.1);
        snow_color = mix(g.fx_snow_a, g.fx_snow_b, rand(p.uv * 4.7 + vec2<f32>(0.53)));
        weight.x = life * snow_color.a;
    }
    if (has_fx(g.effects, FX_COMET)) {
        // p: (head size, white core, tail length, halo)
        radii.y = base * g.fx_comet_p.x;
        radii.w = select(0.0, radii.y * mix(1.5, 5.0, g.fx_comet_p.w), g.fx_comet_p.w > 0.0);
        weight.y = life * g.fx_comet_a.a;
    }
    if (has_fx(g.effects, FX_FIREFLY) && rand(p.uv * 7.7 + vec2<f32>(0.31)) < g.fx_firefly_p.x) {
        // p: (amount, size, pulse rate in Hz, pulse sharpness)
        // whole cycles per 64s so the wrapped time doesn't cause a jump
        let cycles = max(round(g.fx_firefly_p.z * 64.0 * (0.6 + rand(p.uv * 5.3))), 1.0);
        let freq = (2.0 * PI / 64.0) * cycles;
        let phase = rand(p.uv * 9.1) * 2.0 * PI;
        let flicker = pow(max(sin(g.time_acc * freq + phase), 0.0), max(g.fx_firefly_p.w, 0.1));
        radii.z = base * g.fx_firefly_p.y;
        fly_color = mix(g.fx_firefly_a, g.fx_firefly_b, rand(p.uv * 6.3 + vec2<f32>(0.71)));
        weight.z = life * flicker * fly_color.a;
    }

    let radius = max(max(radii.x, radii.y), max(radii.z, radii.w)) + 1.0;

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
    out.snow_color = snow_color;
    out.fly_color = fly_color;
    return out;
}

@fragment
fn fs_main(in: VsOut) -> @location(0) vec4<f32> {
    let d = length(in.local);
    var rgb = vec3<f32>(0.0); // premultiplied
    var a = 0.0;

    // snow: round flakes, hard to soft edges
    if (in.radii.x > 0.0) {
        let edge = mix(0.95, 0.05, g.fx_snow_p.w);
        let k = (1.0 - smoothstep(in.radii.x * edge, in.radii.x, d)) * in.weight.x;
        rgb = rgb + in.snow_color.rgb * k;
        a = a + k;
    }

    // comet: head in the first color with a white core, halo in the second
    if (in.radii.y > 0.0) {
        let n = d / in.radii.y;
        let hot = exp(-30.0 * n * n);
        let k = (exp(-4.0 * n * n) + 0.6 * hot) * in.weight.y;
        let head = mix(g.fx_comet_a.rgb, vec3<f32>(1.0), hot * g.fx_comet_p.y);
        rgb = rgb + head * k;
        a = a + k;
        if (in.radii.w > 0.0) {
            let h = d / in.radii.w;
            let kh = exp(-5.0 * h * h) * 0.35 * g.fx_comet_b.a * in.weight.y;
            rgb = rgb + g.fx_comet_b.rgb * kh;
            a = a + kh * 0.5;
        }
    }

    // firefly: wide flickering glow
    if (in.radii.z > 0.0) {
        let n = d / in.radii.z;
        let k = (exp(-6.0 * n * n) + 0.8 * exp(-60.0 * n * n)) * in.weight.z;
        rgb = rgb + in.fly_color.rgb * k;
        a = a + k * 0.5; // mostly additive, so it glows
    }

    a = clamp(a, 0.0, 1.0);
    rgb = clamp(rgb, vec3<f32>(0.0), vec3<f32>(1.0));
    if (g.srgb_output != 0u) {
        rgb = srgb_to_linear(rgb);
    }
    return vec4<f32>(rgb, a);
}
