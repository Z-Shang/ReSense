# Stock Messages artwork and SMS styling

Source: `res/3.33.401.153.zip!system/app/Mms.apk`; reference APK and aapt dumps in
`references/3.33.401.153/messages/`. Package `com.android.mms`, version
`3.5.2215192631.376509`, SDK/target 15. These are visual references for the new
SMS module only. Do not load the old APK, HTC widgets, services, permissions,
MMS stack, attachment flows, or other legacy runtime behavior.

## Extraction and preview

```sh
python3 tools/prepare_messages_app.py \
  --rom res/3.33.401.153.zip \
  --output /tmp/sense-messages-res \
  --contact-sheet .toolchain/messages-stock-assets.png
```

The `:messages` Gradle asset task generates these resources under
`messages/build/generated/htcMessages/res`; it does not modify source resources. The extractor uses the Python standard library; only the optional
contact sheet requires Pillow. All 124 immediate `res/drawable-hdpi/*.png`
entries are copied, including artwork unrelated to the SMS UI. Copying artwork
does not imply implementing those features.

Names are always `msg_` + original basename; `.9.png` becomes `.png`.
For example `msg_icon_send_rest.png` becomes `msg_msg_icon_send_rest.png`.
Bytes are copied directly, without PNG decoding or re-encoding, preserving
compiled `npTc` nine-patch chunks. Keep these compiled assets as `.png`; do not
manufacture source nine-patch borders. The preview decodes separate in-memory
copies and is not a source asset. No XML selectors or resource-table aliases
are copied, so reconstruct relevant state lists in the new module later.

`raw/messages_provenance.json` is a sorted array with `source` (APK member chain),
`resource` (output basename), and SHA-256 of the exact uncompressed PNG bytes.
Name collisions are rejected before writing. Unrelated destination resources
are neither removed nor renamed. Badging resolves the application icon to
`res/drawable-hdpi/messages.png` for both listed densities (160 and 240), so
`msg_messages.png` is already included; no alternate-qualifier icon is needed.

## Recommended drawable names

These are Android resource names, without the `.png` extension. `l_` names are
stock resource-table aliases, not extracted filenames.

| Role | Original resource / alias | New drawable |
| --- | --- | --- |
| Sent SMS bubble | `0x7f020165 l_message_conversation_me` → `0x7f020043 message_conversation_me` | `msg_message_conversation_me` |
| Received SMS bubble | `0x7f020166 l_message_conversation_others` → `0x7f020044 message_conversation_others` | `msg_message_conversation_others` |
| App icon | `messages` | `msg_messages` |
| Send, resting / active | `msg_icon_send_rest` / `msg_icon_send_on` | `msg_msg_icon_send_rest` / `msg_msg_icon_send_on` |
| Button resting / pressed / selected | `message_btn_rest` / `message_btn_pressed` / `message_btn_selected` | `msg_message_btn_rest` / `msg_message_btn_pressed` / `msg_message_btn_selected` |
| Input panel resting / pressed / selected | `message_btn_input_rest` / `message_btn_input_pressed` / `message_btn_input_selected` | `msg_message_btn_input_rest` / `msg_message_btn_input_pressed` / `msg_message_btn_input_selected` |
| Sending / failed / delivery report | `msg_detail_indicator_sending` / `msg_detail_indicator_undelivered` / `msg_detail_indicator_deliveryreport` | `msg_msg_detail_indicator_sending` / `msg_msg_detail_indicator_undelivered` / `msg_msg_detail_indicator_deliveryreport` |
| Contact picker resting / active | `icon_contact_picker_rest` / `icon_contact_picker_on` | `msg_icon_contact_picker_rest` / `msg_icon_contact_picker_on` |
| Compose action icon | `message_icon_edit_rest` | `msg_message_icon_edit_rest` |

