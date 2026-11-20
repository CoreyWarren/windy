// SPDX-License-Identifier: AGPL-3.0-or-later

// Smooths the flow field grid (momentum in smooth flow mode: wider, gentler
// curves without particles cutting across each other), or copies it.

@group(0) @binding(0) var<uniform> g: Globals;
@group(0) @binding(1) var<storage, read> field_raw: array<vec4<f32>>;
@group(0) @binding(2) var<storage, read_write> field: array<vec4<f32>>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
    if (gid.x >= FIELD_W || gid.y >= FIELD_H) {
        return;
    }
    let r = select(0, i32(round(g.flow_b.w * 3.0)), g.flow_c.w > 0.5); // momentum
    var sum = vec4<f32>(0.0);
    var n = 0.0;
    for (var dy = -r; dy <= r; dy = dy + 1) {
        for (var dx = -r; dx <= r; dx = dx + 1) {
            let x = clamp(i32(gid.x) + dx, 0, i32(FIELD_W) - 1);
            let y = clamp(i32(gid.y) + dy, 0, i32(FIELD_H) - 1);
            sum = sum + field_raw[u32(y) * FIELD_W + u32(x)];
            n = n + 1.0;
        }
    }
    field[gid.y * FIELD_W + gid.x] = sum / n;
}
