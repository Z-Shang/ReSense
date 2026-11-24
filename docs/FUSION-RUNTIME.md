# Sense Fusion runtime

The bottom layer lives in `sense-framework`, package
`dev.sense.framework.fusion`. It is new Java code, not a wrapper around the
ROM's ARM libraries. Weather data remains owned by `sense-services`; the
rendering core has no weather or network dependency.

## Implemented foundation

- `Mode10`: bounded v2 record reader with owned, read-only payloads, strict
  nesting/record/size limits, property lookup, hashed names and UTF-16 strings.
  Unknown tags and property values remain accessible for further decoding.
  Input streams are caller-owned; parsing should run on a worker thread.
- `FxScene`: immutable structural node hierarchy, type hashes, named lookup,
  raw properties and marker spans. Named lookup returns all matches because
  names need not be unique. A structural parent is not automatically a
  Fusion layer-parent relationship.
- `FxTrack`: static float vectors and the observed dense/keyed sample layouts,
  fractional-frame sampling and explicit step/linear adaptation. Original
  interpolation codes remain available; they are not silently interpreted as
  verified HTC easing. `sampleQuaternion` performs shortest-arc normalized slerp; component-wise
  `sample` remains explicitly step/linear.
- `FxTransform`: normalized quaternion TRS composition, matrix multiplication
  and homogeneous points. Column-major matrices, x/y/z/w quaternions.
- `FxCamera`: explicit orthographic/perspective projection and point clipping.
  Camera/view/viewport choices are supplied by the host; recovered constructor
  defaults are not assumed to be the effective stock camera. A future renderer
  must clip triangles crossing the frustum, not discard whole polygons based
  on the point helper.
- `FxPlayback`: timestamp-based play/pause/seek/loop state, deterministic at
  different display refresh rates. Absolute elapsed time avoids accumulated
  per-tick rounding. Rebasing excludes time hidden or detached. Sample endpoints
  are explicit: 0 to 29 spans 29/FPS seconds. This does not settle HTC's separate
  inclusive-end scheduling convention. Zero-length markers are stationary poses.
- `FxSceneView`: main-thread Android host using `Choreographer`, with no fixed
  30/60 Hz timer. Stops requesting frames while hidden/detached or playback is
  stopped. Scene parsing is separate from drawing; a supplied renderer draws
  into the canvas with the current fractional frame and actual viewport size.

## Texture and rendering layers

- `FxTexture` decodes the observed FastLZ levels 1/2, single-level RGBA8888
  (format 6) and dual-ETC1 (format 14) payloads into immutable straight-alpha
  RGBA pixels. It checks padded dimensions, sizes, stream bounds and ETC1
  differential ranges. Unknown formats/mip layouts fail explicitly.
- `FxResources` indexes texture records, keeps IDs scoped to a document, resolves
  embedded textures before shared resources, and bounds its decoded cache by
  bytes. Unrelated font/type descriptors can contain name-only property
  declarations; they are not interpreted as texture tables. Missing textures
  raise a different error from unsupported texture encodings.
- `FxSceneAsset.load` is the worker-thread preparation boundary. It validates
  a scene and resolves all mesh textures before publication, with a limit on
  retained decoded texture bytes. Callers own input streams and async task
  cancellation. It does not create an executor or perform network requests.
- `FxSceneInstance` separates mutable poses from immutable documents. It
  evaluates observed weather transforms, opacity, visibility, active spans,
  structural parent clocks and sibling layer-parent chains. It rejects missing,
  ambiguous or cyclic layer links. `setTimelineFrame` explicitly seeks an
  independent nested clock. Evaluation reuses per-node vectors/matrices.
- `FxGlRenderer` renders textured quads as triangles with GLES2. Hardware clips
  against the frustum and interpolates UVs in perspective. RGBA is premultiplied
  in the shader for correct source-over alpha. GPU resources use a bounded LRU
  cache and explicit context/thread ownership. Layer ordering or opaque depth
  testing plus transparent back-to-front sorting can be selected. Decoded CPU
  pixels are supplied before rendering; no texture decompression occurs in draw. Solid-color
  materials use `DiffuseColor`; the observed zero-ID dynamic-image slots stay
  empty until supplied by a binding. Missing nonzero IDs remain errors.

Camera matrices, blend interpretation and depth mode are explicit host policies.
The stock code 2 is retained, not declared to be a verified blend equation.
Device tests deliberately adapt it to source-over. The existing weather pivot
adaptation is translation * rotation * scale * pivot displacement. Non-default
pivot rotation/scale fail explicitly until their stock semantics are verified.
Nested clock inheritance follows the observed weather model; it does not claim
full stock playback-speed, marker-end or interpolation-code compatibility.
Transparent depth sorting uses quad centers, not order-independent transparency
for intersecting polygons. Meshes beyond the observed four-vertex layout and
additional material channels are not implemented.

