package com.riccardo.roaddisplay;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Local-only telemetry store. Raw IMU is sampled in memory and persisted at GPS cadence. */
public final class TelemetryDb extends SQLiteOpenHelper {
    private static final String NAME="road_telemetry.db";
    private static final int VERSION=1;
    public TelemetryDb(Context c){super(c,NAME,null,VERSION);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,start_time INTEGER NOT NULL,end_time INTEGER DEFAULT 0,calibrated INTEGER DEFAULT 0,calibration_time INTEGER DEFAULT 0,sample_count INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL,time INTEGER NOT NULL,lat REAL,lon REAL,speed_kmh REAL,bearing REAL,accuracy_m REAL,altitude REAL,slope_pct REAL,roll_deg REAL,pitch_deg REAL,yaw_deg REAL,gyro_x REAL,gyro_y REAL,gyro_z REAL,accel_x REAL,accel_y REAL,accel_z REAL,accel_g REAL,curve_type INTEGER,curve_dir INTEGER,curve_distance_m REAL,curve_confidence REAL,curve_radius_m REAL,speed_limit_kmh INTEGER,matched_way_id TEXT,road_source TEXT,FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX idx_telemetry_samples_session_time ON samples(session_id,time)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    @Override public void onOpen(SQLiteDatabase db){ super.onOpen(db); try{ db.enableWriteAheadLogging(); }catch(Exception ignored){} db.execSQL("PRAGMA foreign_keys=ON"); }
    public synchronized long begin(long now,boolean calibrated,long calTime){ContentValues v=new ContentValues();v.put("start_time",now);v.put("calibrated",calibrated?1:0);v.put("calibration_time",calTime);return getWritableDatabase().insertOrThrow("sessions",null,v);}
    public synchronized void addSample(long sid,long time,double lat,double lon,float speed,float bearing,float acc,double alt,float slope,float roll,float pitch,float yaw,float gx,float gy,float gz,float ax,float ay,float az,float amag,Curve curve,int limit,String way){
        ContentValues v=new ContentValues();v.put("session_id",sid);v.put("time",time);v.put("lat",lat);v.put("lon",lon);v.put("speed_kmh",speed);v.put("bearing",bearing);v.put("accuracy_m",acc);v.put("altitude",alt);v.put("slope_pct",slope);v.put("roll_deg",roll);v.put("pitch_deg",pitch);v.put("yaw_deg",yaw);v.put("gyro_x",gx);v.put("gyro_y",gy);v.put("gyro_z",gz);v.put("accel_x",ax);v.put("accel_y",ay);v.put("accel_z",az);v.put("accel_g",amag);
        v.put("curve_type",curve==null?0:curve.type);v.put("curve_dir",curve==null?0:curve.dir);v.put("curve_distance_m",curve==null?0:curve.distance);v.put("curve_confidence",curve==null?0:curve.confidence);v.put("curve_radius_m",curve==null?0:curve.radius);v.put("speed_limit_kmh",Math.max(0,limit));v.put("matched_way_id",way==null?"":way);v.put("road_source",curve==null?"":(curve.source==null?"":curve.source));
        getWritableDatabase().insert("samples",null,v);
    }
    public synchronized void finish(long sid,long end){ContentValues v=new ContentValues();v.put("end_time",end);v.put("sample_count",countSamples(sid));getWritableDatabase().update("sessions",v,"id=?",new String[]{Long.toString(sid)});}
    private int countSamples(long sid){android.database.Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM samples WHERE session_id=?",new String[]{Long.toString(sid)});try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    @Override public synchronized void close(){super.close();}
}
