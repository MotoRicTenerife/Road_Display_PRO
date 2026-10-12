package com.riccardo.roaddisplay;
import android.content.*;import android.database.sqlite.*;import android.database.*;import android.location.Location;import java.util.*;
public class RoadDb extends SQLiteOpenHelper{
 public RoadDb(Context c){super(c,"roads.db",null,8);}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE ways(id TEXT PRIMARY KEY,name TEXT,bbox TEXT,pts TEXT,updated INTEGER,maxspeed TEXT,maxspeed_forward TEXT,maxspeed_backward TEXT,highway TEXT,maxspeed_type TEXT,junction TEXT,oneway TEXT)");d.execSQL("CREATE INDEX idx_updated ON ways(updated)");d.execSQL("CREATE TABLE radars(id TEXT PRIMARY KEY,lat REAL,lon REAL,updated INTEGER)");d.execSQL("CREATE INDEX idx_radar_location ON radars(lat,lon)");d.execSQL("CREATE TABLE radar_sync(cell TEXT PRIMARY KEY,last_success INTEGER NOT NULL)");}
 public void onUpgrade(SQLiteDatabase d,int a,int b){if(a<2){try{d.execSQL("ALTER TABLE ways ADD COLUMN maxspeed TEXT");}catch(Exception ignored){}}if(a<3){d.execSQL("CREATE TABLE IF NOT EXISTS radars(id TEXT PRIMARY KEY,lat REAL,lon REAL,updated INTEGER)");d.execSQL("CREATE INDEX IF NOT EXISTS idx_radar_location ON radars(lat,lon)");}if(a<4){try{d.execSQL("ALTER TABLE ways ADD COLUMN maxspeed_forward TEXT");}catch(Exception ignored){}try{d.execSQL("ALTER TABLE ways ADD COLUMN maxspeed_backward TEXT");}catch(Exception ignored){}}if(a<5){try{d.execSQL("ALTER TABLE ways ADD COLUMN highway TEXT");}catch(Exception ignored){}try{d.execSQL("ALTER TABLE ways ADD COLUMN maxspeed_type TEXT");}catch(Exception ignored){}}if(a<6){try{d.execSQL("ALTER TABLE ways ADD COLUMN junction TEXT");}catch(Exception ignored){}}if(a<7){try{d.execSQL("ALTER TABLE ways ADD COLUMN oneway TEXT");}catch(Exception ignored){}}if(a<8){d.execSQL("CREATE TABLE IF NOT EXISTS radar_sync(cell TEXT PRIMARY KEY,last_success INTEGER NOT NULL)");}}
 public synchronized void put(String id,String name,String maxspeed,String maxspeedForward,String maxspeedBackward,String highway,String maxspeedType,String junction,String oneway,double minLat,double minLon,double maxLat,double maxLon,String pts){SQLiteDatabase d=getWritableDatabase();ContentValues v=new ContentValues();v.put("id",id);v.put("name",name);v.put("bbox",minLat+","+minLon+","+maxLat+","+maxLon);v.put("pts",pts);v.put("updated",System.currentTimeMillis());v.put("maxspeed",maxspeed);v.put("maxspeed_forward",maxspeedForward);v.put("maxspeed_backward",maxspeedBackward);v.put("highway",highway);v.put("maxspeed_type",maxspeedType);v.put("junction",junction);v.put("oneway",oneway);d.insertWithOnConflict("ways",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized ArrayList<RoadWay> near(double lat,double lon,double deg){ArrayList<RoadWay> a=new ArrayList<>();SQLiteDatabase d=getReadableDatabase();Cursor c=d.rawQuery("SELECT id,name,bbox,pts,maxspeed,maxspeed_forward,maxspeed_backward,highway,maxspeed_type,junction,oneway FROM ways",null);while(c.moveToNext()){String[] b=c.getString(2).split(",");try{double minLa=Double.parseDouble(b[0]),minLo=Double.parseDouble(b[1]),maxLa=Double.parseDouble(b[2]),maxLo=Double.parseDouble(b[3]);if(maxLa>=lat-deg&&minLa<=lat+deg&&maxLo>=lon-deg&&minLo<=lon+deg)a.add(new RoadWay(c.getString(0),c.getString(1),decode(c.getString(3)),c.isNull(4)?"":c.getString(4),c.isNull(5)?"":c.getString(5),c.isNull(6)?"":c.getString(6),c.isNull(7)?"":c.getString(7),c.isNull(8)?"":c.getString(8),c.isNull(9)?"":c.getString(9),c.isNull(10)?"":c.getString(10)));}catch(Exception e){}}c.close();return a;}
 static ArrayList<Geo> decode(String s){ArrayList<Geo>a=new ArrayList<>();for(String q:s.split(";")){String[]z=q.split(",");if(z.length==2)try{a.add(new Geo(Double.parseDouble(z[0]),Double.parseDouble(z[1])));}catch(Exception e){}}return a;}
 public synchronized void putRadar(String id,double lat,double lon){SQLiteDatabase d=getWritableDatabase();ContentValues v=new ContentValues();v.put("id",id);v.put("lat",lat);v.put("lon",lon);v.put("updated",System.currentTimeMillis());d.insertWithOnConflict("radars",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized void replaceRadarsNear(double lat,double lon,double radiusMeters,ArrayList<RadarPoint> incoming){
  if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(radiusMeters)||radiusMeters<=0||incoming==null)return;
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();Cursor c=null;
  try{
   double dLat=radiusMeters/110540.0, dLon=radiusMeters/(111320.0*Math.max(0.2,Math.cos(Math.toRadians(lat))));
   c=d.rawQuery("SELECT id,lat,lon FROM radars WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",new String[]{Double.toString(lat-dLat),Double.toString(lat+dLat),Double.toString(lon-dLon),Double.toString(lon+dLon)});
   ArrayList<String> remove=new ArrayList<>();float[] dist=new float[1];
   while(c.moveToNext()){Location.distanceBetween(lat,lon,c.getDouble(1),c.getDouble(2),dist);if(dist[0]<=radiusMeters)remove.add(c.getString(0));}
   c.close();c=null;
   for(String id:remove)d.delete("radars","id=?",new String[]{id});
   long now=System.currentTimeMillis();
   for(RadarPoint r:incoming){if(r==null||r.id==null||r.id.isEmpty()||!Double.isFinite(r.lat)||!Double.isFinite(r.lon)||Math.abs(r.lat)>90||Math.abs(r.lon)>180)continue;ContentValues v=new ContentValues();v.put("id",r.id);v.put("lat",r.lat);v.put("lon",r.lon);v.put("updated",now);d.insertWithOnConflict("radars",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
   // Track freshness by ~11 km geographic cells: one successful regional fetch must not
   // falsely mark the whole country as complete.
   double cellLatStep=0.05, cellLonStep=0.05;
   int minLat=(int)Math.floor((lat-radiusMeters/110540.0)/cellLatStep), maxLat=(int)Math.floor((lat+radiusMeters/110540.0)/cellLatStep);
   int minLon=(int)Math.floor((lon-radiusMeters/(111320.0*Math.max(0.2,Math.cos(Math.toRadians(lat)))))/cellLonStep), maxLon=(int)Math.floor((lon+radiusMeters/(111320.0*Math.max(0.2,Math.cos(Math.toRadians(lat)))))/cellLonStep);
   float[] cellDist=new float[1];
   for(int ilat=minLat;ilat<=maxLat;ilat++) for(int ilon=minLon;ilon<=maxLon;ilon++) {
    double clat=(ilat+0.5)*cellLatStep, clon=(ilon+0.5)*cellLonStep;
    Location.distanceBetween(lat,lon,clat,clon,cellDist);
    if(cellDist[0] + 4000.0 > radiusMeters) continue;
    ContentValues sync=new ContentValues();sync.put("cell",ilat+":"+ilon);sync.put("last_success",now);d.insertWithOnConflict("radar_sync",null,sync,SQLiteDatabase.CONFLICT_REPLACE);
   }
   d.setTransactionSuccessful();
  }catch(Exception ignored){}finally{if(c!=null)c.close();d.endTransaction();}
 }
 public synchronized long lastRadarSyncAt(double lat,double lon){SQLiteDatabase d=getReadableDatabase();Cursor c=null;try{String cell=(int)Math.floor(lat*20.0)+":"+(int)Math.floor(lon*20.0);c=d.rawQuery("SELECT last_success FROM radar_sync WHERE cell=?",new String[]{cell});return c.moveToFirst()?c.getLong(0):0L;}catch(Exception ignored){return 0L;}finally{if(c!=null)c.close();}}
 public synchronized boolean radarSyncDue(double lat,double lon,long now){long last=lastRadarSyncAt(lat,lon);return last<=0L||now-last>=24L*60L*60L*1000L;}
 public synchronized ArrayList<RadarPoint> nearRadars(double lat,double lon,double deg){ArrayList<RadarPoint>a=new ArrayList<>();SQLiteDatabase d=getReadableDatabase();Cursor c=d.rawQuery("SELECT id,lat,lon FROM radars WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",new String[]{Double.toString(lat-deg),Double.toString(lat+deg),Double.toString(lon-deg),Double.toString(lon+deg)});while(c.moveToNext())a.add(new RadarPoint(c.getString(0),c.getDouble(1),c.getDouble(2)));c.close();return a;}
}
class RoadWay{String id,name,maxspeed,maxspeedForward,maxspeedBackward,highway,maxspeedType,junction,oneway;ArrayList<Geo> pts;RoadWay(String i,String n,ArrayList<Geo>p,String m,String f,String b,String h,String mt,String j,String o){id=i;name=n;pts=p;maxspeed=m;maxspeedForward=f;maxspeedBackward=b;highway=h==null?"":h;maxspeedType=mt==null?"":mt;junction=j==null?"":j;oneway=o==null?"":o;}}
class Geo{double lat,lon;Geo(double a,double b){lat=a;lon=b;}}
class RadarPoint{String id;double lat,lon;RadarPoint(String i,double a,double b){id=i;lat=a;lon=b;}}
class RadarApproach{final RadarPoint point;final double distanceMeters;RadarApproach(RadarPoint p,double d){point=p;distanceMeters=d;}}
