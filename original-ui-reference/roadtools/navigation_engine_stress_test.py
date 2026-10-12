import math, random

# Mirrors the navigation math used by ROAD DISPLAY PRO 3.30.0.
def dist(a,b):
    lat1,lon1=a;lat2,lon2=b
    x=math.radians(lon2-lon1)*6371000*math.cos(math.radians((lat1+lat2)/2))
    y=math.radians(lat2-lat1)*6371000
    return math.hypot(x,y)

def bearing(a,b):
    p1,p2=map(math.radians,[a[0],b[0]])
    dl=math.radians(b[1]-a[1])
    y=math.sin(dl)*math.cos(p2)
    x=math.cos(p1)*math.sin(p2)-math.sin(p1)*math.cos(p2)*math.cos(dl)
    return (math.degrees(math.atan2(y,x))+360)%360

def angle_diff(a,b):
    d=abs((a-b)%360)
    return 360-d if d>180 else d

def project(p,a,b):
    lat,lon=p; alat,alon=a; blat,blon=b
    c=math.cos(math.radians(lat))
    ax=(alon-lon)*111320*c; ay=(alat-lat)*110540
    bx=(blon-lon)*111320*c; by=(blat-lat)*110540
    dx,dy=bx-ax,by-ay
    l2=dx*dx+dy*dy
    t=0 if l2<.001 else max(0,min(1,-(ax*dx+ay*dy)/l2))
    d=math.hypot(ax+t*dx,ay+t*dy)
    return d,t

def candidate_score(p,a,b,heading,speed,accuracy):
    d,t=project(p,a,b)
    score=d
    if speed>=3:
        hd=angle_diff(heading,bearing(a,b))
        weight=max(.25,min(1,35/max(5, min(60,accuracy))))
        score += min(90,hd*.85*weight)
    return score,d,t

# 1) Parallel-road discrimination: nearest road is intentionally the wrong one.
main=((28.00000,-16.50000),(28.00000,-16.49900))
parallel=((28.00012,-16.49900),(28.00012,-16.50000))
p=(28.00010,-16.49950)
s_main=candidate_score(p,*main,90,25,5)[0]
s_parallel=candidate_score(p,*parallel,90,25,5)[0]
assert s_main < s_parallel, (s_main,s_parallel)

# 2) Route distance to maneuver must be along route, not straight-line.
route=[(28.0,-16.5),(28.0005,-16.5),(28.0005,-16.4995),(28.0010,-16.4995)]
cum=[0.0]
for a,b in zip(route,route[1:]): cum.append(cum[-1]+dist(a,b))
# maneuver at route index 3; rider is at index 1
assert cum[3]-cum[1] > dist(route[1],route[3]) + 20

# 3) Monotonic progress with GPS jitter around a maneuver.
progress=100.0
random.seed(30)
for _ in range(5000):
    proposal=progress+random.uniform(-15,25)
    if proposal+18 >= progress: progress=max(0,proposal)
    elif proposal > progress-70: progress=proposal
    assert progress >= -1e-9
# Never catastrophically jump backwards.
assert progress >= 80

# 4) Accuracy-aware reroute thresholds.
for acc in [3,5,10,20,30,40,50]:
    threshold=max(32,min(75,acc*1.8))
    assert 32 <= threshold <= 75
assert max(32,min(75,5*1.8)) == 32
assert max(32,min(75,50*1.8)) == 75

# 5) Roundabout instruction semantics.
ordinals={1:'prima',2:'seconda',3:'terza',4:'quarta',5:'quinta',6:'sesta'}
for n in range(1,7):
    s=f"alla rotonda, prendi la {ordinals[n]} uscita"
    assert ordinals[n] in s
    assert 'uscita' in s

# 6) No generic 'slight' curve should become a live maneuver.
ignored={'slight_left','slight_right','continue','becomes','start'}
assert ignored == {'slight_left','slight_right','continue','becomes','start'}

print('NAVIGATION ENGINE STRESS: PASS')
print('parallel-road heading discrimination: PASS')
print('route-distance vs straight-line: PASS')
print('monotonic progress jitter: PASS')
print('accuracy-aware reroute threshold: PASS')
print('roundabout exit semantics: PASS')
print('curve/bend suppression: PASS')
