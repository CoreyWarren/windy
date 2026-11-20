// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var<storage, read_write> particles: array<Particle>;
@group(0) @binding(2) var<storage, read> field: array<vec4<f32>>;

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    let i = gid.x;
    if (i >= g.particle_count) {
        return;
    }
    var p = particles[i];
    let variation = g.flow_a.x;
    let smooth_flow = g.flow_c.w > 0.5;

    // where it wants to go (screen fractions per second), from the field
    var goal = field_at(p.pos).xy * trait_speed(p.seed, variation);

    // smooth flow follows the field exactly (so paths never cross), free
    // flow adds per-particle float/sink and momentum
    var vel = goal;
    if (!smooth_flow) {
        goal.y = goal.y + (trait_rand(p.seed, 7.0) - 0.5) * 2.0 * g.flow_c.x * 0.06; // float/sink
        vel = goal;
        let tau = g.flow_b.w * 1.5;
        if (tau > 0.001) {
            vel = mix(p.vel, goal, 1.0 - exp(-g.time_delta / tau)); // ease instead of snapping
        }
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
    p.speed = field_at(new_pos).z; // for the speed color effect
    particles[i] = p;
}

// the flow field grid, bilinearly interpolated
fn field_at(pos: vec2<f32>) -> vec4<f32> {
    let size = vec2<f32>(f32(FIELD_W), f32(FIELD_H));
    let c = clamp((pos - FIELD_MIN) / FIELD_SPAN * size - 0.5, vec2<f32>(0.0), size - 1.0);
    let c0 = vec2<u32>(floor(c));
    let c1 = min(c0 + vec2<u32>(1u), vec2<u32>(FIELD_W - 1u, FIELD_H - 1u));
    let f = c - floor(c);
    let a = mix(field[c0.y * FIELD_W + c0.x], field[c0.y * FIELD_W + c1.x], f.x);
    let b = mix(field[c1.y * FIELD_W + c0.x], field[c1.y * FIELD_W + c1.x], f.x);
    return mix(a, b, f.y);
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

fn noise_q(pos: vec2<f32>) -> vec2<f32> {
    return vec2<f32>(pos.x / g.resolution.x, pos.y) * (3.0 / max(g.flow_b.x, 0.05));
}
