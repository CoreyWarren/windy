// SPDX-License-Identifier: AGPL-3.0-or-later

// Builds the flow field grid once per frame: the (optionally warped) real
// wind plus turbulence, scaled by the wind speed, so each particle only has
// to read it. In smooth flow mode, the turbulence and warp keep their shape
// and the whole pattern glides instead, plus a shared float, so particles
// following it never cross paths.

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var wind_tex: texture_2d<f32>;
@group(0) @binding(2) var wind_samp: sampler;
@group(0) @binding(3) var<storage, read_write> field_raw: array<vec4<f32>>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= FIELD_W || gid.y >= FIELD_H) {
        return;
    }
    let pos = vec2<f32>(FIELD_MIN) + (vec2<f32>(gid.xy) + 0.5) / vec2<f32>(f32(FIELD_W), f32(FIELD_H)) * FIELD_SPAN;
    let smooth_flow = g.flow_c.w > 0.5;
    let t = select(g.flow_time, 0.0, smooth_flow); // the shape doesn't change in smooth flow
    let shift = select(vec2<f32>(0.0), g.flow_d.xy, smooth_flow); // it glides instead

    let wpos = warped(pos, t, shift);
    var wind = wind_at(wpos) * g.flow_a.z; // real wind
    if (g.flow_a.w > 0.0) {
        wind = wind + curl_at(pos, t, shift) * g.flow_a.w; // turbulence
    }
    var v = wind * g.wind_speed * 0.2;
    if (smooth_flow) {
        v = v - g.interact.zw * g.flow_c.x * 0.06; // everything floats up (against the tilt) together
    }
    field_raw[gid.y * FIELD_W + gid.x] = vec4<f32>(v, wind_sample(wpos).b, 0.0);
}

// position in noise space (square cells, scaled by the swirl size)
fn noise_q(pos: vec2<f32>) -> vec2<f32> {
    return vec2<f32>(pos.x / g.resolution.x, pos.y) * (3.0 / max(g.flow_b.x, 0.05));
}

// warp: read the real wind map at a slowly wandering offset
fn warped(pos: vec2<f32>, time: f32, shift: vec2<f32>) -> vec2<f32> {
    if (g.flow_b.z <= 0.0) {
        return pos;
    }
    let q = noise_q(pos) * 0.8 + shift;
    let t = time * 0.5;
    let off = vec2<f32>(
        vnoise3(vec3<f32>(q + vec2<f32>(5.2, 1.3), t), FLOW_PERIOD * 0.5),
        vnoise3(vec3<f32>(q + vec2<f32>(1.7, 9.2), t), FLOW_PERIOD * 0.5),
    ) - 0.5;
    return pos + off * g.flow_b.z * 0.25;
}

// turbulence: curl of smooth noise, so it swirls without sinks
fn flow_potential(q: vec2<f32>, time: f32) -> f32 {
    return vnoise3(vec3<f32>(q, time), FLOW_PERIOD)
        + 0.5 * vnoise3(vec3<f32>(q * 2.03 + vec2<f32>(17.0, 3.0), time * 2.0), FLOW_PERIOD * 2.0);
}

fn curl_at(pos: vec2<f32>, time: f32, shift: vec2<f32>) -> vec2<f32> {
    let q = noise_q(pos) + shift;
    let e = 0.03;
    let dx = (flow_potential(q + vec2<f32>(e, 0.0), time) - flow_potential(q - vec2<f32>(e, 0.0), time)) / (2.0 * e);
    let dy = (flow_potential(q + vec2<f32>(0.0, e), time) - flow_potential(q - vec2<f32>(0.0, e), time)) / (2.0 * e);
    return vec2<f32>(dy, -dx) * 0.35 * g.resolution;
}

fn wind_sample(pos: vec2<f32>) -> vec4<f32> {
    var uv = g.vector_field_bounds.xy + pos * g.vector_field_bounds.zw * g.size;
    uv = equirect_to_mercator(uv);
    return textureSampleLevel(wind_tex, wind_samp, uv, 0.0);
}

fn wind_at(pos: vec2<f32>) -> vec2<f32> {
    let wind = wind_sample(pos);
    return (wind.xy - 0.5) * vec2<f32>(1.0, -1.0) * g.resolution;
}
