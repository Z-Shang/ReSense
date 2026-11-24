package dev.sense.launcher;

/** Open-Meteo's WMO condition codes, independent of Android UI. */
final class WeatherCodes {
    static String description(int code) {
        return switch(code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45,48 -> "Fog";
            case 51,53,55 -> "Drizzle";
            case 56,57 -> "Freezing drizzle";
            case 61,63,65 -> "Rain";
            case 66,67 -> "Freezing rain";
            case 71,73,75,77 -> "Snow";
            case 80,81,82 -> "Rain showers";
            case 85,86 -> "Snow showers";
            case 95,97 -> "Thunderstorm";
            case 96,99 -> "Thunderstorm / hail";
            default -> "Conditions unavailable";
        };
    }
    /** WMO and HTC conditions differ; select the closest available original clock scene. */
    static int htcScene(int code,boolean day) {
        return switch(code) {
            case 0 -> day ? 1 : 33;
            case 1 -> day ? 2 : 34;
            case 2 -> day ? 3 : 35;
            case 3 -> 7;
            case 45,48 -> 11;
            case 51,53,55 -> 12;
            case 56,57,66,67 -> 26;
            case 61,63,65 -> 18;
            case 71,73,75,86 -> 22;
            case 77,85 -> 19;
            case 80,81,82 -> 12;
            case 95,96,97,99 -> 15;
            default -> 0;
        };
    }
    static boolean snow(int code){return code==71||code==73||code==75||code==77||code==85||code==86;}
    static boolean rain(int code){return code>=51&&code<=67||code>=80&&code<=82||code==95||code==96||code==97||code==99;}
    private WeatherCodes() { }
}