Visual inspection of `.toolchain/messages-stock-assets.png` confirms two distinct
200 × 88 px bubble images: sent is pale gray with subtle texture/gradient,
right-pointing tail, thin gray outline and bottom shadow; received is pale blue
with a left-pointing tail, thin outline and bottom shadow. Do not mirror a single
bubble or use green for sent messages. Green belongs to pressed/selected button
states. Preserve the stock stretch regions rather than scaling tails and shadows
uniformly. The sheet shows original image dimensions and labels without the
extractor's additional `msg_` prefix.

## Layout evidence and dimensions

`text_msg_view_me.txt` is a full-width horizontal row with right gravity and
left empty-space padding; `text_msg_view_others.txt` reserves right empty space
and includes the received bubble. Both bubble layouts wrap content. Hidden,
zero-size contact badges are present; visible avatars beside every SMS are not
supported by these layouts. Timestamp/status content sits below the body inside
the bubble (`sent_time_view`, `indicator`); separate zero-height fake status
views reserve structure. Body ID is `0x7f0e007f`; time ID is `0x7f0e0069`.

The Mms resource-table dimensions below are **px**, not dp/sp (type 0x05,
unit nibble 0). At hdpi, divide by 1.5 for an approximate dp design translation;
do not silently reinterpret the original pixel counts as dp.

| ID suffix (`0x7f0b…`) | Stock dimension | px | Placement |
| --- | --- | ---: | --- |
| `00e0` | `me_bubble_empty_width` | 110 | Sent row left padding |
| `00e1` | `others_bubble_empty_width` | 98 | Received row right padding |
| `00e2` | `bubble_marging` | 12 | Top margin |
| `00e3` | `bubble_marging_vertical` | 6 | Sent right / received left margin, despite name |
| `00e4` | `bubble_padding_bottom` | 16 | Both bubbles |
| `00e5` | `me_bubble_padding_left` | 12 | Sent outer container |
| `00e6` | `others_bubble_padding_right` | 12 | Received outer container |
| `00e7` | `text_padding` | 35 | Sent inner right / received inner left |
| `0094` | `message_view_text_marginTop` | 12 | Body top margin |
| `0096` | `message_view_viewstub_marginBottom` | 5 | Stub top/bottom margins |
| `0098` | `common_list_item_indicator_width` | 36 | Status image width/height |

Use these with the nine-patch's own padding deliberately; the dumps specify
explicit padding on selected sides, so adding every inset twice is incorrect.
The stock empty-space values are evidence for asymmetric row width, not a
universal maximum-width percentage on modern displays.

`compose_message_activity.txt` establishes a vertical screen, recipient bar,
content background `#fff2f2f2`, a 2 px `#ff1e1e1e` separator, and bottom panel
container. `edit_message_panel.txt` uses a bottom-gravity relative layout,
full-width wrapping editor, right-aligned send button and text/segment counters.
`l_msg_input_editor` (`0x7f02018b`) aliases drawable `0x7f02009e`;
`l_icon_send_button` (`0x7f020195`) and `l_msg_button_notrans` (`0x7f020174`)
are indirections, not PNG basenames. Rebuild their state behavior from the
recommended resting/active icons and button art. Attachment and subject controls
in these original layouts are outside the new SMS scope.

`list_item_conversation.txt` delegates the inbox row to `HtcListItem` with
`itemMode=1` and `HtcListItemMessageBody`; it does not specify a locally recoverable
row height or all inbox text sizes. `recipients_editor.txt` is another reference
for the recipient field. Reimplement the structure with current Android views.

## Typography and remaining limits

The bubble body uses HTC `list_primary_m` (`0x02030036`), timestamps use
`list_secondary_m` (`0x0203003d`), and links use `0x020a001c`. Body autoLink
mask is `0xf`, with `linksClickable=false` in the XML; do not infer runtime link
handling from that alone.

