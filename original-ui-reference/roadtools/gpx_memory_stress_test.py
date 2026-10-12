from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java').read_text()
curve=(root/'app/src/main/java/com/riccardo/roaddisplay/Curve.java').read_text()
assert 'showGpxManager' in main
assert 'REQ_IMPORT_GPX' in main and 'REQ_EXPORT_GPX' in main
assert 'exportTripToUri' in main and 'copyGpxFileToUri' in main
assert 'getAvailableBytes' in main and 'clearMapCache' in main
assert 'clearTileMemoryCache' in main
assert '96 * 1024 * 1024' in main
assert '30L * 86400000L' in main
assert 'releaseTiles();' in main
assert 'onTrimMemory(int level)' in main
assert 'TRIM_MEMORY_RUNNING_LOW' in main
assert 'if (view != null) view.releaseTiles();' in main
assert not re.search(r'\bDEMO\b|demo\(', main)
assert not re.search(r'\bDEMO\b|demo\(', curve)
# Basic delimiter sanity after stripping strings/comments.
s=re.sub(r'"(?:\\.|[^"\\])*"','""',main,flags=re.S)
s=re.sub(r'//[^\n]*|/\*.*?\*/','',s,flags=re.S)
stack=[]; pairs={')':'(',']':'[','}':'{'}
for ch in s:
    if ch in '([{': stack.append(ch)
    elif ch in ')]}':
        assert stack and stack.pop()==pairs[ch]
assert not stack
print('GPX_MEMORY_STRESS_STATIC_PASS')
