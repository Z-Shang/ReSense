# Original dock icons

`tools/extract_dock_icons.py` resolves material IDs from
`Rosie.apk!/assets/Port/scenes/Rosie_navbar.m10` into the default Fusion shared
texture container. FastLZ and dual-ETC1 decoding recover RGBA PNGs:

| Original material | Native resource | Size |
| --- | --- | --- |
| home_icon_all_apps.png | sense_dock_apps | 38 × 38 |
| home_icon_settings.png | sense_dock_settings | 36 × 36 |
| home_icon_add_page.png | sense_dock_add_page | 36 × 36 |
| home_icon_trash_rest.png | sense_dock_trash | 36 × 36 |

The phone comes directly from `Rosie.apk!/res/drawable-hdpi/icon_p_phone_rest.png`.
It is not claimed to be a Mode10 texture. Legacy arrow/personalization drawables
also exist in Rosie; the modern dock uses the grid and gear referenced by its
Mode10 navbar scene instead.

Build-generated resources remain local alongside the dock artwork. The build
writes `dock-icons-provenance.json` with scene hash, source paths, material IDs,
texture record offsets and dimensions. No original artwork is checked into source.

Apps and Settings display only centered original glyphs, with accessibility
labels retained. Phone keeps its glyph beside its label in a centered row. DockButton draws all foreground content without
mesh deformation, so the dock curve bends only the button surfaces.
