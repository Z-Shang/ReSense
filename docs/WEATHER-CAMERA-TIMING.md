# Weather camera and timing recovery

Run `python3 tools/inspect_weather_playback.py` to reproduce the report in
`.toolchain/camera-timing/recovery.json` from the local stock ROM. It records
scene and library hashes, byte offsets for timing values, marker tables,
and native camera property/literal addresses.

## Confirmed scene timing

`HtcClock3DWidget.apk!/assets/Port/s_03.m10` has three timelines:
`Forecast_Weather_state`, `s_03`, and `Sun`. Each specifies `FramesPerSecond=30`
and `PlaybackSpeed=1`. The marker table uses a count followed by records of
start frame, frame count, UTF-16 character count, and UTF-16 name:

| Marker | Start | Frame count | Last frame | Sample span at 30 fps |
| --- | --- | --- | --- | --- |
| In | 0 | 31 | 30 | 1 second |
| Rest | 30 | 11 | 40 | 1/3 second |
| Out | 40 | 21 | 60 | 2/3 second |

The native widget now reads its entrance endpoints and duration from generated
scene metadata. These durations describe elapsed time between sample positions;
Fusion's inclusive marker boundary/event scheduling could add a frame interval.
The modern clock digit flip remains at the user's requested one second.

Corroboration from the original APK's DEX: `WeatherView` loads its scene into
`FxSceneContainer` and invokes `playMarker("In")` (DEX instruction addresses
0x2149a–0x2149e and the fallback 0x214ba–0x214be). The Fusion framework wrapper
`FxTimelineControl.playMarker(String)` supplies playback speed 1.0 before entering
its native overload. Read-only DEX inspection changes the local copy's header
version 036 to 035 solely for the SDK dexdump tool; it is not an installable patch.

## Confirmed constructor defaults, effective camera unconfirmed

`system/lib/libmode10.so` exports `fusion::mode10::Camera::Camera()` at virtual
address 0x44ef8. Thumb disassembly and `RegisterProperties()` at 0x42bc8 associate
constructor values with the following properties:

| Property | Constructor default | Literal address |
| --- | --- | --- |
| Ortho | false | instruction at 0x44f08 |
| HorizontalFOV | 0° | 0x44ff4 |
| VerticalFOV | 45° | 0x44ff8 |
| NearClip | 0.1 | 0x44ffc |
| FarClip | 10000 | 0x45000 |
| Viewport | (0, 0, 800, 480) | 0x45004, 0x45008 |

The script verifies PC-relative property-name strings before reading float
literals; these addresses apply to the supplied ROM, not arbitrary HTC builds.
Neither the weather scene nor its clock parent contains an explicit Camera node.
`Timeline::GetRenderCamera()` traverses ancestors and can use fallback state.
`libmode10GLES20.so` shows runtime reconfiguration: `SetViewport` invokes
`Camera::SetupMillimeter` at 0x2a666, `TimelineGL2::InternalRender` invokes
`Camera::SetupDistance` at 0x4c946 (other branches do so too), and
`GraphicsDeviceGL2::PushCamera2D` constructs an orthographic camera.
Therefore constructor defaults are not proof of the active weather projection.

The modern renderer retains its documented adapted projection. Determining the
active camera requires tracing the widget's parent hierarchy/render branches,
including effective viewport/distance and camera selection. Applying 45° directly
would claim fidelity that this evidence does not support.

## Runtime reconfiguration trace

`python3 tools/trace_weather_camera.py` extracts the three relevant libraries,
verifies vtable pointers, and disassembles bounded ARM Thumb function ranges.
The report and disassembly are in `.toolchain/camera-runtime-trace/`. This is
static tracing of the runtime implementation, not an observed original-ROM run.

### Default camera selected by the graphics device

`GraphicsDeviceGL2::GetActiveCamera()` (0x28664) selects the top camera-stack
entry if the stack count at device+0xf8 is nonzero; otherwise it returns the
default camera at device+0x11c. The graphics device's vtable slot +0x50 resolves
to Thumb pointer 0x28665, verified from the ELF table at 0x5d198. Thus the
indirect calls at `TimelineGL2::InternalRender` 0x4c602–0x4c60a and
`GetCentralizedViewport` 0x4a2b4–0x4a2ca are identified, not guessed by offset.

