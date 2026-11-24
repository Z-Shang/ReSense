# Widget movement and resizing

`SenseWidgetHost` supplies a `SenseWidgetHostView` for each hosted Android widget.
Its dispatch handler queues the launcher long-press timer before forwarding DOWN
to RemoteViews children. A successful hold invokes the workspace's drag callback
and sends CANCEL to the widget content, preventing a release click. Ordinary taps
still pass through. Movement beyond touch slop, multi-touch, cancellation and view
detachment cancel the pending launcher hold. Child interception-disallow requests
do not suppress observation at the host's dispatch entry point.

After adding or dropping a widget, the workspace displays resize handles if the
provider permits resizing. The same frame is available through dock Settings →
Manage placed items → Resize. The built-in Sense clock supports both axes.

Drag a side handle to snap its edge to the home grid. The overlay shows the
proposed cell size and grid while resizing; occupied or out-of-bounds placement
is red and rejected on release. Valid releases save coordinates/spans and leave
the handles visible for another adjustment. Tap Done or outside the frame to
finish. Widget content inside the frame still receives normal taps.

Android provider resizeMode, minResizeWidth/Height, and maxResizeWidth/Height
constrain hosted widget handles. Unsupported axes remain fixed; providers with
no resize support do not show the frame. Source dimensions are converted using
cell pixel extents and the widget's default padding.

The host view reports each actual size change via the modern SizeF overload of
`updateAppWidgetSize`, even when the workspace's outer dimensions do not change.
This lets responsive RemoteViews providers choose layouts for the new space.

Android checks use the registered instrumentation runner with `-e suite widgets`.
They cover a child that consumes long clicks and disallows parent interception,
short taps, cancelled/moving gestures, grid-snapped resizing, collision rejection,
and saved spans. Resize tests use a separate preferences file and do not modify
the user's home layout.

References:
- https://developer.android.com/reference/android/appwidget/AppWidgetHostView
- https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo

## Remove from the home screen

Long-press a placed icon, widget or Sense clock to drag it. The dock and page
indicator become invisible, retaining their layout space so the desktop does not
resize. Their bottom slot instead shows a rubbish-bin icon and Remove label.
The bin uses Rosie's original `icon_remove_panel_out.png` and
`icon_remove_panel_over.png`, extracted at build time with provenance. Hovering
switches to the original open-lid icon and a red background. Dropping removes and
saves the item immediately. App shortcuts are removed from the workspace only;
Android widgets also release their AppWidgetHost ID. Cancelled drags preserve the
item. Dropping a new drawer shortcut on Remove cancels placing it.

The whole bottom slot is the drop target, above the system navigation inset.
The dock returns when dragging ends, including cancellation and workspace detach.
Moving between desktop pages does not remove the target.

`-e suite remove` in the Android instrumentation runner checks an actual system
long-press/drag cancellation and removal in an isolated workspace, saved shortcut
removal, widget ID release and protection against releasing an already removed ID, as well as dock hide/restore.
