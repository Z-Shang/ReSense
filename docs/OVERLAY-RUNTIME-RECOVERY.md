# Original HTC overlay runtime: recovered behavior

This is static reverse engineering of the supplied 3.33.401.153 ROM. Addresses
are ELF virtual addresses for its ARM Thumb libraries. It does not constitute
a capture from a running HTC phone. Launcher rendering remains unchanged.

Reproduce with `python3 tools/recover_overlay_runtime.py`. The tool reads the
ROM directly, verifies property names, camera literals and virtual method
bindings, resolves imported ELF relocations, and saves bounded disassemblies
and `recovery.json` under `.toolchain/htc-overlay-recovery/`.
The checked-in evidence is
`references/3.33.401.153/overlay-runtime-recovery.json`, including library and
scene SHA-256 hashes and all 392 scene nodes with serialized camera/pivot flags.

## Surface and rendering path

The original wallpaper service's `surfaceChanged` forwards the actual surface,
format, width and height to `FxViewObject.setSurface` (DEX address 0x1398).
`FxView::SetSurface` stores width and height at offsets 0x40 and 0x44.
The service loads `Port/weather_overlay.m10` using `createScene(path, true)`.
`FxView::CreateScene` at 0xc18a0 forwards that boolean to `FxScene::Load`.
Follow-up recovery resolves it to `FxNodeControl::SetVisibility(bool)` through
scene virtual slot 0x88; it is initial visibility, not fit-to-screen.

The render route is:

```
FxView::Render (0xc1804)
  FxScene::Render (0xb9dac)
    TimelineGL2::Render (0x4bfbc)
      Timeline::Render (0x76018)
        TransformNode::Render (0x79748)
          TimelineGL2::InternalRender (0x4c5d8)
            Timeline::PolicyRender (0x77744)
              GraphicsDevice::Render(child)
```

The normal path uses virtual slot 0x78; slot 0x7c is selection. Timeline's
slot 0xe4 resolves to `PolicyRender`. Both imported entries can be zero in the
unrelocated ELF file: reading vtables without ELF relocations produces wrong
conclusions. The recovery tool checks these `R_ARM_ABS32` bindings.

## TwoDimension controls camera selection, not an orthographic switch

`TransformNode::RegisterProperties` (0x791ec) registers `TwoDimension` at
object offset 0x1c0, with its boolean at 0x1c4. It registers `Pivot` at 0x130,
with its boolean at 0x134. PC-relative string loads verify those names.

`TimelineGL2::PreRenderChildren` reads TwoDimension at 0x4b23e and 0x4b24e,
and calls graphics virtual slots 0xa8 and 0xb0. The verified bindings are:

| Slot | Function | Address |
| --- | --- | --- |
| 0xa8 | PushCameraDepth(bool) | 0x28684 |
| 0xac | PopCameraDepth(bool) | 0x28694 |
| 0xb0 | SetCameraDepth(bool) | 0x286a4 |

Recovered state transitions (device fields: depth 0x114, flag 0x118,
camera stack count 0xf8):

```
PushCameraDepth(twoDimension):
    if !twoDimension: depth += 1
PopCameraDepth(twoDimension):
    if !twoDimension && depth != 0: depth -= 1
SetCameraDepth(twoDimension):
    if depth == 0 && twoDimension:
        flag = true
    else if cameraStackCount == 0:
        flag = true
    else:
        flag = false
```

When the flag is true, `GetProjectionMatrix` takes the camera pointer from
device offset 0x11c (0x2a336). When false, a nonempty explicit camera stack
supplies the camera; with no stack this function returns its fallback matrix.
`GetViewMatrix` also branches on this flag. Consequently a 3D ancestor matters,
and camera-stack state cannot be replaced by a boolean on each leaf.

The default camera is constructed in `GraphicsDeviceGL2::Initialize` and then
configured by `Camera::SetupMillimeter` at call site 0x31254. Its inputs include
the actual surface dimensions, focal length 50, and film gate 36 multiplied by
height/width (literals 0x314dc and 0x314d8). This is a perspective camera setup;
the separate `PushCamera2D` orthographic path must not be inferred from the
name `TwoDimension`.

`SetViewport` likewise reconfigures this camera. The recovered distance
magnitude is `50 * viewportWidth / 36`: 750 for width 540, 1500 for 1080, and
2000 for 1440. This is not the launcher's current fixed 1000 scene-unit camera.
These distances alone do not specify a complete replacement projection:
view adjustment, transforms, coordinate signs and viewport mapping also matter.

## Relevant serialized overlay settings

| Node | File offset | Pivot | TwoDimension |
| --- | --- | --- | --- |
| __weather_overlay | 215936 | false | true |
| 03_Rain | 278116 | true | true |
| b_rain_wiper.png | 673568 | true | true |
| b_raindrop_streaks.png | 675900 | true | true |

`TransformNode::CalculateTransform` tests Pivot at 0x78742 before composing
pivot scale, rotation and translation, followed by the ordinary transform.
The current overlay exporter omits both boolean flags; the modern renderer
retains PivotPosition but uses its own simplified transform/projection policy.
This establishes a compatibility gap, not proof that a particular camera
change will fill the entire screen.

## Implementation implications and remaining work

The framework needs explicit camera-depth push/pop state, default versus
scene camera selection, preserved Pivot/TwoDimension properties and verified
matrix conventions. They should be implemented as reusable Fusion behavior
before changing rain-specific sizing. Widget centering flags belong to another
path and have not been demonstrated to apply to the wallpaper overlay.

Follow-up recovery connects the default camera, viewport and transform order
for the direct, unfrozen overlay path; see `WEATHER-SCREEN-MAPPING.md` and
`tools/recover_weather_mapping.py`. Its Z=0 plane maps scene units to pixels.
No automatic screen-filling policy or full-screen rain result has been verified
on the original runtime.
The existing custom lower-edge fade is independently non-stock; see
`WEATHER-FRAMING-TYPOGRAPHY-AUDIT.md` for its measured contribution.
