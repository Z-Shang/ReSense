#!/usr/bin/env python3
"""Single source of shared HTC UI assets. Compiled nine-patches are copied byte-for-byte."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path

FRAMEWORK_NAMES={
    'common_list_divider.9.png','common_list_divider_vertical.9.png','list_selector_background_pressed.9.png',
    'common_app_bkg_src.png','common_subnav_selector_tab.9.png',
    'common_icon_phone_rest.png','common_icon_all_people_rest.png','common_icon_group_rest.png',
    'common_icon_call_history_rest.png','common_icon_voicemail_rest.png',
    'common_icon_all_people_on.png','common_icon_group_on.png','common_icon_default_group.png','common_icon_glance_add_rest.png','common_icon_glance_add_on.png',
    'common_panel_input_base.9.png','common_icon_clear_input_rest.png','common_icon_clear_input_press.png',
    'common_icon_glance_grid_rest.png','common_icon_glance_grid_on.png','common_icon_glance_list_view_rest.png','common_icon_glance_list_view_on.png',
    'common_icon_glance_down_rest.png','common_icon_glance_down_on.png','common_icon_glance_updates_rest.png','common_icon_glance_updates_on.png','common_icon_action_delete_on.png','common_icon_my_apps_rest.png','common_icon_my_apps_on.png','common_icon_starred_rest.png','common_icon_starred_on.png','common_icon_glance_next_rest.png','common_icon_glance_next_on.png','common_icon_call_history_on.png',
    'common_icon_phone_on.png','common_icon_glance_more_rest.png','common_icon_glance_more_on.png','common_icon_glance_previous_rest.png','common_icon_glance_previous_on.png',
    'common_icon_glance_show_keypad_rest.png','common_icon_glance_show_keypad_on.png',
    'common_icon_glance_hide_keypad_rest.png','common_icon_glance_hide_keypad_on.png',
    'common_inputfield_rest.9.png','common_inputfield_selected.9.png','common_photo_frame.9.png',
    'common_button_rest.9.png','common_button_press.9.png','common_button_selected.9.png',
    'common_checkbox_off.png','common_checkbox_on.png','common_checkbox_off_disable.png','common_checkbox_on_disable.png',
    'common_checkbox_off_pressed.png','common_checkbox_on_pressed.png',
    'common_radiobutton_off.png','common_radiobutton_on.png','common_radiobutton_off_disable.png','common_radiobutton_on_disable.png',
    'common_radiobutton_off_selected.png','common_radiobutton_selected.png',
    'common_popupmenu.9.png','common_dropdown_arrow_rest.png','common_dropdown_arrow_press.png',
    'common_icon_action_delete_rest.png','common_icon_favorites_rest.png','common_icon_favorites_on.png','common_icon_action_add_rest.png',
    'common_icon_callhistory_incoming.png','common_icon_callhistory_outgoing.png','common_icon_callhistory_misscall.png',
}

def prepare(rom,output):
    output=Path(output);report=[];target_dir=output/'drawable-hdpi';target_dir.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(rom) as source,zipfile.ZipFile(io.BytesIO(source.read('system/framework/com.htc.resources.apk'))) as apk:
        for path in sorted(apk.namelist()):
            if not path.startswith('res/drawable-hdpi/') or not path.endswith('.png'):continue
            name=path.rsplit('/',1)[1]
            if name not in FRAMEWORK_NAMES and not name.startswith(('common_tab_button_','common_titlebar_btn_')):continue
            data=apk.read(path);filename='sense_'+name.replace('.9.png','.png');(target_dir/filename).write_bytes(data)
            report.append(dict(source='com.htc.resources.apk!/'+path,resource=filename,sha256=hashlib.sha256(data).hexdigest()))
    with zipfile.ZipFile(rom) as source,zipfile.ZipFile(io.BytesIO(source.read('system/app/HtcContacts.apk'))) as apk:
        for name,filename in {'people_home_defaultphoto.png':'sense_people_default_photo.png','search_people.png':'sense_people_search.png'}.items():
            path='res/drawable-hdpi/'+name;data=apk.read(path);(target_dir/filename).write_bytes(data)
            report.append(dict(source='HtcContacts.apk!/'+path,resource=filename,sha256=hashlib.sha256(data).hexdigest()))
    with zipfile.ZipFile(rom) as source,zipfile.ZipFile(io.BytesIO(source.read('system/app/HomePersonalize.apk'))) as apk:
        for name in ('home_widget_picker_arrow_left.png','home_widget_picker_arrow_right.png'):
            path='res/drawable-hdpi/'+name;data=apk.read(path);filename='sense_'+name
            (target_dir/filename).write_bytes(data)
            report.append(dict(source='HomePersonalize.apk!/'+path,resource=filename,sha256=hashlib.sha256(data).hexdigest()))
    raw=output/'raw';raw.mkdir(parents=True,exist_ok=True);(raw/'sense_framework_provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'Prepared {len(report)} shared Sense framework assets')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--rom',required=True);parser.add_argument('--output',required=True);args=parser.parse_args();prepare(args.rom,args.output)
