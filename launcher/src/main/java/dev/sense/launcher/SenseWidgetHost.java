package dev.sense.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

/** Widget host that gives launcher long presses precedence over RemoteViews children. */
final class SenseWidgetHost extends AppWidgetHost {
    SenseWidgetHost(Context context,int id){super(context,id);}
    @Override protected AppWidgetHostView onCreateView(Context context,int appWidgetId,AppWidgetProviderInfo info) {
        return new SenseWidgetHostView(context);
    }
}
