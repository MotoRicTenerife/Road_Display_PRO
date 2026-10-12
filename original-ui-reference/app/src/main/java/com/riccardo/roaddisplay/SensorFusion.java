package com.riccardo.roaddisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

/**
 * Phone-to-motorcycle sensor fusion for ROAD DISPLAY PRO 3.28.
 *
 * The Android rotation-vector sensor supplies the gravity/absolute-orientation
 * correction, the gyroscope supplies the high-rate relative motion, the
 * accelerometer is used for stationary validation and gravity magnitude, and
 * GPS course can gently correct accumulated yaw drift while the motorcycle is
 * moving straight enough. No magnetic heading is used as a safety authority.
 */
public final class SensorFusion implements SensorEventListener {
    public interface Listener { void onFusionChanged(FusionSample sample); }

    public static final class FusionSample {
        public final long timestampNs;
        public final float gyroX, gyroY, gyroZ;
        public final float accelX, accelY, accelZ;
        public final float rollDeg, pitchDeg, yawDeg;
        public final float yawRateDegS, rollRateDegS, pitchRateDegS;
        public final float accelMagnitude;
        public final boolean calibrated;
        FusionSample(long ts, float gx, float gy, float gz, float ax, float ay, float az,
                     float roll, float pitch, float yaw, float yr, float rr, float pr,
                     float amag, boolean cal) {
            timestampNs=ts; gyroX=gx; gyroY=gy; gyroZ=gz; accelX=ax; accelY=ay; accelZ=az;
            rollDeg=roll; pitchDeg=pitch; yawDeg=yaw; yawRateDegS=yr; rollRateDegS=rr;
            pitchRateDegS=pr; accelMagnitude=amag; calibrated=cal;
        }
    }

    private static final String PREFS="road_display_sensor";
    private static final String KEY_MATRIX="mount_matrix";
    private static final String KEY_CAL_TIME="calibration_time";
    private static final float RAD_TO_DEG=57.2957795f;

    private final Context context;
    private final SensorManager sm;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Sensor rotationVector, gyro, accel, magnetic;
    private final float[] currentMatrix=new float[9];
    private final float[] baselineMatrix=new float[9];
    private final float[] relativeMatrix=new float[9];
    private final float[] accelValues=new float[3];
    private final float[] gyroValues=new float[3];
    private final float[] magneticValues=new float[3];
    private final float[] gyroQuaternion={1f,0f,0f,0f};
    private long lastGyroNs;
    private long lastRotationNs;
    private boolean haveRotation, haveAccel, haveGyro, haveMagnetic, calibrated;
    private float roll,pitch,yaw;
    private float accelMagnitude;
    private long lastGpsTimeMs;
    private float lastGpsCourseDeg;
    private float lastGpsSpeedKmh;
    private float lastGpsAccuracyM=Float.MAX_VALUE;

