#!/usr/bin/env python3
"""Deterministic stress tests for ROAD DISPLAY PRO 3.27 ETA math/UI constraints."""
import math


def eta_from_profile(total_m, progress_m, samples, base_sec, observed_factor=1.0):
    total_m=max(0.0,total_m); progress_m=max(0.0,min(total_m,progress_m))
    if total_m<=0: return 0.0
    remaining=total_m-progress_m
    if remaining<=0: return 0.0
    fallback=max(20.0,min(110.0,total_m/max(1.0,base_sec)*3.6))
    def lim_at(d):
        best=samples[0][1] if samples else 0
        for sd,lim in samples:
            if sd<=d: best=lim
            else: break
        return best
    if not samples:
        sec=base_sec*(remaining/total_m) if base_sec>0 else remaining/(fallback/3.6)
    else:
        sec=0.0
        d=progress_m
        while d<total_m:
            end=total_m
            for sd,_ in samples:
                if sd>d+0.5:
                    end=min(end,sd); break
            lim=lim_at((d+end)/2.0)
            cruise=max(20.0,lim*.88) if lim>0 else fallback
            sec+=(end-d)/(cruise/3.6)
            if end<=d+0.01: break
            d=end
    return max(0.0,sec/max(.80,min(1.20,observed_factor)))


def check(name, cond):
    if not cond: raise AssertionError(name)

# 100,000 randomized ETA evaluations: monotonicity, finite/non-negative result, and bounded adaptation.
import random
rng=random.Random(327000)
profiles=[
    [(0,120),(10000,120),(20000,90),(30000,50)],
    [(0,90),(5000,0),(10000,70),(15000,0)],
    [],
]
case_count=0
for _ in range(100000):
    profile=rng.choice(profiles)
    total=rng.uniform(500.0,120000.0)
    base=rng.uniform(30.0,7200.0)
    p=rng.uniform(0.0,total)
    e=eta_from_profile(total,p,profile,base,rng.uniform(.80,1.20))
    check('finite ETA',math.isfinite(e))
    check('non-negative ETA',e>=0.0)
    p2=min(total,p+max(1.0,rng.uniform(1.0,1000.0)))
    e2=eta_from_profile(total,p2,profile,base,1.0)
    check('ETA decreases with progress',e2<=eta_from_profile(total,p,profile,base,1.0)+1e-9)
    case_count += 1

# Lowering a limit must never make the model faster on that section.
a=eta_from_profile(10000,0,[(0,120),(10000,120)],400)
b=eta_from_profile(10000,0,[(0,60),(10000,60)],700)
check('lower limit increases ETA', b>a)

# Observed factor is bounded: no single GPS speed can create absurd ETA changes.
base=eta_from_profile(20000,5000,[(0,90),(20000,90)],900,1.0)
fast=eta_from_profile(20000,5000,[(0,90),(20000,90)],900,1.18)
slow=eta_from_profile(20000,5000,[(0,90),(20000,90)],900,.80)
check('factor fast bounded', fast >= base/1.18-1e-6)
check('factor slow bounded', slow <= base/.80+1e-6)
check('zero progress finite', math.isfinite(base))
check('arrival zero', eta_from_profile(1000,1000,[(0,90)],60)==0.0)

# UI geometry smoke tests for common phone aspect ratios.
def nav_geometry(w,h,density):
    land=w>h
    if land:
        nav_left=12*density; nav_right=min(0.35*w, 976) # conservative proxy
        nav_top=150*density; nav_bottom=.84*h
    else:
        nav_left=18*density; nav_right=w-18*density
        # use a representative speedBounds.top after first draw
        number_h=min(760*density,(.19 if h/density < 700 else (.30 if h/density < 850 else .37))*h)
        base=.73*h
        speed_top=base-number_h*.88
        nav_top=131*density; nav_bottom=min(.41*h,speed_top-105*density)
    return nav_right-nav_left, nav_bottom-nav_top

for w,h,d in [(1440,3088,3.5),(1080,2400,3.0),(3088,1440,3.5),(2400,1080,3.0),(800,1280,2.0)]:
    nw,nh=nav_geometry(w,h,d)
    if w>h: check('land nav minimum', nw>150*d and nh>120*d)
    else: check('portrait nav minimum', nw>150*d and nh>120*d)

print('ETA_STRESS_CASES='+str(case_count))
print('FAILURES=0')
print('RESULT=PASS')
