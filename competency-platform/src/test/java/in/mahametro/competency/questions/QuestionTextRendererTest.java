package in.mahametro.competency.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestionTextRendererTest {

    @Test
    void plainTextIsEscaped() {
        assertEquals("a &lt; b<br>next", QuestionTextRenderer.toHtml("a < b\nnext"));
        assertEquals("", QuestionTextRenderer.toHtml(null));
    }

    @Test
    void svgIsDrawnInPlaceAndCleaned() {
        String html = QuestionTextRenderer.toHtml("Look at this: <svg width=\"20\" height=\"20\" onload=\"alert(1)\">"
                + "<script>alert(2)</script><circle cx=\"10\" cy=\"10\" r=\"8\" fill=\"red\"/>"
                + "<a href=\"http://example.com\">x</a></svg> What is the shape?");
        assertTrue(html.startsWith("Look at this: <svg"));
        assertTrue(html.contains("<circle"));
        assertTrue(html.contains("fill=\"red\""));
        assertTrue(html.endsWith("</svg> What is the shape?"));
        assertFalse(html.contains("onload"));
        assertFalse(html.contains("script"));
        assertFalse(html.contains("example.com"));
    }

    @Test
    void brokenSvgIsShownAsText() {
        String html = QuestionTextRenderer.toHtml("<svg><g></svg>");
        assertTrue(html.startsWith("&lt;svg&gt;"));
    }
}
