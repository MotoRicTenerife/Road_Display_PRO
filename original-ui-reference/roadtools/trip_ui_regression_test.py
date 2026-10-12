#!/usr/bin/env python3
"""Static regression checks for ROAD DISPLAY PRO trip-detail navigation UI."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/riccardo/roaddisplay/MainActivity.java"
s = JAVA.read_text(encoding="utf-8")


# Diary header must have only one RIDE action: the functional tool-row button.
assert 'void drawTrips(Canvas c)' in s
trips_src = s[s.find('void drawTrips(Canvas c)'):s.find('void openTripDay(String key)')]
assert trips_src.count('txt(c,"← RIDE"') == 1
assert 'tripsCloseBounds.set(w-dp(104),dp(8),w-dp(8),dp(50))' not in trips_src

# Portrait must expose three distinct bottom actions.
assert 'tripDetailListBounds.set(list); tripDetailHomeBounds.set(home); tripDetailDeleteBounds.set(del);' in s
assert 'txt(c,"ELIMINA",del.centerX(),del.centerY()+dp(5),12,0xFFFF7777,Paint.Align.CENTER);' in s
assert 'tripDetailDeleteBounds.setEmpty();' not in s

# Landscape must retain the delete action.
assert 'RectF del=new RectF(w-dp(150),h-dp(56),w-dp(10),h-dp(12));' in s

# List and GPX export actions must be distinct.
list_block = re.search(r'else if\(tripDetailListBounds\.contains\(x,y\)\)\{(.*?)\n\s*\}', s, re.S)
home_block = re.search(r'else if\(tripDetailHomeBounds\.contains\(x,y\)\)\{(.*?)\n\s*\}', s, re.S)
assert list_block and home_block
lb, hb = list_block.group(1), home_block.group(1)
assert 'tripsScreen=true' in lb and 'tripsScroll=0f' in lb
assert 'exportSelectedTripGpx();' in hb

# Delete action remains wired to the confirmation/delete path.
assert 'else if(tripDetailDeleteBounds.contains(x,y)) deleteSelectedTrip();' in s
assert 'setTitle("ELIMINARE IL VIAGGIO?")' in s

print("TRIP UI REGRESSION: PASS")
