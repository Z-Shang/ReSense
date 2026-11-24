package dev.sense.contacts;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.*;
import android.provider.ContactsContract.CommonDataKinds.*;
import java.util.*;

/** Immutable snapshots of aggregated contacts, with one row per person. Call on a worker. */
final class PeopleRepository {
    record Field(String kind,String label,String value) {}
    record Person(long id,String lookup,String name,String photo,boolean favorite,List<Field> fields) {
        Uri uri(){return lookup==null?android.content.ContentUris.withAppendedId(Contacts.CONTENT_URI,id):Contacts.getLookupUri(id,lookup);}
        String subtitle(){for(Field f:fields)if(f.kind().equals("phone")||f.kind().equals("email"))return f.value();return "No phone number";}
    }
    record Group(long id,String name,Set<Long> members) {}
    record Snapshot(List<Person> people,List<Group> groups) {}
    private static final class Builder {
        long id;String lookup,name,photo;boolean favorite;ArrayList<Field> fields=new ArrayList<>();
        Person build(){return new Person(id,lookup,name,photo,favorite,List.copyOf(fields));}
    }
    static Snapshot read(Context context) {
        var resolver=context.getContentResolver();LinkedHashMap<Long,Builder> people=new LinkedHashMap<>();
        try(Cursor c=resolver.query(Contacts.CONTENT_URI,new String[]{Contacts._ID,Contacts.LOOKUP_KEY,Contacts.DISPLAY_NAME_PRIMARY,Contacts.PHOTO_URI,Contacts.STARRED},null,null,Contacts.SORT_KEY_PRIMARY+" ASC, "+Contacts._ID+" ASC")){
            if(c==null)throw new IllegalStateException("Contacts unavailable");
            while(c.moveToNext()){Builder b=new Builder();b.id=c.getLong(0);b.lookup=c.getString(1);b.name=c.getString(2);if(b.name==null||b.name.isBlank())b.name="Unnamed contact";b.photo=c.getString(3);b.favorite=c.getInt(4)!=0;people.put(b.id,b);}
        }
        Map<Long,Set<Long>> members=new HashMap<>();
        try(Cursor c=resolver.query(Data.CONTENT_URI,new String[]{Data.CONTACT_ID,Data.MIMETYPE,Data.DATA1,Data.DATA2,Data.DATA3,Data.DATA4},null,null,Data._ID+" ASC")){
            if(c==null)throw new IllegalStateException("Contact details unavailable");
            while(c.moveToNext()){
                long id=c.getLong(0);Builder b=people.get(id);if(b==null)continue;
                String mime=c.getString(1),value=c.getString(2);if(value==null||value.isBlank())continue;
                int type=c.getInt(3);String custom=c.getString(4);String kind=null,label=null;
                switch(mime){
                    case Phone.CONTENT_ITEM_TYPE -> {kind="phone";label=Phone.getTypeLabel(context.getResources(),type,custom).toString();}
                    case Email.CONTENT_ITEM_TYPE -> {kind="email";label=Email.getTypeLabel(context.getResources(),type,custom).toString();}
                    case StructuredPostal.CONTENT_ITEM_TYPE -> {kind="address";label=StructuredPostal.getTypeLabel(context.getResources(),type,custom).toString();}
                    case Organization.CONTENT_ITEM_TYPE -> {kind="organization";label="Organization";if(c.getString(5)!=null&&!c.getString(5).isBlank())value+=" · "+c.getString(5);}
                    case Website.CONTENT_ITEM_TYPE -> {kind="website";label="Website";}
                    case Event.CONTENT_ITEM_TYPE -> {kind="event";label=Event.getTypeLabel(context.getResources(),type,custom).toString();}
                    case Note.CONTENT_ITEM_TYPE -> {kind="note";label="Notes";}
                    case GroupMembership.CONTENT_ITEM_TYPE -> {try{members.computeIfAbsent(Long.parseLong(value),key->new HashSet<>()).add(id);}catch(NumberFormatException ignored){}}
                    default -> { }
                }
                if(kind!=null){Field field=new Field(kind,label,value);if(!b.fields.contains(field))b.fields.add(field);}
            }
        }
        ArrayList<Group> groups=new ArrayList<>();
        try(Cursor c=resolver.query(Groups.CONTENT_URI,new String[]{Groups._ID,Groups.TITLE},Groups.DELETED+"=0",null,Groups.TITLE+" COLLATE LOCALIZED ASC")){
            if(c!=null)while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1);if(title!=null&&!title.isBlank())groups.add(new Group(id,title,Set.copyOf(members.getOrDefault(id,Set.of()))));}
        }
        ArrayList<Person> result=new ArrayList<>();for(Builder b:people.values())result.add(b.build());return new Snapshot(List.copyOf(result),List.copyOf(groups));
    }
    private PeopleRepository() {}
}