`GraphicsDeviceGL2::SetViewport` (0x2a518) reconfigures device+0x11c through
`Camera::SetupMillimeter` at 0x2a666. Its literal pool supplies 50.0 at 0x2a694
and 36.0 at 0x2a690. The argument setup at 0x2a624–0x2a662 passes references to
50, 36×height/width, width, and height, followed by true. These are the focal/
film-gate inputs to this path; they replace a simple assumption that the
constructor's 45-degree field is the final runtime projection. They do not
by themselves establish the weather widget's viewport dimensions.

### Conditional per-timeline camera fitting

`TimelineGL2::InternalRender` starts at 0x4c5d8 and saves the active camera's
position, vertical FOV, viewport, and the GL viewport before rendering.
Its byte at timeline+0x380 contains two low centering flags. Either flag
branches at 0x4c68a–0x4c696 into the viewport-fitting path. The flags start
cleared in the TimelineGL2 constructor (0x4a250–0x4a266).

A setter at 0x4a170 inserts its boolean arguments into bits 0 and 1. The
TimelineGL2 vtable slot +0xd4 resolves to Thumb pointer 0x4a171, verified at
ELF virtual address 0x5dc3c. `FxCellSceneContainer::PostLoad` in libmode10fx
calls that slot at 0x10216c–0x102174 with r1=r2=1, enabling both axes for its
underlying timeline. This identifies a concrete caller that selects the
camera-fitting branch. It does not prove that every scene container is a cell
container or that the weather subtree is rendered directly by that control.

`GetCentralizedViewport` (0x4a2ac) reads the active camera viewport and adjusts
its origin/extent according to those flags and the transformed timeline center.
`InternalRender` takes the timeline's integer Dimensions at +0x2dc/+0x2e0,
uses half-width/half-height as a center, transforms it through the graphics
device matrix, then calls the centralized viewport routine at 0x4c8fe. It
changes the device viewport and calls `Camera::SetupDistance` at 0x4c946.
The latter arguments reference the saved active camera's Z position
(`sp+0x88`, saved at 0x4c62c–0x4c630) and the fitted viewport extents,
rather than fixed camera constructor literals. The transformed center supplies
the 2D centering calculation, not the camera distance. Additional
render branches call SetupDistance at 0x4cbfe and 0x4d2e8.

`Camera::SetupDistance` at libmode10.so 0x44b1c explicitly disables Ortho,
sets a viewport with a negated height, sets near/far to 0.1 and 10000, derives
vertical FOV using atan and a radians-to-degrees conversion, and updates the
camera transform. `InternalRender` later restores the saved camera properties
and GL viewport (the restoration sequence begins at 0x4c7b4). The fitting is
therefore scoped around rendering, not simply a one-time global setup.

### Another distinct path: explicit 2D camera

`GraphicsDeviceGL2::PushCamera2D` at 0x28fec allocates a CameraGL2, explicitly
sets Ortho=true, constructs its viewport from the supplied dimensions, and
sets near/far to 10 and 1000 before pushing it. These differ from constructor
and SetupDistance defaults. A parent timeline's 2D/3D rendering choices and
the camera stack must be traced along with the child scene.

### What remains for a live trace

The original libraries are 32-bit ARM HTC/Fusion components; our current
Android 15 x86_64 emulator runs the native replacement, not those libraries.
There is no original-runtime camera state available in its logcat. A compatible
original environment (physical Pyramid hardware or a working compatible ARM
runtime with the HTC framework) is needed to log actual pointers, viewport,
active camera Z, projected center, flags and camera matrices at these call sites. Virtual
addresses above are module-relative; live breakpoints must account for the
module load address and ARM/Thumb state.

The modern launcher's projection remains unchanged. The recovered call path
is evidence for the next implementation step, not a claim that its effective
weather camera has been dynamically observed.


The equivalent data flow for the verified fitting branch is:

```text
camera = graphics.GetActiveCamera()
savedPosition = camera.Position
savedVerticalFov = camera.VerticalFOV
savedViewport = camera.Viewport
projectedCenter = graphics.ProjectCoordinate(timelineMatrix, (width/2, height/2, 0, 1))
(origin, fittedWidth, fittedHeight) = GetCentralizedViewport(projectedCenter, centeringFlags)
graphics.SetViewport(origin, fittedWidth, fittedHeight)
camera.SetupDistance(savedPosition.z, fittedWidth, fittedHeight)
render timeline content
restore saved camera properties and viewport
```

`ProjectCoordinate` is verified through graphics vtable slot +0x2c, pointing
to Thumb address 0x2a37d; `SetViewport` is slot +0x24, pointing to 0x2a519.
Keeping saved camera Z separate from projected scene center is necessary to
reproduce the original fitting math correctly.
