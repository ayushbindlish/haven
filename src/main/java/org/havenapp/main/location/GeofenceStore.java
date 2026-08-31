package org.havenapp.main.location;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Simple JSON-in-SharedPreferences store for named circular geofences. */
public final class GeofenceStore {

    public static final class Place {
        public final String name;
        public final double lat, lng;
        public final float radiusM;

        public Place(String name, double lat, double lng, float radiusM) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.radiusM = radiusM;
        }
    }

    private static final String PREFS = "org.havenapp.main";
    private static final String KEY = "geofences";

    private final SharedPreferences sp;

    public GeofenceStore(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Place> all() {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Place(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"),
                        (float) o.optDouble("r", 150)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void add(Place p) {
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", p.name);
            o.put("lat", p.lat);
            o.put("lng", p.lng);
            o.put("r", p.radiusM);
            a.put(o);
            sp.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void removeByName(String name) {
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            JSONArray b = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (!a.getJSONObject(i).getString("name").equals(name)) b.put(a.getJSONObject(i));
            }
            sp.edit().putString(KEY, b.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void clear() {
        sp.edit().remove(KEY).apply();
    }
}
