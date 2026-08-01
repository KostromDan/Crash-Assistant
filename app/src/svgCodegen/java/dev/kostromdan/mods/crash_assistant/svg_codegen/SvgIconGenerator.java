package dev.kostromdan.mods.crash_assistant.svg_codegen;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts the fixed project SVG files into tiny Java2D painters at build time. */
public final class SvgIconGenerator {
    private SvgIconGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6 || (args.length - 2) % 2 != 0) {
            throw new IllegalArgumentException(
                    "Usage: <output-dir> <package> (<svg-file> <class-name>)+");
        }

        File packageDirectory = new File(args[0], args[1].replace('.', File.separatorChar));
        if (!packageDirectory.mkdirs() && !packageDirectory.isDirectory()) {
            throw new IllegalStateException("Cannot create " + packageDirectory);
        }

        for (int index = 2; index < args.length; index += 2) {
            SvgDocument svg = parse(new File(args[index]));
            File output = new File(packageDirectory, args[index + 1] + ".java");
            write(output, args[1], args[index + 1], svg, new File(args[index]).getName());
        }
    }

    private static SvgDocument parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(file);
        Element root = document.getDocumentElement();
        double[] viewBox = parseViewBox(root);
        Style rootStyle = Style.DEFAULT.derive(root);
        Map<String, Group> masks = parseMasks(root, rootStyle);
        Group drawing = parseContainer(root, rootStyle);
        if (drawing.children.isEmpty()) {
            throw new IllegalArgumentException(file + " contains no supported SVG shapes");
        }
        return new SvgDocument(viewBox, drawing, masks);
    }

    private static void write(File output, String packageName, String className,
                              SvgDocument svg, String sourceName) throws Exception {
        StringBuilder code = new StringBuilder(8192);
        code.append("package ").append(packageName).append(";\n\n")
                .append("import java.awt.AlphaComposite;\n")
                .append("import java.awt.BasicStroke;\n")
                .append("import java.awt.Color;\n")
                .append("import java.awt.Composite;\n")
                .append("import java.awt.Graphics2D;\n")
                .append("import java.awt.geom.Path2D;\n\n")
                .append("/** Generated from ").append(sourceName)
                .append(". Do not edit; change the SVG source instead. */\n")
                .append("final class ").append(className).append(" {\n")
                .append("    static final VectorIcon ICON = new VectorIcon(")
                .append(number(svg.viewBox[0])).append(", ")
                .append(number(svg.viewBox[1])).append(", ")
                .append(number(svg.viewBox[2])).append(", ")
                .append(number(svg.viewBox[3])).append(", ")
                .append(className).append("::paint);\n\n")
                .append("    private ").append(className).append("() {\n    }\n\n")
                .append("    private static void paint(Graphics2D graphics) {\n");

        Emitter emitter = new Emitter(code, svg.masks);
        emitter.emitGroup(svg.root, "        ");
        code.append("    }\n}\n");

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8)) {
            writer.write(code.toString());
        }
    }

    private static final class Emitter {
        private final StringBuilder code;
        private final Map<String, Group> masks;
        private int shapeIndex;
        private int compositeIndex;

        private Emitter(StringBuilder code, Map<String, Group> masks) {
            this.code = code;
            this.masks = masks;
        }

        private void emitGroup(Group group, String indent) {
            for (Drawable child : group.children) {
                if (child instanceof ShapeElement) {
                    emitShape((ShapeElement) child, indent, false);
                } else {
                    Group childGroup = (Group) child;
                    emitGroup(childGroup, indent);
                    if (childGroup.maskId != null) {
                        Group mask = masks.get(childGroup.maskId);
                        if (mask == null) {
                            throw new IllegalArgumentException("Unknown SVG mask: " + childGroup.maskId);
                        }
                        int id = compositeIndex++;
                        code.append(indent).append("Composite composite").append(id)
                                .append(" = graphics.getComposite();\n")
                                .append(indent).append("graphics.setComposite(AlphaComposite.Clear);\n");
                        emitDarkMaskShapes(mask, indent);
                        code.append(indent).append("graphics.setComposite(composite").append(id)
                                .append(");\n");
                    }
                }
            }
        }

        /** The project masks are white canvases with black vector knockout shapes. */
        private void emitDarkMaskShapes(Group group, String indent) {
            for (Drawable child : group.children) {
                if (child instanceof Group) {
                    emitDarkMaskShapes((Group) child, indent);
                } else {
                    ShapeElement shape = (ShapeElement) child;
                    boolean darkFill = shape.style.fill != null
                            && luminance(shape.style.fill) < 128;
                    boolean darkStroke = shape.style.stroke != null
                            && luminance(shape.style.stroke) < 128;
                    if (darkFill || darkStroke) {
                        emitShape(shape, indent, true);
                    }
                }
            }
        }

        private void emitShape(ShapeElement element, String indent, boolean erase) {
            int id = shapeIndex++;
            String path = "path" + id;
            PathIterator iterator = element.shape.getPathIterator(null);
            code.append(indent).append("Path2D.Double ").append(path)
                    .append(" = new Path2D.Double(")
                    .append(iterator.getWindingRule() == PathIterator.WIND_EVEN_ODD
                            ? "Path2D.WIND_EVEN_ODD" : "Path2D.WIND_NON_ZERO")
                    .append(");\n");
            double[] coordinates = new double[6];
            while (!iterator.isDone()) {
                int type = iterator.currentSegment(coordinates);
                code.append(indent).append(path).append('.');
                if (type == PathIterator.SEG_MOVETO) {
                    code.append("moveTo(").append(number(coordinates[0])).append(", ")
                            .append(number(coordinates[1])).append(");\n");
                } else if (type == PathIterator.SEG_LINETO) {
                    code.append("lineTo(").append(number(coordinates[0])).append(", ")
                            .append(number(coordinates[1])).append(");\n");
                } else if (type == PathIterator.SEG_QUADTO) {
                    code.append("quadTo(").append(number(coordinates[0])).append(", ")
                            .append(number(coordinates[1])).append(", ")
                            .append(number(coordinates[2])).append(", ")
                            .append(number(coordinates[3])).append(");\n");
                } else if (type == PathIterator.SEG_CUBICTO) {
                    code.append("curveTo(").append(number(coordinates[0])).append(", ")
                            .append(number(coordinates[1])).append(", ")
                            .append(number(coordinates[2])).append(", ")
                            .append(number(coordinates[3])).append(", ")
                            .append(number(coordinates[4])).append(", ")
                            .append(number(coordinates[5])).append(");\n");
                } else if (type == PathIterator.SEG_CLOSE) {
                    code.append("closePath();\n");
                }
                iterator.next();
            }

            Style style = element.style;
            if (style.fill != null && (!erase || luminance(style.fill) < 128)) {
                emitColor(style.fill, style.opacity * style.fillOpacity, indent);
                code.append(indent).append("graphics.fill(").append(path).append(");\n");
            }
            if (style.stroke != null && (!erase || luminance(style.stroke) < 128)) {
                emitColor(style.stroke, style.opacity * style.strokeOpacity, indent);
                code.append(indent).append("graphics.setStroke(new BasicStroke(")
                        .append(floatNumber(style.strokeWidth)).append(", ")
                        .append(capName(style.lineCap)).append(", ")
                        .append(joinName(style.lineJoin)).append("));\n")
                        .append(indent).append("graphics.draw(").append(path).append(");\n");
            }
        }

        private void emitColor(Color color, float opacity, String indent) {
            int alpha = Math.round(color.getAlpha() * opacity);
            code.append(indent).append("graphics.setColor(new Color(")
                    .append(color.getRed()).append(", ")
                    .append(color.getGreen()).append(", ")
                    .append(color.getBlue()).append(", ")
                    .append(alpha).append("));\n");
        }
    }

    private static int luminance(Color color) {
        return (color.getRed() * 2126 + color.getGreen() * 7152
                + color.getBlue() * 722 + 5000) / 10000;
    }

    private static String capName(int cap) {
        if (cap == BasicStroke.CAP_ROUND) return "BasicStroke.CAP_ROUND";
        if (cap == BasicStroke.CAP_SQUARE) return "BasicStroke.CAP_SQUARE";
        return "BasicStroke.CAP_BUTT";
    }

    private static String joinName(int join) {
        if (join == BasicStroke.JOIN_ROUND) return "BasicStroke.JOIN_ROUND";
        if (join == BasicStroke.JOIN_BEVEL) return "BasicStroke.JOIN_BEVEL";
        return "BasicStroke.JOIN_MITER";
    }

    private static String number(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value) + "d";
        return Double.toString(value) + "d";
    }

    private static String floatNumber(float value) {
        return Float.toString(value) + "f";
    }

    private static double[] parseViewBox(Element root) {
        String viewBox = root.getAttribute("viewBox").trim();
        if (!viewBox.isEmpty()) {
            String[] values = viewBox.split("[,\\s]+");
            if (values.length != 4) {
                throw new IllegalArgumentException("Invalid SVG viewBox: " + viewBox);
            }
            double[] parsed = new double[4];
            for (int index = 0; index < parsed.length; index++) {
                parsed[index] = Double.parseDouble(values[index]);
            }
            if (parsed[2] <= 0 || parsed[3] <= 0) {
                throw new IllegalArgumentException("SVG viewBox must have a positive size");
            }
            return parsed;
        }
        double width = parseLength(root.getAttribute("width"));
        double height = parseLength(root.getAttribute("height"));
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("SVG requires a positive viewBox or dimensions");
        }
        return new double[]{0, 0, width, height};
    }

    private static double parseLength(String value) {
        String trimmed = value.trim();
        int end = 0;
        while (end < trimmed.length()) {
            char character = trimmed.charAt(end);
            if (!(Character.isDigit(character) || character == '.'
                    || character == '+' || character == '-')) break;
            end++;
        }
        return end == 0 ? -1 : Double.parseDouble(trimmed.substring(0, end));
    }

    private static Map<String, Group> parseMasks(Element root, Style rootStyle) {
        Map<String, Group> masks = new HashMap<String, Group>();
        NodeList nodes = root.getElementsByTagName("mask");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element mask = (Element) nodes.item(index);
            String id = mask.getAttribute("id").trim();
            if (!id.isEmpty()) {
                masks.put(id, parseContainer(mask, rootStyle.derive(mask)));
            }
        }
        return masks;
    }

    private static Group parseContainer(Element parent, Style parentStyle) {
        List<Drawable> children = new ArrayList<Drawable>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (!(nodes.item(index) instanceof Element)) continue;
            Element child = (Element) nodes.item(index);
            String name = child.getTagName();
            if ("defs".equals(name) || "mask".equals(name)) continue;

            Style style = parentStyle.derive(child);
            if ("g".equals(name)) {
                Group group = parseContainer(child, style);
                children.add(new Group(group.children,
                        parseMaskId(attributeOrStyle(child, "mask"))));
            } else if ("path".equals(name)) {
                int winding = "evenodd".equalsIgnoreCase(attributeOrStyle(child, "fill-rule"))
                        ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
                children.add(new ShapeElement(
                        new PathDataParser(child.getAttribute("d"), winding).parse(), style));
            } else if ("circle".equals(name)) {
                double cx = Double.parseDouble(child.getAttribute("cx"));
                double cy = Double.parseDouble(child.getAttribute("cy"));
                double radius = Double.parseDouble(child.getAttribute("r"));
                children.add(new ShapeElement(new Ellipse2D.Double(
                        cx - radius, cy - radius, radius * 2d, radius * 2d), style));
            } else {
                throw new IllegalArgumentException("Unsupported SVG element: " + name);
            }
        }
        return new Group(children, null);
    }

    private static String parseMaskId(String value) {
        String mask = value.trim();
        if (mask.startsWith("url(#") && mask.endsWith(")")) {
            return mask.substring(5, mask.length() - 1);
        }
        return null;
    }

    private static String attributeOrStyle(Element element, String name) {
        String attribute = element.getAttribute(name).trim();
        if (!attribute.isEmpty()) return attribute;
        for (String declaration : element.getAttribute("style").split(";")) {
            int separator = declaration.indexOf(':');
            if (separator > 0
                    && name.equalsIgnoreCase(declaration.substring(0, separator).trim())) {
                return declaration.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private interface Drawable {
    }

    private static final class Group implements Drawable {
        private final List<Drawable> children;
        private final String maskId;

        private Group(List<Drawable> children, String maskId) {
            this.children = children;
            this.maskId = maskId;
        }
    }

    private static final class ShapeElement implements Drawable {
        private final Shape shape;
        private final Style style;

        private ShapeElement(Shape shape, Style style) {
            this.shape = shape;
            this.style = style;
        }
    }

    private static final class SvgDocument {
        private final double[] viewBox;
        private final Group root;
        private final Map<String, Group> masks;

        private SvgDocument(double[] viewBox, Group root, Map<String, Group> masks) {
            this.viewBox = viewBox;
            this.root = root;
            this.masks = masks;
        }
    }

    private static final class Style {
        private static final Style DEFAULT = new Style(
                Color.BLACK, null, 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, 1f, 1f);

        private final Color fill;
        private final Color stroke;
        private final float strokeWidth;
        private final int lineCap;
        private final int lineJoin;
        private final float opacity;
        private final float fillOpacity;
        private final float strokeOpacity;

        private Style(Color fill, Color stroke, float strokeWidth, int lineCap, int lineJoin,
                      float opacity, float fillOpacity, float strokeOpacity) {
            this.fill = fill;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
            this.lineCap = lineCap;
            this.lineJoin = lineJoin;
            this.opacity = opacity;
            this.fillOpacity = fillOpacity;
            this.strokeOpacity = strokeOpacity;
        }

        private Style derive(Element element) {
            return new Style(
                    colorProperty(element, "fill", fill),
                    colorProperty(element, "stroke", stroke),
                    floatProperty(element, "stroke-width", strokeWidth),
                    lineCapProperty(element, lineCap),
                    lineJoinProperty(element, lineJoin),
                    opacity * unitInterval(attributeOrStyle(element, "opacity")),
                    inheritedOpacity(element, "fill-opacity", fillOpacity),
                    inheritedOpacity(element, "stroke-opacity", strokeOpacity));
        }

        private static Color colorProperty(Element element, String name, Color inherited) {
            String value = attributeOrStyle(element, name);
            if (value.isEmpty()) return inherited;
            if ("none".equalsIgnoreCase(value)) return null;
            return parseColor(value);
        }

        private static float floatProperty(Element element, String name, float inherited) {
            String value = attributeOrStyle(element, name);
            return value.isEmpty() ? inherited : Float.parseFloat(value);
        }

        private static float inheritedOpacity(Element element, String name, float inherited) {
            String value = attributeOrStyle(element, name);
            return value.isEmpty() ? inherited : unitInterval(value);
        }

        private static int lineCapProperty(Element element, int inherited) {
            String value = attributeOrStyle(element, "stroke-linecap");
            if (value.isEmpty()) return inherited;
            if ("round".equalsIgnoreCase(value)) return BasicStroke.CAP_ROUND;
            if ("square".equalsIgnoreCase(value)) return BasicStroke.CAP_SQUARE;
            return BasicStroke.CAP_BUTT;
        }

        private static int lineJoinProperty(Element element, int inherited) {
            String value = attributeOrStyle(element, "stroke-linejoin");
            if (value.isEmpty()) return inherited;
            if ("round".equalsIgnoreCase(value)) return BasicStroke.JOIN_ROUND;
            if ("bevel".equalsIgnoreCase(value)) return BasicStroke.JOIN_BEVEL;
            return BasicStroke.JOIN_MITER;
        }
    }

    private static float unitInterval(String value) {
        if (value.isEmpty()) return 1f;
        return Math.max(0f, Math.min(1f, Float.parseFloat(value)));
    }

    private static Color parseColor(String value) {
        String color = value.trim();
        if (color.startsWith("#")) {
            String hex = color.substring(1);
            if (hex.length() == 3) {
                return new Color(
                        Integer.parseInt(hex.substring(0, 1), 16) * 17,
                        Integer.parseInt(hex.substring(1, 2), 16) * 17,
                        Integer.parseInt(hex.substring(2, 3), 16) * 17);
            }
            if (hex.length() == 6) return new Color(Integer.parseInt(hex, 16));
            if (hex.length() == 8) {
                long rgba = Long.parseLong(hex, 16);
                return new Color((int) (rgba >>> 24) & 0xff,
                        (int) (rgba >>> 16) & 0xff, (int) (rgba >>> 8) & 0xff,
                        (int) rgba & 0xff);
            }
        }
        if ("black".equalsIgnoreCase(color)) return Color.BLACK;
        if ("white".equalsIgnoreCase(color)) return Color.WHITE;
        if ("red".equalsIgnoreCase(color)) return Color.RED;
        throw new IllegalArgumentException("Unsupported SVG color: " + value);
    }

    private static final class PathDataParser {
        private final String data;
        private final Path2D.Double path;
        private int index;
        private char command;
        private char previousSegment;
        private double x;
        private double y;
        private double subpathX;
        private double subpathY;
        private double controlX;
        private double controlY;

        private PathDataParser(String data, int windingRule) {
            this.data = data;
            this.path = new Path2D.Double(windingRule);
        }

        private Path2D.Double parse() {
            while (hasMore()) {
                skipSeparators();
                if (!hasMore()) break;
                char next = data.charAt(index);
                if (Character.isLetter(next)) {
                    command = next;
                    index++;
                } else if (command == 0) {
                    throw error("Expected an SVG path command");
                }
                switch (command) {
                    case 'M': case 'm': move(); break;
                    case 'L': case 'l': line(); break;
                    case 'H': case 'h': horizontal(); break;
                    case 'V': case 'v': vertical(); break;
                    case 'C': case 'c': cubic(); break;
                    case 'S': case 's': smoothCubic(); break;
                    case 'Q': case 'q': quadratic(); break;
                    case 'T': case 't': smoothQuadratic(); break;
                    case 'Z': case 'z': close(); break;
                    default: throw error("Unsupported SVG path command: " + command);
                }
            }
            return path;
        }

        private void move() {
            boolean relative = command == 'm';
            boolean first = true;
            do {
                double newX = number(), newY = number();
                if (relative) { newX += x; newY += y; }
                if (first) {
                    path.moveTo(newX, newY);
                    subpathX = newX; subpathY = newY; first = false;
                } else path.lineTo(newX, newY);
                x = newX; y = newY; previousSegment = 'M';
            } while (hasNumber());
            command = relative ? 'l' : 'L';
        }

        private void line() {
            boolean relative = command == 'l';
            do {
                double newX = number(), newY = number();
                if (relative) { newX += x; newY += y; }
                path.lineTo(newX, newY);
                x = newX; y = newY; previousSegment = 'L';
            } while (hasNumber());
        }

        private void horizontal() {
            boolean relative = command == 'h';
            do {
                double newX = number();
                if (relative) newX += x;
                path.lineTo(newX, y); x = newX; previousSegment = 'L';
            } while (hasNumber());
        }

        private void vertical() {
            boolean relative = command == 'v';
            do {
                double newY = number();
                if (relative) newY += y;
                path.lineTo(x, newY); y = newY; previousSegment = 'L';
            } while (hasNumber());
        }

        private void cubic() {
            boolean relative = command == 'c';
            do {
                double firstX = number(), firstY = number();
                double secondX = number(), secondY = number();
                double newX = number(), newY = number();
                if (relative) {
                    firstX += x; firstY += y; secondX += x; secondY += y;
                    newX += x; newY += y;
                }
                path.curveTo(firstX, firstY, secondX, secondY, newX, newY);
                controlX = secondX; controlY = secondY;
                x = newX; y = newY; previousSegment = 'C';
            } while (hasNumber());
        }

        private void smoothCubic() {
            boolean relative = command == 's';
            do {
                double firstX = previousSegment == 'C' ? 2 * x - controlX : x;
                double firstY = previousSegment == 'C' ? 2 * y - controlY : y;
                double secondX = number(), secondY = number();
                double newX = number(), newY = number();
                if (relative) { secondX += x; secondY += y; newX += x; newY += y; }
                path.curveTo(firstX, firstY, secondX, secondY, newX, newY);
                controlX = secondX; controlY = secondY;
                x = newX; y = newY; previousSegment = 'C';
            } while (hasNumber());
        }

        private void quadratic() {
            boolean relative = command == 'q';
            do {
                double newControlX = number(), newControlY = number();
                double newX = number(), newY = number();
                if (relative) {
                    newControlX += x; newControlY += y; newX += x; newY += y;
                }
                path.quadTo(newControlX, newControlY, newX, newY);
                controlX = newControlX; controlY = newControlY;
                x = newX; y = newY; previousSegment = 'Q';
            } while (hasNumber());
        }

        private void smoothQuadratic() {
            boolean relative = command == 't';
            do {
                double newControlX = previousSegment == 'Q' ? 2 * x - controlX : x;
                double newControlY = previousSegment == 'Q' ? 2 * y - controlY : y;
                double newX = number(), newY = number();
                if (relative) { newX += x; newY += y; }
                path.quadTo(newControlX, newControlY, newX, newY);
                controlX = newControlX; controlY = newControlY;
                x = newX; y = newY; previousSegment = 'Q';
            } while (hasNumber());
        }

        private void close() {
            path.closePath(); x = subpathX; y = subpathY;
            previousSegment = 'Z'; command = 0;
        }

        private boolean hasNumber() {
            skipSeparators();
            return hasMore() && !Character.isLetter(data.charAt(index));
        }

        private double number() {
            skipSeparators();
            int start = index;
            if (hasMore() && (data.charAt(index) == '+' || data.charAt(index) == '-')) index++;
            while (hasMore() && Character.isDigit(data.charAt(index))) index++;
            if (hasMore() && data.charAt(index) == '.') {
                index++;
                while (hasMore() && Character.isDigit(data.charAt(index))) index++;
            }
            if (hasMore() && (data.charAt(index) == 'e' || data.charAt(index) == 'E')) {
                index++;
                if (hasMore() && (data.charAt(index) == '+' || data.charAt(index) == '-')) index++;
                while (hasMore() && Character.isDigit(data.charAt(index))) index++;
            }
            if (start == index) throw error("Expected a number");
            return Double.parseDouble(data.substring(start, index));
        }

        private void skipSeparators() {
            while (hasMore()) {
                char character = data.charAt(index);
                if (!Character.isWhitespace(character) && character != ',') break;
                index++;
            }
        }

        private boolean hasMore() {
            return index < data.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at path offset " + index);
        }
    }
}
