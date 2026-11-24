package dev.sense.launcher;

import android.app.Activity;

/** Weather controls live alongside the other launcher settings. */
final class WeatherSettings {
    static void show(Activity activity){LauncherSettingsActivity.open(activity,"weather");}
    static void openWeather(Activity activity){
        var prefs=WeatherRepository.get(activity).prefs;
        try{
            android.content.Intent intent=new android.content.Intent().setComponent(new android.content.ComponentName("dev.sense.weather","dev.sense.weather.WeatherActivity"));
            intent.putExtra("sharedWeather",true);
            intent.putExtra("city",prefs.getString("city","Sydney"));
            intent.putExtra("latitude",Double.parseDouble(prefs.getString("latitude","-33.8688")));
            intent.putExtra("longitude",Double.parseDouble(prefs.getString("longitude","151.2093")));
            activity.startActivity(intent);
        }catch(android.content.ActivityNotFoundException | SecurityException | NumberFormatException error){show(activity);}
    }
    private WeatherSettings(){}
}
