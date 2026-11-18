package in.mahametro.competency.questions;

import java.io.StringReader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Turns question text into safe HTML. Ordinary text is escaped. An &lt;svg&gt;...&lt;/svg&gt; block written inside
 * the text is drawn as a picture at that place, after everything that could run code (scripts, event
 * handlers, outside links) has been removed. A block that is not valid SVG is shown as plain text.
 */
public final class QuestionTextRenderer {

    private static final Pattern SVG_BLOCK =
            Pattern.compile("<svg\\b.*?</svg\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
            "svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon", "text", "tspan",
            "defs", "marker", "lineargradient", "radialgradient", "stop", "title", "desc", "clippath",
            "pattern", "mask", "symbol", "use");

    private QuestionTextRenderer() {
    }

    public static String toHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Matcher matcher = SVG_BLOCK.matcher(text);
        int last = 0;
        while (matcher.find()) {
            out.append(escapeText(text.substring(last, matcher.start()), true));
            String svg = sanitizeSvg(matcher.group());
            out.append(svg != null ? svg : escapeText(matcher.group(), true));
            last = matcher.end();
        }
        out.append(escapeText(text.substring(last), true));
        return out.toString();
    }

    /** Returns the cleaned SVG, or null if the block cannot be read as SVG. */
    static String sanitizeSvg(String svg) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new InputSource(new StringReader(svg))).getDocumentElement();
            if (!"svg".equalsIgnoreCase(root.getTagName())) {
                return null;
            }
            StringBuilder out = new StringBuilder();
            write(root, out);
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(Element element, StringBuilder out) {
        String name = element.getTagName();
        if (!ALLOWED_ELEMENTS.contains(name.toLowerCase())) {
            return;
        }
        out.append('<').append(name);
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (isSafeAttribute(attribute.getNodeName(), attribute.getNodeValue())) {
                out.append(' ').append(attribute.getNodeName()).append("=\"")
                        .append(escapeText(attribute.getNodeValue(), false)).append('"');
            }
        }
        out.append('>');
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                write((Element) child, out);
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                out.append(escapeText(child.getNodeValue(), false));
            }
        }
        out.append("</").append(name).append('>');
    }

    private static boolean isSafeAttribute(String name, String value) {
        String n = name.toLowerCase();
        String v = value == null ? "" : value.toLowerCase().replaceAll("\\s+", "");
        if (n.startsWith("on")) {
            return false;
        }
        if ((n.equals("href") || n.endsWith(":href")) && !v.startsWith("#")) {
            return false;
        }
        if (v.contains("javascript:") || v.contains("expression(") || v.contains("@import")) {
            return false;
        }
        if (v.contains("url(") && !v.contains("url(#")) {
            return false;
        }
        return true;
    }

    private static String escapeText(String text, boolean lineBreaks) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
        if (lineBreaks) {
            escaped = escaped.replace("\r\n", "\n").replace("\n", "<br>");
        }
        return escaped;
    }
}
