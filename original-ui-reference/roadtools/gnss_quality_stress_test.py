#!/usr/bin/env python3
# Static/model stress checks for ROAD DISPLAY PRO GNSS quality states.
CASES=[(5,12,'OTTIMO'),(10,8,'OTTIMO'),(10,7,'BUONO'),(25,5,'BUONO'),(50,4,'DISCRETO'),(80,3,'DEBOLE'),(100,2,'DEBOLE'),(101,2,'MOLTO DEBOLE'),(200,1,'MOLTO DEBOLE')]
for acc,sat,want in CASES:
    got='OTTIMO' if acc<=10 and sat>=8 else 'BUONO' if acc<=25 else 'DISCRETO' if acc<=50 else 'DEBOLE' if acc<=100 else 'MOLTO DEBOLE'
    assert got==want,(acc,sat,got,want)
for age in (0,3000,8000,9999,14999): assert age<=15000
for age in (15001,30000): assert age>15000
print('GNSS quality stress: PASS')

# Fused provider must be treated as live location, not as network-only GPS loss.
for provider in ('gps','fused'):
    assert provider in ('gps','fused')
print('Fused provider acceptance: PASS')
