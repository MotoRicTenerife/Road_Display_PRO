package com.riccardo.roaddisplay;

import android.location.Location;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Automatic trip recorder. All database writes are serialized on one worker. */
public final class TripRecorder {
    public interface Listener { void onTripStateChanged(boolean recording, long tripId); }

    private final TripDb db;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "trip-diary-io"); t.setDaemon(true); return t;
    });
    private final Object lock = new Object();
    private boolean recording;
    private long tripId = -1L;
    private int movingFixes;
    private long lastPointTime;
    private double lastLat, lastLon, lastAlt;
    private long startTime;
    private double distanceM;
    private double maxKmh;
    private double minAlt = Double.NaN, maxAlt = Double.NaN, elevationGain;
    private long idleSince;
    private int queuedPoints;

    public TripRecorder(TripDb database, Listener stateListener) { db=database; listener=stateListener; }

    public void accept(Location l) {
        if (l == null || !l.hasAccuracy() || l.getAccuracy() > 50f) return;
        final long now = l.getTime() > 0 ? l.getTime() : System.currentTimeMillis();
        final double lat=l.getLatitude(), lon=l.getLongitude();
        final double alt=l.hasAltitude() ? l.getAltitude() : 0.0;
        float speedKmh = l.hasSpeed() ? Math.max(0f,l.getSpeed()*3.6f) : 0f;
        synchronized(lock) {
            if (!recording) {
                if (speedKmh >= 8f) movingFixes++; else movingFixes=0;
                if (movingFixes >= 3) startLocked(now,lat,lon,alt);
                return;
            }
            double delta = 0.0;
            long dt = now-lastPointTime;
            if (lastPointTime > 0 && dt >= 1000L && dt <= 120000L) {
                float[] out=new float[1]; Location.distanceBetween(lastLat,lastLon,lat,lon,out); delta=out[0];
                if (delta >= 2.0 && delta <= 2000.0) {
                    distanceM += delta;
                    if (altitudeValid(alt) && altitudeValid(lastAlt)) {
                        double dz=alt-lastAlt;
                        if (dz > 1.5 && dz < 300.0) elevationGain += dz;
                        if (Double.isNaN(minAlt)) minAlt=maxAlt=alt; else { minAlt=Math.min(minAlt,alt); maxAlt=Math.max(maxAlt,alt); }
                    }
                } else if (delta > 2000.0) {
                    // GPS jump: preserve the trip but do not add the impossible segment.
                    delta=0.0;
                }
            }
            if (speedKmh > maxKmh && speedKmh <= 300f) maxKmh=speedKmh;
            if (speedKmh < 3f && delta < 15.0) {
                if (idleSince == 0L) idleSince=now;
                else if (now-idleSince >= 5L*60L*1000L) { stopLocked(now,lat,lon,alt); return; }
            } else idleSince=0L;
            if (dt >= 3000L || delta >= 10.0 || queuedPoints == 0) {
                lastPointTime=now; lastLat=lat; lastLon=lon; lastAlt=alt;
                queuedPoints++;
                final long id=tripId, t=now; final double a=lat,o=lon,z=alt;
                io.execute(() -> db.addPoint(id,t,a,o,z));
            }
            final long id=tripId, end=now; final double eLat=lat,eLon=lon,eAlt=alt;
            final double dist=distanceM, max=maxKmh, min=Double.isNaN(minAlt)?eAlt:minAlt, hi=Double.isNaN(maxAlt)?eAlt:maxAlt, gain=elevationGain;
            final long duration=Math.max(0L,end-startTime);
            final double avg=duration>0 ? dist/1000.0/(duration/3600000.0) : 0.0;
            if (queuedPoints >= 10) {
                queuedPoints=0;
                io.execute(() -> db.updateMetrics(id,end,eLat,eLon,dist,duration,avg,max,min,hi,gain));
            }
        }
    }

    private void startLocked(long now,double lat,double lon,double alt) {
        recording=true; movingFixes=0; startTime=now; lastPointTime=now; lastLat=lat; lastLon=lon; lastAlt=alt;
        distanceM=0; maxKmh=0; elevationGain=0; minAlt=alt; maxAlt=alt; idleSince=0; queuedPoints=0;
        final long id=db.beginTrip(now,lat,lon,alt); tripId=id;
        io.execute(() -> db.addPoint(id,now,lat,lon,alt));
        if(listener!=null) listener.onTripStateChanged(true,id);
    }

    private void stopLocked(long now,double lat,double lon,double alt) {
        if(!recording)return;
        recording=false; movingFixes=0; idleSince=0;
        final long id=tripId; final double dist=distanceM,max=maxKmh,min=Double.isNaN(minAlt)?alt:minAlt,hi=Double.isNaN(maxAlt)?alt:maxAlt,gain=elevationGain;
        final long duration=Math.max(0L,now-startTime); final double avg=duration>0?dist/1000.0/(duration/3600000.0):0.0;
        final double eLat=lat,eLon=lon,eAlt=alt;
        io.execute(() -> { db.addPoint(id,now,eLat,eLon,eAlt); db.updateMetrics(id,now,eLat,eLon,dist,duration,avg,max,min,hi,gain); });
        tripId=-1L; queuedPoints=0;
        if(listener!=null) listener.onTripStateChanged(false,id);
    }

    public void stopNow() {
        synchronized(lock) {
            if(!recording)return;
            stopLocked(lastPointTime>0?lastPointTime:System.currentTimeMillis(),lastLat,lastLon,lastAlt);
        }
    }

    public boolean isRecording(){ synchronized(lock){return recording;} }
    public long currentTripId(){ synchronized(lock){return tripId;} }

    public void shutdown(){
        synchronized(lock) {
            if (recording) stopLocked(lastPointTime>0?lastPointTime:System.currentTimeMillis(),lastLat,lastLon,lastAlt);
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
    }
    private boolean altitudeValid(double a){return !Double.isNaN(a)&&!Double.isInfinite(a)&&a>-500&&a<10000;}
}
