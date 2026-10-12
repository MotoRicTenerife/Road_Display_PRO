package com.riccardo.roaddisplay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Persistent trip diary. Route points are stored separately so a long trip does not
 * become one huge row and can be streamed safely from the recorder. */
public final class TripDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "trip_diary.db";
    private static final int DB_VERSION = 1;

    public static final class TripSummary {
        public long id, startTime, endTime, durationMs;
        public double startLat, startLon, endLat, endLon;
        public double distanceM, avgKmh, maxKmh, minAlt, maxAlt, elevationGainM;
        public int pointCount;
        public boolean active;
    }

    public static final class Point {
        public final long time;
        public final double lat, lon, alt;
        public Point(long t, double a, double o, double z) { time=t; lat=a; lon=o; alt=z; }
    }

    public TripDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER DEFAULT 0," +
                "start_lat REAL NOT NULL,start_lon REAL NOT NULL," +
                "end_lat REAL DEFAULT 0,end_lon REAL DEFAULT 0," +
                "distance_m REAL DEFAULT 0,duration_ms INTEGER DEFAULT 0," +
                "avg_kmh REAL DEFAULT 0,max_kmh REAL DEFAULT 0," +
                "min_alt REAL DEFAULT 0,max_alt REAL DEFAULT 0," +
                "elevation_gain_m REAL DEFAULT 0,active INTEGER DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_trips_start ON trips(start_time DESC)");
        db.execSQL("CREATE TABLE trip_points(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,trip_id INTEGER NOT NULL," +
                "time INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,alt REAL DEFAULT 0," +
                "FOREIGN KEY(trip_id) REFERENCES trips(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_trip_points_trip_time ON trip_points(trip_id,time)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public synchronized long beginTrip(long startTime, double lat, double lon, double alt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("start_time", startTime); v.put("start_lat", lat); v.put("start_lon", lon);
        v.put("end_lat", lat); v.put("end_lon", lon); v.put("min_alt", alt); v.put("max_alt", alt);
        v.put("active", 1);
        return db.insertOrThrow("trips", null, v);
    }

    public synchronized void addPoint(long tripId, long time, double lat, double lon, double alt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("trip_id", tripId); v.put("time", time); v.put("lat", lat); v.put("lon", lon); v.put("alt", alt);
        db.insert("trip_points", null, v);
    }

    public synchronized void updateMetrics(long tripId, long endTime, double endLat, double endLon,
                                            double distanceM, long durationMs, double avgKmh, double maxKmh,
                                            double minAlt, double maxAlt, double elevationGainM) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("end_time", endTime); v.put("end_lat", endLat); v.put("end_lon", endLon);
        v.put("distance_m", distanceM); v.put("duration_ms", durationMs); v.put("avg_kmh", avgKmh);
        v.put("max_kmh", maxKmh); v.put("min_alt", minAlt); v.put("max_alt", maxAlt);
        v.put("elevation_gain_m", elevationGainM); v.put("active", 0);
        db.update("trips", v, "id=?", new String[]{Long.toString(tripId)});
    }

    public synchronized void markInterrupted(long tripId, long endTime, double endLat, double endLon,
                                              double distanceM, long durationMs, double avgKmh, double maxKmh,
                                              double minAlt, double maxAlt, double elevationGainM) {
        updateMetrics(tripId,endTime,endLat,endLon,distanceM,durationMs,avgKmh,maxKmh,minAlt,maxAlt,elevationGainM);
    }

    public synchronized List<TripSummary> listTrips(int limit) {
        ArrayList<TripSummary> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT t.id,t.start_time,t.end_time,t.start_lat,t.start_lon,t.end_lat,t.end_lon," +
                "t.distance_m,t.duration_ms,t.avg_kmh,t.max_kmh,t.min_alt,t.max_alt,t.elevation_gain_m,t.active," +
                "(SELECT COUNT(*) FROM trip_points p WHERE p.trip_id=t.id) " +
                "FROM trips t ORDER BY t.start_time DESC LIMIT ?", new String[]{Integer.toString(Math.max(1,limit))});
        try { while(c.moveToNext()) out.add(readSummary(c)); } finally { c.close(); }
        return out;
    }

    public synchronized TripSummary getTrip(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT t.id,t.start_time,t.end_time,t.start_lat,t.start_lon,t.end_lat,t.end_lon," +
                "t.distance_m,t.duration_ms,t.avg_kmh,t.max_kmh,t.min_alt,t.max_alt,t.elevation_gain_m,t.active," +
                "(SELECT COUNT(*) FROM trip_points p WHERE p.trip_id=t.id) FROM trips t WHERE t.id=?",
                new String[]{Long.toString(id)});
        try { return c.moveToFirst() ? readSummary(c) : null; } finally { c.close(); }
    }

    private TripSummary readSummary(Cursor c) {
        TripSummary t = new TripSummary();
        t.id=c.getLong(0); t.startTime=c.getLong(1); t.endTime=c.getLong(2);
        t.startLat=c.getDouble(3); t.startLon=c.getDouble(4); t.endLat=c.getDouble(5); t.endLon=c.getDouble(6);
        t.distanceM=c.getDouble(7); t.durationMs=c.getLong(8); t.avgKmh=c.getDouble(9); t.maxKmh=c.getDouble(10);
        t.minAlt=c.getDouble(11); t.maxAlt=c.getDouble(12); t.elevationGainM=c.getDouble(13); t.active=c.getInt(14)!=0; t.pointCount=c.getInt(15);
        return t;
    }

    public synchronized ArrayList<Point> getPoints(long tripId) {
        ArrayList<Point> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT time,lat,lon,alt FROM trip_points WHERE trip_id=? ORDER BY time ASC", new String[]{Long.toString(tripId)});
        try { while(c.moveToNext()) out.add(new Point(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3))); }
        finally { c.close(); }
        return out;
    }

    public synchronized void deleteTrip(long tripId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try { db.delete("trip_points","trip_id=?",new String[]{Long.toString(tripId)}); db.delete("trips","id=?",new String[]{Long.toString(tripId)}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    /** Any trip left active after a process restart is closed at its last persisted point. */
    public synchronized void recoverInterruptedTrips(long now) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id,end_lat,end_lon,distance_m,duration_ms,avg_kmh,max_kmh,min_alt,max_alt,elevation_gain_m FROM trips WHERE active=1",null);
        ArrayList<long[]> ids = new ArrayList<>();
        ArrayList<double[]> data = new ArrayList<>();
        try { while(c.moveToNext()) { ids.add(new long[]{c.getLong(0),c.getLong(4)}); data.add(new double[]{c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getDouble(5),c.getDouble(6),c.getDouble(7),c.getDouble(8),c.getDouble(9)}); } }
        finally { c.close(); }
        for(int i=0;i<ids.size();i++) updateMetrics(ids.get(i)[0],now,data.get(i)[0],data.get(i)[1],data.get(i)[2],ids.get(i)[1],data.get(i)[3],data.get(i)[4],data.get(i)[5],data.get(i)[6],data.get(i)[7]);
    }
}
