package com.pfe.coverage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Converts KMZ or KML files to a valid GeoJSON FeatureCollection string.
 * Uses only JDK built-in XML and ZIP APIs — no extra dependencies required.
 */
@Service
public class CoverageKmlConversionService {

    private final ObjectMapper objectMapper;

    public CoverageKmlConversionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Entry point: accepts a .kmz or .kml file and returns a GeoJSON string.
     */
    public String convert(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        validateFileSize(file);

        String lower = filename.toLowerCase();
        byte[] kmlBytes;

        if (lower.endsWith(".kmz")) {
            kmlBytes = extractKmlFromKmz(file.getInputStream());
        } else if (lower.endsWith(".kml")) {
            kmlBytes = file.getBytes();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type '" + filename + "'. Only .kmz and .kml files are accepted.");
        }

        return parseKmlToGeoJson(kmlBytes);
    }

    // ── KMZ extraction ────────────────────────────────────────────────────────

    private byte[] extractKmlFromKmz(InputStream kmzStream) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(kmzStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Prevent path traversal: reject entries that escape the archive root
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    zis.closeEntry();
                    continue;
                }

                if (name.toLowerCase().endsWith(".kml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = zis.read(buf)) != -1) {
                        baos.write(buf, 0, read);
                    }
                    return baos.toByteArray();
                }
                zis.closeEntry();
            }
        }
        throw new IllegalArgumentException("No .kml file found inside the KMZ archive.");
    }

    // ── KML XML → GeoJSON ────────────────────────────────────────────────────

    private String parseKmlToGeoJson(byte[] kmlBytes) throws Exception {
        Document doc = buildXmlDocument(kmlBytes);
        doc.getDocumentElement().normalize();

        NodeList placemarks = doc.getElementsByTagName("Placemark");
        NodeList groundOverlays = doc.getElementsByTagName("GroundOverlay");

        // GroundOverlay-only files carry raster imagery, not polygon geometry
        if (groundOverlays.getLength() > 0 && placemarks.getLength() == 0) {
            throw new IllegalArgumentException(
                    "This KMZ contains raster/overlay data only and cannot be used for " +
                    "point-in-polygon comparison yet. Please provide a vector KML/KMZ.");
        }

        List<Map<String, Object>> features = new ArrayList<>();

        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            Map<String, Object> properties = extractProperties(placemark);

            for (Map<String, Object> geometry : extractGeometries(placemark)) {
                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("type", "Feature");
                feature.put("properties", properties);
                feature.put("geometry", geometry);
                features.add(feature);
            }
        }

        if (features.isEmpty()) {
            throw new IllegalArgumentException(
                    "No recognizable geometry found in the KML file. " +
                    "Supported types: Polygon, MultiGeometry, LineString, Point.");
        }

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return objectMapper.writeValueAsString(fc);
    }

    private Document buildXmlDocument(byte[] kmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(kmlBytes));
    }

    // ── Geometry extraction ───────────────────────────────────────────────────

    private Map<String, Object> extractProperties(Element placemark) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", firstChildText(placemark, "name"));
        props.put("description", firstChildText(placemark, "description"));
        return props;
    }

    private List<Map<String, Object>> extractGeometries(Element placemark) {
        List<Map<String, Object>> result = new ArrayList<>();

        NodeList polygons = placemark.getElementsByTagName("Polygon");
        for (int i = 0; i < polygons.getLength(); i++) {
            Map<String, Object> g = parsePolygon((Element) polygons.item(i));
            if (g != null) result.add(g);
        }

        NodeList lines = placemark.getElementsByTagName("LineString");
        for (int i = 0; i < lines.getLength(); i++) {
            Map<String, Object> g = parseLineString((Element) lines.item(i));
            if (g != null) result.add(g);
        }

        NodeList points = placemark.getElementsByTagName("Point");
        for (int i = 0; i < points.getLength(); i++) {
            Map<String, Object> g = parsePoint((Element) points.item(i));
            if (g != null) result.add(g);
        }

        return result;
    }

    private Map<String, Object> parsePolygon(Element polygon) {
        NodeList outer = polygon.getElementsByTagName("outerBoundaryIs");
        if (outer.getLength() == 0) return null;

        List<List<Double>> outerRing = extractRing((Element) outer.item(0));
        if (outerRing == null || outerRing.isEmpty()) return null;

        List<List<List<Double>>> rings = new ArrayList<>();
        rings.add(outerRing);

        NodeList inner = polygon.getElementsByTagName("innerBoundaryIs");
        for (int i = 0; i < inner.getLength(); i++) {
            List<List<Double>> hole = extractRing((Element) inner.item(i));
            if (hole != null && !hole.isEmpty()) rings.add(hole);
        }

        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "Polygon");
        geo.put("coordinates", rings);
        return geo;
    }

    private List<List<Double>> extractRing(Element boundary) {
        NodeList coords = boundary.getElementsByTagName("coordinates");
        if (coords.getLength() == 0) return null;
        return parseCoordinates(coords.item(0).getTextContent());
    }

    private Map<String, Object> parseLineString(Element lineString) {
        NodeList coords = lineString.getElementsByTagName("coordinates");
        if (coords.getLength() == 0) return null;
        List<List<Double>> pts = parseCoordinates(coords.item(0).getTextContent());
        if (pts == null || pts.isEmpty()) return null;

        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "LineString");
        geo.put("coordinates", pts);
        return geo;
    }

    private Map<String, Object> parsePoint(Element point) {
        NodeList coords = point.getElementsByTagName("coordinates");
        if (coords.getLength() == 0) return null;
        List<List<Double>> pts = parseCoordinates(coords.item(0).getTextContent().trim());
        if (pts == null || pts.isEmpty()) return null;

        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "Point");
        // A GeoJSON Point coordinate is a single position, not an array of positions
        geo.put("coordinates", pts.get(0));
        return geo;
    }

    /**
     * Parses a KML coordinates string (space-separated "lon,lat[,alt]" tuples)
     * into a list of [longitude, latitude] pairs (GeoJSON order).
     */
    private List<List<Double>> parseCoordinates(String coordText) {
        if (coordText == null || coordText.isBlank()) return Collections.emptyList();

        List<List<Double>> result = new ArrayList<>();
        String[] tuples = coordText.trim().split("\\s+");

        for (String tuple : tuples) {
            if (tuple.isBlank()) continue;
            String[] parts = tuple.split(",");
            if (parts.length < 2) continue;
            try {
                double lon = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                // altitude (parts[2]) is discarded — not needed for 2D analysis
                result.add(Arrays.asList(lon, lat));
            } catch (NumberFormatException ignored) {
                // Skip malformed coordinate tuples
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String firstChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        Node n = nodes.item(0);
        return n != null ? n.getTextContent() : null;
    }

    private void validateFileSize(MultipartFile file) {
        long maxBytes = 100L * 1024 * 1024; // 100 MB
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File size exceeds the 100 MB limit.");
        }
    }
}
