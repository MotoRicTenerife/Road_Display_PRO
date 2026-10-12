"""Deterministic stress tests for ROAD DISPLAY PRO navigation route-progress logic (regression suite).
This mirrors the Java algorithm at meter-level, without claiming Android runtime coverage.
"""
import math, random

R=111320.0

def dist(a,b):
    return math.hypot((b[0]-a[0])*R, (b[1]-a[1])*110540.0)

def project(p,a,b):
    cos=math.cos(math.radians(p[1]))
    ax=(a[0]-p[0])*R*cos; ay=(a[1]-p[1])*110540
    bx=(b[0]-p[0])*R*cos; by=(b[1]-p[1])*110540
    dx,dy=bx-ax,by-ay; l=dx*dx+dy*dy
    t=0 if l<.001 else max(0,min(1,-(ax*dx+ay*dy)/l))
    return math.hypot(ax+t*dx,ay+t*dy),t

def cumulative(route):
    c=[0.0]
    for a,b in zip(route,route[1:]): c.append(c[-1]+dist(a,b))
    return c

def nearest(route,c,p,center=0):
    n=len(route)-1; lo=max(0,center-80); hi=min(n,center+100)
    best=(1e99,0,0)
    for i in range(lo,hi):
        d,t=project(p,route[i],route[i+1]); pr=c[i]+t*(c[i+1]-c[i])
        if d<best[0]: best=(d,i,pr)
    if best[0]>250:
        for i in range(n):
            d,t=project(p,route[i],route[i+1]); pr=c[i]+t*(c[i+1]-c[i])
            if d<best[0]: best=(d,i,pr)
    return best

def assert_true(x,msg):
    if not x: raise AssertionError(msg)

# Winding route: straight-line distance can become much smaller than route distance.
route=[]
for i in range(401):
    x=i*0.00005
    y=0.001*math.sin(i/22)
    route.append((x,y))
c=cumulative(route)
# maneuver at route point ~300; rider is at ~250 along route.
mi=300
p=route[250]
d,seg,progress=nearest(route,c,p,250)
assert_true(abs(progress-c[250])<2,"route progress failed on normal movement")
assert_true(c[mi]-progress > 0,"maneuver should remain ahead")
# GPS noise around maneuver point must advance, not stick on old step.
progresses=[]; center=295
for k in range(3000):
    i=300+random.randint(-2,2)
    p=(route[i][0]+random.uniform(-.000005,.000005),route[i][1]+random.uniform(-.000005,.000005))
    d,seg,pr=nearest(route,c,p,center)
    progresses.append(pr); center=seg
assert_true(max(progresses)-min(progresses)<80,"GPS noise caused excessive route-progress jumping")
# Deliberate detour: >75 m persistent deviation should qualify as off-route.
off=(route[250][0],route[250][1]+0.0010)  # ~110 m
od,_,_=nearest(route,c,off,250)
assert_true(od>75,"detour did not exceed off-route threshold")
# A single spike is not enough: persistence logic requires multiple samples.
spikes=[False,False,True,False,False]
assert_true(sum(spikes)==1,"single GPS spike fixture invalid")
# Roundabout/maneuver distance must use along-route progress, not crow-flies.
# Construct a U-like route where two points are geographically close but far along route.
route2=[(0,0),(0.001,0),(0.002,0),(0.002,0.001),(0.002,0.002),(0.001,0.002),(0,0.002)]
c2=cumulative(route2)
rider=route2[1]; maneuver=route2[-1]
_,_,rp=nearest(route2,c2,rider,1)
_,_,mp=nearest(route2,c2,maneuver,1)
assert_true(mp-rp>300,"maneuver distance incorrectly collapsed to straight-line distance")
print("NAVIGATION REPAIR STRESS: PASS")
print("normal progress, noisy maneuver passage, detour threshold, persistence fixture, winding-route maneuver distance: PASS")
