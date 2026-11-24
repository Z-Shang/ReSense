"""Copy Rosie's original search-widget artwork, retaining compiled nine-patch chunks."""
import hashlib,io,zipfile
from pathlib import Path

SOURCES={
 'res/drawable-hdpi/search_floater.9.png':('drawable-hdpi','sense_search_floater.png'),
 'res/drawable-hdpi/ic_search_widget.png':('drawable-hdpi','sense_search_icon.png'),
 'res/drawable/textfield_searchwidget_default.9.png':('drawable-mdpi','sense_search_field.png'),
 'res/drawable/textfield_searchwidget_pressed.9.png':('drawable-mdpi','sense_search_field_pressed.png'),
 'res/drawable/textfield_searchwidget_selected.9.png':('drawable-mdpi','sense_search_field_selected.png')}

def extract(rom_path,output):
    output=Path(output);report=[]
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/Rosie.apk'))) as apk:
            for source,(directory,name) in SOURCES.items():
                data=apk.read(source);target=output/directory/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
                report.append({'source':'Rosie.apk!/'+source,'resource':directory+'/'+name,'sha256':hashlib.sha256(data).hexdigest()})
    return {'layout':'Rosie.apk!/res/layout/widget_search.xml','paddingDp':[14,13,14,12],'fieldHeightDp':42,'assets':report}