Local `aapt dump --values resources` on the ROM's
`system/framework/com.htc.resources.apk` resolves these styles: primary text size
references `0x02050029`, secondary `0x0205002e`, colors reference `0x020a005d`
and `0x020a005f`. The first hdpi entries are 26 px primary and 20 px secondary;
additional HTC configuration entries offer 22–32 px primary and 16–26 px
secondary, plus Chinese locale variants. These are stock pixel sizes, not
26 sp / 20 sp. A reasonable modern baseline is about 17.3 sp body and 13.3 sp
time at default hdpi font scale, retaining user font scaling. This is a design
translation, not proof of the legacy app's runtime font selection.

The editor style `message_text_editor_style` inherits
`message_text_editor_style_default`: text size `0x02050024` (`input_default_m`,
first hdpi value 26 px), black text, hint `#ff999999`, top/start gravity,
wrap height and sentence capitalization/autotext. Its actual size comes from HTC,
not the unused-looking local `compose_bottom_editor_textsize` alias (15 px).
Counter style uses `text_size_editor_count` = 20 px, bold, right gravity and HTC
secondary color. Reply-phone-number size resolves to local `text_size_22` = 22 px.
No exact modern font family, final state-dependent text colors, inbox metrics,
or runtime overrides are claimed from these dumps.

## Validation

Extraction was run against the ROM, not the standalone reference APK. All 124
outputs and manifest hashes were compared with the ROM's APK members. Compiled
nine-patch chunks were parsed and compared byte-for-byte, including both bubble
images. A second run produced the same provenance bytes. The contact sheet was
generated and visually inspected. The modern app integration is documented in [MESSAGES-APP.md](MESSAGES-APP.md); no legacy runtime is used.

## Layout correction in the modern app

The conversation background is now the XML's `#f2f2f2`. Bubble row gutters
translate the original hdpi 110/98 px empty widths to approximately 73/65 dp;
outer margins are 4 dp, top margin 8 dp. Sent text gets 8 dp left / 23 dp right
padding, received text gets 23 dp left / 8 dp right, with 11 dp bottom padding.
The entire bubble, including body and stamp, shares one available-width budget.

The editor is one full-width `message_btn_input_*` panel, with the text editor ending
80 dp before the right edge (stock 120 px reserved for controls). The modern
editor uses a right layout margin rather than padding so its editable and touch
bounds stay entirely to the left of Send and the counters. Send uses the original
icon as an alternative to its text label, not an icon stacked over a label.
The original send target is 100×62 px; the modern target uses 67×48 dp to retain
a usable touch height. The two numeric counters live inside the panel above
Send. Its 205 px minimum translates to 137 dp; this reduces to 90 dp with the
IME visible on modern displays. Attachment controls are omitted for the SMS-only
scope rather than presenting an unsupported attachment action.

The contact picker is in the recipient row. Existing conversations show their
recipient in the title area and collapse that row, keeping the hidden address
field for SMS dispatch. Header actions use compact unframed touch targets.
Inbox row metrics remain an approximation of the framework `HtcListItem`, since
the local XML delegates sizing to framework classes.

## Screen transitions

`transitions/framework-res-activity_{open,close}_{enter,exit}.txt` contains the
ROM's compiled XML dumps. Open-enter fades alpha 0→1, close-exit fades 1→0;
both use `config_shortAnimTime` = 200 ms and
`accelerate_decelerate_interpolator`. The other two sets have no animation
children, retaining the underlying screen. The resource values were resolved
from the same ROM, not the modern device defaults. Separate HTC slide resources
use 300 ms; these alone do not establish a Messages conversation transition.
Runtime overrides in the old app have not been traced, so the recovered window
resources are the basis for this recreation, not a claim of complete legacy
runtime equivalence.

`SenseScreenHost` reproduces these fades for the modern app's in-activity
conversation stack. Incoming content fades over the previous screen when opening;
the previous screen fades away above the inbox when closing. Both the header
back action and Android Back share this route. Interrupted animations settle to
the latest screen; detached old views are released. Property animations follow
Android frame scheduling, duration scaling and disabled-animation settings.
Window open/close overrides use equivalent XML for external Activity navigation.
