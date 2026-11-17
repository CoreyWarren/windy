// SPDX-FileCopyrightText: 2023-2026 Patrick Gaskin
// SPDX-License-Identifier: AGPL-3.0-or-later

mod color;
mod config;
mod render;
mod source;

#[allow(clippy::all, dead_code)]
mod shaders {
    pub mod simulate {
        include!(concat!(env!("OUT_DIR"), "/simulate.rs"));
    }
    pub mod trail {
        include!(concat!(env!("OUT_DIR"), "/trail.rs"));
    }
    pub mod fade {
        include!(concat!(env!("OUT_DIR"), "/fade.rs"));
    }
    pub mod composite {
        include!(concat!(env!("OUT_DIR"), "/composite.rs"));
    }
    pub mod sprites {
        include!(concat!(env!("OUT_DIR"), "/sprites.rs"));
    }
}

pub use color::{Style, StyleColor, generate};
pub use config::{Config, Effects, Flow, Theme, ThemeColors, ThemeParams};
pub use render::Renderer;
pub use source::ThemeSource;
