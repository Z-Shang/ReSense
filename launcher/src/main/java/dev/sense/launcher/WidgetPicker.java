package dev.sense.launcher;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import java.util.List;

/** Widget metadata catalogue; UI and lifecycle belong to WidgetPickerActivity. */
final class WidgetPicker {
    record Row(String label,AppWidgetProviderInfo info,boolean header,int musicRows,boolean search,String group,String weatherStyle){boolean music(){return musicRows>0;}boolean weather(){return weatherStyle!=null;}}
    private record Group(String name,java.util.ArrayList<Row> widgets){}
    static List<Row> groupedRows(Context context,List<AppWidgetProviderInfo> providers){
        java.util.Map<String,Group> groups=new java.util.HashMap<>();
        for(AppWidgetProviderInfo info:providers){
            String key=info.provider.getPackageName()+"/"+info.getProfile();
            Group group=groups.get(key);
            if(group==null){
                String name=info.provider.getPackageName();
                try{name=context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(name,0)).toString();}
                catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
                name=context.getPackageManager().getUserBadgedLabel(name,info.getProfile()).toString();
                group=new Group(name,new java.util.ArrayList<>());groups.put(key,group);
            }
            group.widgets().add(new Row(info.loadLabel(context.getPackageManager()),info,false,0,false,key,null));
        }
        String ownKey=context.getPackageName()+"/"+android.os.Process.myUserHandle();
        Group own=groups.computeIfAbsent(ownKey,ignored->new Group("Sense Launcher",new java.util.ArrayList<>()));
        for(int style:new int[]{1,2,4})own.widgets().add(new Row("HTC Sense Music · 4 × "+style,null,false,style,false,ownKey,null));
        own.widgets().add(new Row("HTC Sense Search · 4 × 1",null,false,0,true,ownKey,null));
        for(String style:List.of("2x1","2x2","4x4"))own.widgets().add(new Row("HTC Sense Weather · "+style,null,false,0,false,ownKey,style));
        java.text.Collator collator=java.text.Collator.getInstance();
        var sorted=new java.util.ArrayList<>(groups.entrySet());
        sorted.sort((a,b)->{int order=collator.compare(a.getValue().name(),b.getValue().name());return order!=0?order:a.getKey().compareTo(b.getKey());});
        var rows=new java.util.ArrayList<Row>();
        for(var entry:sorted){
            Group group=entry.getValue();group.widgets().sort((a,b)->collator.compare(a.label(),b.label()));
            rows.add(new Row(group.name(),null,true,0,false,entry.getKey(),null));rows.addAll(group.widgets());
        }
        return rows;
    }
    record Style(String key,String label,AppWidgetProviderInfo info,int musicRows,boolean search,String weatherStyle){boolean music(){return musicRows>0;}boolean weather(){return weatherStyle!=null;}}
    record Family(String key,String name,String application,List<Style> styles){}
    record ProviderGroup(String key,String name,List<Family> families){}
    static List<ProviderGroup> providerGroups(Context context,List<Family> families){
        java.util.Map<String,java.util.ArrayList<Family>> groups=new java.util.HashMap<>();
        java.util.Map<String,String> names=new java.util.HashMap<>();
        for(Family family:families){
            var info=family.styles().get(0).info();
            String key=info==null?context.getPackageName()+"/"+android.os.Process.myUserHandle():info.provider.getPackageName()+"/"+info.getProfile();
            String name=family.application();
            if(info!=null)name=context.getPackageManager().getUserBadgedLabel(name,info.getProfile()).toString();
            names.put(key,name);groups.computeIfAbsent(key,ignored->new java.util.ArrayList<>()).add(family);
        }
        java.text.Collator collator=java.text.Collator.getInstance();var result=new java.util.ArrayList<ProviderGroup>();
        for(var entry:groups.entrySet()){
            entry.getValue().sort((a,b)->{int order=collator.compare(a.name(),b.name());return order!=0?order:a.key().compareTo(b.key());});
            result.add(new ProviderGroup(entry.getKey(),names.get(entry.getKey()),List.copyOf(entry.getValue())));
        }
        result.sort((a,b)->{int order=collator.compare(a.name(),b.name());return order!=0?order:a.key().compareTo(b.key());});return List.copyOf(result);
    }
    /** One chooser entry per application/profile, with all of its widget variants. */
    static List<Family> providerFamilies(Context context,List<Family> families){
        var result=new java.util.ArrayList<Family>();
        for(var provider:providerGroups(context,families)){
            var styles=new java.util.ArrayList<Style>();
            for(var family:provider.families())styles.addAll(family.styles());
            result.add(new Family(provider.key(),provider.name(),provider.name(),List.copyOf(styles)));
        }
        return List.copyOf(result);
    }
    static List<Family> families(Context context,List<AppWidgetProviderInfo> providers){
        java.util.Map<String,java.util.ArrayList<Style>> styles=new java.util.LinkedHashMap<>();
        java.util.Map<String,String> names=new java.util.HashMap<>(),applications=new java.util.HashMap<>();
        for(Row row:groupedRows(context,providers)){
            if(row.header())continue;
            String name=row.music()?"HTC Sense Music":row.search()?"HTC Sense Search":row.weather()?"HTC Sense Weather":row.info().loadLabel(context.getPackageManager());
            String key=row.music()?"sense:music":row.search()?"sense:search":row.weather()?"sense:weather":row.group()+"/"+name;
            String application="Sense Launcher";
            if(row.info()!=null)try{application=context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(row.info().provider.getPackageName(),0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
            names.put(key,name);applications.put(key,application);
            String styleKey=row.music()?"music:"+row.musicRows():row.search()?"search":row.weather()?"weather:"+row.weatherStyle():row.info().provider.flattenToString()+"/"+row.info().getProfile();
            styles.computeIfAbsent(key,ignored->new java.util.ArrayList<>()).add(new Style(styleKey,name,row.info(),row.musicRows(),row.search(),row.weatherStyle()));
        }
        java.util.ArrayList<Family> result=new java.util.ArrayList<>();
        for(var entry:styles.entrySet()){
            entry.getValue().sort((a,b)->a.music()&&b.music()?Integer.compare(a.musicRows(),b.musicRows()):a.key().compareTo(b.key()));
            result.add(new Family(entry.getKey(),names.get(entry.getKey()),applications.get(entry.getKey()),List.copyOf(entry.getValue())));
        }
        java.text.Collator collator=java.text.Collator.getInstance();result.sort((a,b)->{int order=collator.compare(a.name(),b.name());return order!=0?order:a.key().compareTo(b.key());});return List.copyOf(result);
    }
}
