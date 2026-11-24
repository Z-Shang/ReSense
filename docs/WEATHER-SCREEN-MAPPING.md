# HTC weather overlay: recovered screen mapping

The direct, unfrozen overlay path uses a centered perspective camera whose
Z=0 plane maps one scene unit to one surface pixel. It does not automatically
fit the root timeline's 540×960 Dimensions to an arbitrary screen. This is a
static reconstruction from ROM 3.33.401.153, not an observed original-device
render. Application rendering has not been changed.

Reproduce the native evidence, mathematical model and checks:

```sh
python3 tools/recover_overlay_runtime.py
python3 tools/recover_weather_mapping.py
python3 tools/test_weather_mapping.py
```

The first command saves bounded Thumb disassemblies and verified bindings under
`.toolchain/htc-overlay-recovery/`. The second reads the original meshes and
tracks directly from the ROM and writes
`references/3.33.401.153/weather-screen-mapping.json`. The third checks surface
placement, depth, clipping, pivot order and the recovered ROM geometry.

## Surface size reaches the viewport directly

`FxHost::SetupView` at 0xabd10 calls `FxView::GetSurface` at 0xabd34, retrieving
the actual width and height. At 0xabf4a–0xabf5a it invokes graphics virtual slot
0x24 with `(0, 0, width, height)`. This resolves to
`GraphicsDeviceGL2::SetViewport` at 0x2a518, which calls `glViewport` and
reconfigures the default camera at 0x2a666. There is no root-Dimensions fit in
this surface-to-viewport operation.

The earlier unknown `createScene(path, true)` boolean is now resolved:
`FxScene::LoadInternal` forwards it at 0xba0ea–0xba0f4 to scene virtual slot
0x88. The scene vtable at 0x153710 binds that slot to
`FxNodeControl::SetVisibility(bool)` at 0xade68. It controls initial visibility.

## Camera, projection and viewport equations

Let W and H be the viewport size, and let (x,y,z) be a point **after** its node,
layer-parent and ancestor transforms. The default camera parameters are:

```
D = 50 * W / 36
camera position = (W/2, H/2, D)
vertical FOV = -2 * atan(H / (2*D))
near = 0.1
far = 10000
```

The negative vertical FOV supplies the Y-down convention. SetupMillimeter
sets it at 0x44cee–0x44d0a, sets viewport `(0,0,W,-H)` at 0x44d0c–0x44d38,
and assigns the centered camera position at 0x44d6a–0x44d9e.
`CameraGL2::InternalUpdate` computes the inverse camera transform;
`CameraGL2::CalculateProjection` builds the perspective matrix with negative
Y scale and clip W equal to negative camera-space Z.

In conventional column-vector notation the centered view matrix is:

```
V = [ 1  0  0  -W/2 ]
    [ 0  1  0  -H/2 ]
    [ 0  0  1  -D   ]
    [ 0  0  0   1   ]

P = [ 2D/W     0          0              0         ]
    [   0    -2D/H        0              0         ]
    [   0      0    -(far+near)/(far-near) -2*far*near/(far-near) ]
    [   0      0         -1              0         ]
```

For an unclipped point in front of the camera, perspective divide followed by
the viewport gives top-left surface coordinates (viewportX/Y below denote the
viewport's origin converted to that top-left coordinate system):

```
k = D / (D - z)
screenX = viewportX + W/2 + (x - W/2) * k
screenY = viewportY + H/2 + (y - H/2) * k
```

At Z=0, k=1, so screen coordinates equal transformed scene coordinates plus
the viewport origin. Positive Z moves toward the camera and increases scale.
Points beyond the near/far planes are clipped, rather than assigned a clamped
perspective denominator as our current overlay renderer does.

`GraphicsDeviceGL2::ProjectCoordinate` at 0x2a37c independently confirms the
sequence: get view and projection, compose with model, transform the vector,
divide XYZ by W, then apply the viewport matrix. Its CPU viewport helper uses
integer half extents (`SetViewport` at 0x2a540–0x2a546); GPU rasterization uses
the GL viewport. All stock/comparison sizes in this report are even, so that
odd-size CPU rounding distinction does not affect the reported coordinates.

## Matrix order and view adjustment

The native NEON routines settle the multiplication convention:

* `PlatformMath_Matrix4Vector4Multiply` at 0xb44f0 combines four contiguous
  matrix columns weighted by the vector's four components.
* `PlatformMath_Matrix4Multiply(a,b,out)` at 0xb44a0 returns **b × a** when
  interpreted in that column-vector convention. Porting its call sites as
  ordinary a × b would reverse composition order.
* `TransformNode::CalculateTransform` starts with pivot scale, rotation and
  translation when Pivot is enabled, then applies ordinary scale, rotation
  and translation. Its column-vector equivalent is
  `T * R * S * pivotT * pivotR * pivotS`.
* The quaternion helper at 0x77e48 uses `2 / squaredNorm` for a non-unit
  quaternion, equivalent to normalizing it before forming the rotation matrix.

The inspected wiper/streak ancestry has default pivot rotation and scale, so
its local transform reduces to `T * R * S * pivotT`. The reference model
rejects unexpected pivot, timing, layer-parent or frozen-path settings instead
of silently applying this restricted reconstruction to other scenes.

`GraphicsDeviceGL2` initializes view adjustment disabled at 0x2c81c. The calls
found in this GLES library enable it at 0x4cf3c, set its matrix at 0x4cf48 and
disable it at 0x4cf5c, within `TimelineGL2::RenderToFramebuffer`. This is an
offscreen rendering facility, not evidence of an always-active surface-fit
transform. The selected ancestry serializes Frozen=false on its timelines.
The root also has identity Position/Rotation/Scale and Pivot=false.

## What this means for rain coverage

The source wiper and streak ancestry is entirely TwoDimension and its vertices
remain at world Z=0. Their depth-free placement therefore follows directly
from the recovered default camera mapping. Changing only focal distance cannot
increase their coverage.

| Geometry on the stock 540×960 surface | Reconstructed extent |
| --- | --- |
| Streak mesh X | −30.53 to 570.53 px |
| Streak mesh Y | −33.44 to 753.44 px |
| Maximum wiper mesh Y, frames 45–104 | 684.19 px, frame 73 |

These are mesh bounds, not a claim that every enclosed pixel is opaque or that
the whole rectangle is wiped. They exclude our added lower-edge fade. Integer
animation samples are used; subframe event/interpolation parity remains a
separate runtime question.

The native mapping alone would keep these authored pixel extents on a larger
raw surface. Our current launcher instead applies a centered uniform contain
fit to the 540×960 source frame. At 1080×2400, that adapter places the streak
bottom at 1746.89px and the maximum wiper bottom at 1608.39px. Those values are
modern adaptation results, not a stock policy for a screen HTC never targeted.

The recovered original mapping is now sufficient to reproduce the inspected
direct path at its authored size. Filling a modern tall screen still requires
an explicit adaptation of the source frame or effect geometry. That choice
should preserve the recovered source-space behavior and be implemented as a
separate mapping policy, rather than attributing a stretch-to-fill rule to HTC.

This does not recover arbitrary scene cameras, frozen/offscreen compositions,
all effect shaders or live runtime reconfiguration. The selected direct path
has static native and asset evidence; original hardware validation would still
be useful for confirming pixel and timing parity.