## Controls and widget host

`FxControls` supplies accessible native `TextLabel`, `DynamicImage`, `Button`,
`Hitbox` and `SceneContainer` classes. Buttons inherit the shared Sense styling;
text/images retain Android font, image and accessibility behavior. Hitboxes
support native clicks and flings. Content and actions are bound explicitly;
these classes do not yet deserialize every original Fusion control property.

`FxWidgetHost` combines a transparent GLES surface below native controls with
main-thread display-clock playback and completion callbacks. `bind` attaches a
native control to a node-local rectangle and projects it with the same camera
and evaluated pose. GPU and UI evaluation use separate instances. Native
control bindings use a homography; they hide when a corner leaves the frustum
rather than partially clipping across the near plane. The GPU scene itself
uses real triangle clipping.

The host suspends rendering and playback advancement while hidden/detached.
`Widget.onStart/onStop` provide an explicit observer/data subscription lifecycle.
A widget can use `WeatherShareClient` there without making the renderer depend
on weather. GL objects are recreated on context loss; immutable decoded pixels
remain reusable. Scene setup, native controls and host methods belong on the
main thread; resource parsing/decompression belongs on a worker. Renderers own
and release their GL resources on the GL context thread.

This is a modern framework subset, not binary compatibility with HTC's
`com.htc.fusion.fx` or an implementation of Rosie's proprietary broadcast,
shared-UID and widget-provider protocol. The launcher now binds the original 2×1, 2×2 and 4×4 Weather layouts to shared
weather data; see [WEATHER-WIDGETS-RESEARCH.md](WEATHER-WIDGETS-RESEARCH.md).
Original control metadata and unverified material/timing semantics still need
validation as further controls are connected.

## Validation

Run `python3 tools/test_fusion_core.py`. It compiles the platform-independent
runtime with the local JDK and compares the Java hierarchy counts to the Python
research decoder for all 83 recovered weather-widget scenes: 3,719 nodes and
701 markers. It also exercises malformed/truncated input, immutable payloads,
track boundaries, retained interpolation codes, transforms/camera clipping,
pause/rebase/loop behavior and equivalent elapsed playback at 60/90/120/144 Hz.
These are deterministic clock checks, not measured device frame-rate results.

Run `source tools/android-env.sh` then
`./gradlew :sense-framework:assembleDebug :sense-framework:lintDebug --offline`
for the Android frame host. The isolated emulator suite below checks the GPU renderer and native host
lifecycle. Shared APK builds automatically collect
the framework's new Java sources through the existing packager.

Run `source tools/android-env.sh`, `./gradlew assembleSharedDebug --offline`,
then `python3 tools/test_fusion_emulator.py` for isolated device checks. The
script installs the rebuilt framework plus a temporary test app, renders into
an EGL pbuffer, and exercises native host lifecycle/playback. It removes the
test app afterward without modifying launcher preferences or weather stores.
The framework APK remains updated. These tests use explicit adapted camera and
blend policies; a nonblank render is not proof of pixel equivalence to HTC.

Verified device results: all 80 large/small condition scenes render nonblank
across sampled frames using the explicit adapted camera and source-over policy.
The three widget layout scenes also prepare successfully; their dynamic
controls/scene containers still require widget-specific data binding. Synthetic
pixel readback verifies straight-to-premultiplied alpha, source-over composition,
solid colors, empty image slots, layer ordering, opaque depth, behind-camera
rejection and a quad crossing the near plane. Recreating the EGL context repeats
the shader/texture checks. The native host test verifies completion callbacks,
button clicks, hide/show subscription intervals and asynchronous teardown.
Host-side differential checks match six original texture pixel hashes (three
RGBA and three dual-ETC1) against the independent Python decoder.

## Canvas composition backend

`FxCanvasRenderer` shares decoded textures and evaluated scene instances with
the GLES implementation. It projects observed quads using Android Canvas so
launcher pages, drag shadows and Leap snapshots include weather artwork. It
also exposes image-control and explicit nine-slice bindings. Bitmap preparation
runs on the worker, while each draw runs on the UI thread. Near-plane-crossing
meshes and general 3D depth/material behavior still require GLES; this backend
supports the front-facing weather composition with source-over alpha.
`setChildFrame` separates a timeline's child clock from its own pose clock;
`setVisible` supports per-instance binding overrides without changing documents.