    public SensorFusion(Context c, Listener l){
        context=c.getApplicationContext(); listener=l; sm=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        loadCalibration();
        if(sm!=null){
            rotationVector=sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            gyro=sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accel=sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetic=sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
    }

    public void start(){
        if(sm==null)return;
        try{ if(rotationVector!=null)sm.registerListener(this,rotationVector,SensorManager.SENSOR_DELAY_GAME); }catch(Exception ignored){}
        try{ if(gyro!=null)sm.registerListener(this,gyro,SensorManager.SENSOR_DELAY_GAME); }catch(Exception ignored){}
        try{ if(accel!=null)sm.registerListener(this,accel,SensorManager.SENSOR_DELAY_GAME); }catch(Exception ignored){}
        if(rotationVector==null){try{if(magnetic!=null)sm.registerListener(this,magnetic,SensorManager.SENSOR_DELAY_GAME);}catch(Exception ignored){}}
    }
    public void stop(){if(sm!=null)sm.unregisterListener(this);}

    public boolean hasCalibration(){return calibrated;}
    public long calibrationTime(){return prefs.getLong(KEY_CAL_TIME,0L);}

    public void updateGps(Location l){
        if(l==null)return;
        if(l.hasSpeed()&&l.hasBearing()&&l.hasAccuracy()){
            float sp=Math.max(0f,l.getSpeed()*3.6f);
            if(sp>=8f&&l.getAccuracy()<=30f){lastGpsCourseDeg=normalize360(l.getBearing());lastGpsSpeedKmh=sp;lastGpsAccuracyM=l.getAccuracy();lastGpsTimeMs=System.currentTimeMillis();}
        }
    }

    /** Capture the current phone attitude as the fixed motorcycle mounting reference. */
    public boolean calibrateNow(){
        if(!haveRotation || !isStationaryEnough()) return false;
        System.arraycopy(currentMatrix,0,baselineMatrix,0,9);
        calibrated=true;
        saveCalibration();
        resetGyroQuaternion();
        emit();
        return true;
    }

    public boolean isStationaryEnough(){
        return haveAccel && Math.abs(accelMagnitude-9.80665f)<=1.6f &&
                (!haveGyro || vectorMagnitude(gyroValues)<0.18f);
    }

    private void saveCalibration(){
        SharedPreferences.Editor e=prefs.edit();
        StringBuilder s=new StringBuilder(); for(int i=0;i<9;i++){if(i>0)s.append(',');s.append(Float.toString(baselineMatrix[i]));}
        e.putString(KEY_MATRIX,s.toString()).putLong(KEY_CAL_TIME,System.currentTimeMillis()).apply();
    }
    private void loadCalibration(){
        String s=prefs.getString(KEY_MATRIX,"");
        String[] p=s.split(",");
        if(p.length!=9)return;
        try{for(int i=0;i<9;i++)baselineMatrix[i]=Float.parseFloat(p[i]);calibrated=true;}catch(Exception ignored){calibrated=false;}
    }

    @Override public void onSensorChanged(SensorEvent e){
        if(e==null||e.sensor==null||e.values==null)return;
        int type=e.sensor.getType();
        if(type==Sensor.TYPE_GYROSCOPE && e.values.length>=3){
            gyroValues[0]=e.values[0];gyroValues[1]=e.values[1];gyroValues[2]=e.values[2];haveGyro=true;
            if(lastGyroNs>0){float dt=(e.timestamp-lastGyroNs)*1e-9f;if(dt>0f&&dt<0.1f)integrateGyro(dt,e.values[0],e.values[1],e.values[2]);}
            lastGyroNs=e.timestamp;
        } else if(type==Sensor.TYPE_ACCELEROMETER && e.values.length>=3){
            System.arraycopy(e.values,0,accelValues,0,3);haveAccel=true;accelMagnitude=vectorMagnitude(accelValues);
        } else if(type==Sensor.TYPE_MAGNETIC_FIELD && e.values.length>=3){System.arraycopy(e.values,0,magneticValues,0,3);haveMagnetic=true;}
        if(type==Sensor.TYPE_ROTATION_VECTOR && e.values.length>=4){
            SensorManager.getRotationMatrixFromVector(currentMatrix,e.values);haveRotation=true;lastRotationNs=e.timestamp;
            if(!calibrated){System.arraycopy(currentMatrix,0,relativeMatrix,0,9);}
            correctGyroFromRotation();
            updateAngles();
            emit();
        } else if(type==Sensor.TYPE_GYROSCOPE){updateAngles();emit();}
    }

    private void correctGyroFromRotation(){
        if(!haveRotation)return;
        float[] fused=new float[9];
        if(calibrated) multiplyTransposeLeft(baselineMatrix,currentMatrix,fused); else System.arraycopy(currentMatrix,0,fused,0,9);
        float[] target={fused[0],fused[1],fused[2],fused[3],fused[4],fused[5],fused[6],fused[7],fused[8]};
        // Re-anchor the integrated gyro slowly. This keeps high-rate motion responsive
        // without allowing gyro bias to accumulate indefinitely.
        float[] targetQ=matrixToQuaternion(target);
        float a=0.08f;
        slerpInto(gyroQuaternion,targetQ,a);
        if(lastGpsTimeMs>0 && System.currentTimeMillis()-lastGpsTimeMs<1500L && lastGpsSpeedKmh>=15f){
            float gpsRel=normalize180(lastGpsCourseDeg-lastGpsCourseReference());
            float yawNow=quaternionYawDeg(gyroQuaternion);
            float err=normalize180(gpsRel-yawNow);
            float corr=Math.max(-2f,Math.min(2f,err*0.015f));
            multiplyYawCorrection(gyroQuaternion,corr);
        }
    }

    // The calibration reference contains the phone's absolute heading at capture;
    // for GPS correction we compare current GPS course with the calibrated heading.
    private float lastGpsCourseReference(){
        float[] o=new float[3];SensorManager.getOrientation(baselineMatrix,o);return normalize360(o[0]*RAD_TO_DEG);
    }

    private void updateAngles(){
        if(calibrated){
            roll=quaternionRollDeg(gyroQuaternion);pitch=quaternionPitchDeg(gyroQuaternion);yaw=quaternionYawDeg(gyroQuaternion);
        }else if(haveRotation){
            float[] o=new float[3];SensorManager.getOrientation(currentMatrix,o);yaw=o[0]*RAD_TO_DEG;pitch=o[1]*RAD_TO_DEG;roll=o[2]*RAD_TO_DEG;
        }
    }

    private void integrateGyro(float dt,float x,float y,float z){
        float angle=(float)Math.sqrt(x*x+y*y+z*z)*dt;
        if(angle<1e-6f)return;
        float ax=x,ay=y,az=z;float n=(float)Math.sqrt(ax*ax+ay*ay+az*az);if(n<1e-6f)return;
        ax/=n;ay/=n;az/=n;
        float h=angle*.5f,s=(float)Math.sin(h);
        float[] dq={(float)Math.cos(h),ax*s,ay*s,az*s};
        multiplyQuat(gyroQuaternion,dq,gyroQuaternion);
        normalizeQuat(gyroQuaternion);
    }
    private void resetGyroQuaternion(){gyroQuaternion[0]=1;gyroQuaternion[1]=gyroQuaternion[2]=gyroQuaternion[3]=0;lastGyroNs=0;}

    private void emit(){
        if(listener==null)return;
        float yr=gyroValues[2]*RAD_TO_DEG,rr=gyroValues[0]*RAD_TO_DEG,pr=gyroValues[1]*RAD_TO_DEG;
        listener.onFusionChanged(new FusionSample(System.nanoTime(),gyroValues[0],gyroValues[1],gyroValues[2],accelValues[0],accelValues[1],accelValues[2],roll,pitch,yaw,yr,rr,pr,accelMagnitude,calibrated));
    }

    private static void multiplyTransposeLeft(float[] a,float[] b,float[] out){
        // out = transpose(a) * b for 3x3 row-major matrices.
        for(int r=0;r<3;r++)for(int c=0;c<3;c++)out[r*3+c]=a[r]*b[c]+a[3+r]*b[3+c]+a[6+r]*b[6+c];
    }
    private static float[] matrixToQuaternion(float[] m){
        float w,x,y,z;float tr=m[0]+m[4]+m[8];
        if(tr>0){float s=(float)Math.sqrt(tr+1)*2;w=.25f*s;x=(m[7]-m[5])/s;y=(m[2]-m[6])/s;z=(m[3]-m[1])/s;}
        else if(m[0]>m[4]&&m[0]>m[8]){float s=(float)Math.sqrt(1+m[0]-m[4]-m[8])*2;w=(m[7]-m[5])/s;x=.25f*s;y=(m[1]+m[3])/s;z=(m[2]+m[6])/s;}
        else if(m[4]>m[8]){float s=(float)Math.sqrt(1+m[4]-m[0]-m[8])*2;w=(m[2]-m[6])/s;x=(m[1]+m[3])/s;y=.25f*s;z=(m[5]+m[7])/s;}
        else{float s=(float)Math.sqrt(1+m[8]-m[0]-m[4])*2;w=(m[3]-m[1])/s;x=(m[2]+m[6])/s;y=(m[5]+m[7])/s;z=.25f*s;}
        float[] q={w,x,y,z};normalizeQuat(q);return q;
    }
    private static void slerpInto(float[] q,float[] t,float a){
        float dot=q[0]*t[0]+q[1]*t[1]+q[2]*t[2]+q[3]*t[3];if(dot<0){dot=-dot;t=new float[]{-t[0],-t[1],-t[2],-t[3]};}
        if(dot>.9995f){for(int i=0;i<4;i++)q[i]+=a*(t[i]-q[i]);normalizeQuat(q);return;}
        double th=Math.acos(Math.max(-1,Math.min(1,dot)));double s=Math.sin(th);float a0=(float)(Math.sin((1-a)*th)/s),a1=(float)(Math.sin(a*th)/s);
        float w=a0*q[0]+a1*t[0],x=a0*q[1]+a1*t[1],y=a0*q[2]+a1*t[2],z=a0*q[3]+a1*t[3];q[0]=w;q[1]=x;q[2]=y;q[3]=z;normalizeQuat(q);
    }
    private static void multiplyQuat(float[] a,float[] b,float[] out){
        float w=a[0]*b[0]-a[1]*b[1]-a[2]*b[2]-a[3]*b[3],x=a[0]*b[1]+a[1]*b[0]+a[2]*b[3]-a[3]*b[2],y=a[0]*b[2]-a[1]*b[3]+a[2]*b[0]+a[3]*b[1],z=a[0]*b[3]+a[1]*b[2]-a[2]*b[1]+a[3]*b[0];out[0]=w;out[1]=x;out[2]=y;out[3]=z;
    }
    private static void normalizeQuat(float[] q){float n=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);if(n>1e-8f){for(int i=0;i<4;i++)q[i]/=n;}}
    private static float quaternionRollDeg(float[] q){return (float)Math.atan2(2*(q[0]*q[1]+q[2]*q[3]),1-2*(q[1]*q[1]+q[2]*q[2]))*RAD_TO_DEG;}
    private static float quaternionPitchDeg(float[] q){float s=2*(q[0]*q[2]-q[3]*q[1]);s=Math.max(-1f,Math.min(1f,s));return (float)Math.asin(s)*RAD_TO_DEG;}
    private static float quaternionYawDeg(float[] q){return normalize180((float)Math.atan2(2*(q[0]*q[3]+q[1]*q[2]),1-2*(q[2]*q[2]+q[3]*q[3]))*RAD_TO_DEG);}
    private static void multiplyYawCorrection(float[] q,float deg){double h=Math.toRadians(deg)*.5;float[] d={(float)Math.cos(h),0,0,(float)Math.sin(h)};multiplyQuat(q,d,q);normalizeQuat(q);}
    private static float vectorMagnitude(float[] v){return (float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);}
    private static float normalize360(float d){d%=360f;if(d<0)d+=360f;return d;}
    private static float normalize180(float d){d=normalize360(d);return d>180?d-360:d;}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
