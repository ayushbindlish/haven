package org.havenapp.main.location;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

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
        /** A trusted place suppresses "away" / "untrusted location" auto-arm. */
        public final boolean trusted;

        public Place(String name, double lat, double lng, float radiusM) {
            this(name, lat, lng, radiusM, false);
        }

        public Place(String name, double lat, double lng, float radiusM, boolean trusted) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.radiusM = radiusM;
            this.trusted = trusted;
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
                        (float) o.optDouble("r", 150), o.optBoolean("trusted", false)));
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
            o.put("trusted", p.trusted);
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

    public boolean hasTrusted() {
        for (Place p : all()) if (p.trusted) return true;
        return false;
    }

    /**
     * True when {@code loc} is inside at least one trusted place. A null location is treated
     * as "not in a trusted place" so a fix failure errs on the side of arming.
     */
    public boolean inTrustedPlace(Location loc) {
        if (loc == null) return false;
        float[] d = new float[1];
        for (Place p : all()) {
            if (!p.trusted) continue;
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), p.lat, p.lng, d);
            if (d[0] <= p.radiusM) return true;
        }
        return false;
    }
}
