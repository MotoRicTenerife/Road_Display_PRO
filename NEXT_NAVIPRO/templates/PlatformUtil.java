package net.osmand;

import net.osmand.map.OsmandRegions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/** Android-compatible PlatformUtil shim for the OsmAnd Java routing library. */
public final class PlatformUtil {
    private static volatile OsmandRegions regions;
    private PlatformUtil() { }
    public static Log getLog(Class<?> type) { return LogFactory.getLog(type); }
    public static void setOsmandRegions(OsmandRegions value) { regions = value; }
    public static OsmandRegions getOsmandRegions() throws IOException {
        if (regions == null) regions = new OsmandRegions(null);
        return regions;
    }
    public static XmlPullParser newXMLPullParser() throws XmlPullParserException {
        org.kxml2.io.KXmlParser parser = new org.kxml2.io.KXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        return parser;
    }
    public static XmlSerializer newSerializer() {
        return new org.kxml2.io.KXmlSerializer();
    }
}
