package com.riccardo.roaddisplay;

import android.location.Location;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded-rate telemetry writer. Sensor callbacks never write SQLite directly. */
public final class TelemetryRecorder {
    private final TelemetryDb db;
    private final SensorFusion fusion;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"road-telemetry-io");t.setDaemon(true);return t;});
    private final Object lock=new Object();
    private boolean active;
    private long sessionId=-1;
    private long lastPersistMs;
    private SensorFusion.FusionSample latest;
    public TelemetryRecorder(TelemetryDb d,SensorFusion f){db=d;fusion=f;}
    public void start(){synchronized(lock){if(active)return;active=true;sessionId=db.begin(System.currentTimeMillis(),fusion.hasCalibration(),fusion.calibrationTime());lastPersistMs=0;}}
    public void stop(){long id; synchronized(lock){if(!active)return;active=false;id=sessionId;sessionId=-1;} final long sid=id;io.execute(()->db.finish(sid,System.currentTimeMillis()));}
    public void onFusion(SensorFusion.FusionSample s){synchronized(lock){latest=s;}}
    public void onLocation(Location l,Curve curve,int limit,String way){
        if(l==null)return;long now=l.getTime()>0?l.getTime():System.currentTimeMillis();SensorFusion.FusionSample s;
        final long sid;
        synchronized(lock){if(!active||now-lastPersistMs<900)return;lastPersistMs=now;s=latest;sid=sessionId;}
        if(s==null||sid<0)return; final SensorFusion.FusionSample fs=s; final Curve copy=copyCurve(curve);
        final float speed=l.hasSpeed()?Math.max(0f,l.getSpeed()*3.6f):0f,bearing=l.hasBearing()?l.getBearing():0f,acc=l.hasAccuracy()?l.getAccuracy():999f;final double alt=l.hasAltitude()?l.getAltitude():0;
        io.execute(()->db.addSample(sid,now,l.getLatitude(),l.getLongitude(),speed,bearing,acc,alt,copy==null?0f:copy.grade,fs.rollDeg,fs.pitchDeg,fs.yawDeg,fs.gyroX,fs.gyroY,fs.gyroZ,fs.accelX,fs.accelY,fs.accelZ,fs.accelMagnitude,copy,limit,way));
    }
    private Curve copyCurve(Curve c){if(c==null)return null;Curve q=new Curve();q.valid=c.valid;q.type=c.type;q.dir=c.dir;q.distance=c.distance;q.recommended=c.recommended;q.grade=c.grade;q.confidence=c.confidence;q.radius=c.radius;q.label=c.label;q.roadId=c.roadId;q.source=c.source;return q;}
    public boolean isActive(){synchronized(lock){return active;}}
    public void shutdown(){stop();io.shutdownNow();}
}
