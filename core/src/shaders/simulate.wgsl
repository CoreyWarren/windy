// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var<storage, read_write> particles: array<Particle>;
@group(0) @binding(2) var wind_tex: texture_2d<f32>;
@group(0) @binding(3) var wind_samp: sampler;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let i = gid.x;
    if (i >= g.particle_count) {
        return;
    }
    var p = particles[i];
    let variation = g.flow_a.x;

    // the wind here: the (optionally warped) real wind, plus turbulence
    let wpos = warped(p.pos);
    var wind = wind_at(wpos) * g.flow_a.z; // real wind
    if (g.flow_a.w > 0.0) {
        wind = wind + curl_at(p.pos) * g.flow_a.w; // turbulence
    }

    // where it wants to go (screen fractions per second)
    var goal = wind * g.wind_speed * 0.2 * trait_speed(p.seed, variation);
    goal.y = goal.y + (trait_rand(p.seed, 7.0) - 0.5) * 2.0 * g.flow_c.x * 0.06; // float/sink

    // momentum: ease towards it instead of snapping (fps-independent)
    let tau = g.flow_b.w * 1.5;
    var vel = goal;
    if (tau > 0.001) {
        vel = mix(p.vel, goal, 1.0 - exp(-g.time_delta / tau));
    }

    // particle motion (i.e., advection), real time so this is independent of fps
    var new_pos = p.pos + vel * g.time_delta;

    // snow effect: flakes fall (fraction of the screen per second)
    if (has_fx(g.effects, FX_SNOW) && is_flake(p.uv, g.fx_snow_q.x)) {
        new_pos.y = new_pos.y + g.fx_snow_p.z * 0.25 * g.time_delta;
    }

    var life = p.age - (g.time_delta / g.particle_life) * 0.3 * trait_life(p.seed, variation);

    // respawn dead particle if needed
    if (life <= 0.0) {
        let seed = p.uv + vec2<f32>(g.time_acc);
        new_pos = spawn_pos(seed);
        life = 0.2 + rand(vec2<f32>(p.uv.x * p.uv.y + g.time_acc)) * 0.8;
        p.seed = rand(seed * 3.7 + vec2<f32>(0.13, 0.71));
        p.life0 = life;
        p.prev = new_pos; // don't draw a line from the old position
        vel = goal;
    } else {
        p.prev = p.pos;
    }

    // update particle
    p.pos = new_pos;
    p.vel = vel;
    p.age = life;
    p.speed = speed_at(warped(new_pos)); // for the speed color effect
    particles[i] = p;
}

// clumping: some spots are likelier than others (the spots drift slowly)
fn spawn_pos(seed: vec2<f32>) -> vec2<f32> {
    var pos = vec2<f32>(rand(seed), rand(seed * 2.0)) * 1.2 - 0.1;
    if (g.flow_a.y > 0.0) {
        for (var k = 0; k < 4; k = k + 1) {
            let fk = f32(k);
            let n = vnoise3(vec3<f32>(noise_q(pos) * 0.7, g.flow_time * 0.25), FLOW_PERIOD * 0.25);
            let density = smoothstep(0.35, 0.65, n);
            if (rand(seed + vec2<f32>(fk * 7.1 + 0.3, 3.3)) < mix(1.0, density, g.flow_a.y)) {
                break;
            }
            pos = vec2<f32>(rand(seed + vec2<f32>(fk * 1.3 + 0.7, 0.2)), rand(seed + vec2<f32>(0.4, fk * 2.9 + 0.1))) * 1.2 - 0.1;
        }
    }
    return pos;
}

// position in noise space (square cells, scaled by the swirl size)
fn noise_q(pos: vec2<f32>) -> vec2<f32> {
    return vec2<f32>(pos.x / g.resolution.x, pos.y) * (3.0 / max(g.flow_b.x, 0.05));
}

// warp: read the real wind map at a slowly wandering offset
fn warped(pos: vec2<f32>) -> vec2<f32> {
    if (g.flow_b.z <= 0.0) {
        return pos;
    }
    let q = noise_q(pos) * 0.8;
    let t = g.flow_time * 0.5;
    let off = vec2<f32>(
        vnoise3(vec3<f32>(q + vec2<f32>(5.2, 1.3), t), FLOW_PERIOD * 0.5),
        vnoise3(vec3<f32>(q + vec2<f32>(1.7, 9.2), t), FLOW_PERIOD * 0.5),
    ) - 0.5;
    return pos + off * g.flow_b.z * 0.25;
}

// turbulence: curl of smooth noise, so it swirls without sinks
fn flow_potential(q: vec2<f32>) -> f32 {
    return vnoise3(vec3<f32>(q, g.flow_time), FLOW_PERIOD)
        + 0.5 * vnoise3(vec3<f32>(q * 2.03 + vec2<f32>(17.0, 3.0), g.flow_time * 2.0), FLOW_PERIOD * 2.0);
}

fn curl_at(pos: vec2<f32>) -> vec2<f32> {
    let q = noise_q(pos);
    let e = 0.03;
    let dx = (flow_potential(q + vec2<f32>(e, 0.0)) - flow_potential(q - vec2<f32>(e, 0.0))) / (2.0 * e);
    let dy = (flow_potential(q + vec2<f32>(0.0, e)) - flow_potential(q - vec2<f32>(0.0, e))) / (2.0 * e);
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

// normalized speed (field blue channel, same as the background tint)
fn speed_at(pos: vec2<f32>) -> f32 {
    return wind_sample(pos).b;
}
