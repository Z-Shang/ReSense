package dev.sense.messages;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.provider.Telephony;

final class MessageNotifications {
    static void received(Context c,String address,String body){
        long thread=Telephony.Threads.getOrCreateThreadId(c,address);
        show(c,"thread:"+thread,MessageStore.contactName(c,address),body,new Intent(c,MessagesActivity.class).putExtra("thread",thread).putExtra("address",address));
    }
    static void show(Context c,String tag,String title,String body,Intent intent){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("messages","Messages",NotificationManager.IMPORTANCE_HIGH));
        if(!MessageStore.permission(c,Manifest.permission.POST_NOTIFICATIONS))return;
        PendingIntent open=PendingIntent.getActivity(c,tag.hashCode(),intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(tag,1,new Notification.Builder(c,"messages").setSmallIcon(R.drawable.msg_stat_notify_sms).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(new Notification.Builder(c,"messages").setSmallIcon(R.drawable.msg_stat_notify_sms).setContentTitle("New message").build()).build());
    }
}
