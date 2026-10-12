from pathlib import Path

ROOT = Path(__file__).resolve().parents[0].parent
SRC = ROOT / 'app/src/main/java/com/riccardo/roaddisplay/MainActivity.java'
s = SRC.read_text(encoding='utf-8')

assert 'field.setText("CASA")' not in s
assert 'putString(PREF_HOME_LAT,Double.toString(lat))' in s
assert 'putString(PREF_HOME_LON,Double.toString(lon))' in s
assert 'String.format(Locale.US, "%.8f, %.8f", lat, lon)' in s
assert 'String.format(Locale.US,"%.8f, %.8f",lat,lon)' in s

# CASA must use the stored coordinate pair before generic parsing/geocoding.
start = s.index('double[] homeDestination=homeCoordsIfMatch(toQuery,savedHome,hp);')
end = s.index('if(destinationLocation==null) throw new Exception("Destinazione non trovata");', start)
block = s[start:end]
assert block.index('homeCoordsIfMatch') < block.index('parseCoordinateDestination')
assert block.index('homeCoordsIfMatch') < block.index('geocodeAddressPhoton')
assert 'new Location("saved-home")' in block

# CASA must not be reverse-geocoded or overwritten by an address later.
assert 'reverseGeocodeHomeLocation' not in s
assert 'PREF_HOME_ADDRESS,coordinates' in s

# Coordinate parser remains available for ordinary typed coordinate destinations.
assert 'parseCoordinateDestination' in s
assert 'latitude,longitude' in s

print('HOME_DESTINATION_REGRESSION: PASS')
