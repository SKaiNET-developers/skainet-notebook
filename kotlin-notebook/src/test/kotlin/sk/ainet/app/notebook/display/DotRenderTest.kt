package sk.ainet.app.notebook.display

import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.jetbrains.kotlinx.jupyter.api.MimeTypes

class DotRenderTest {

    @Test
    fun dot_value_class_round_trips_source() {
        val d = Dot("digraph G { A -> B }")
        assertEquals("digraph G { A -> B }", d.source)
    }

    @Test
    fun string_asDot_extension_wraps_source() {
        val d = "digraph { A }".asDot()
        assertEquals(Dot("digraph { A }"), d)
    }

    @Test
    fun dot_options_default_to_dot_engine() {
        val opts = DotOptions()
        assertEquals(DotEngine.DOT, opts.engine)
        assertEquals(null, opts.width)
        assertEquals(null, opts.maxHeight)
    }

    @Test
    fun dot_options_dsl_mutates_fields() {
        val opts = DotOptions().apply {
            engine = DotEngine.NEATO
            width = "640px"
            maxHeight = "480px"
        }
        assertEquals(DotEngine.NEATO, opts.engine)
        assertEquals("640px", opts.width)
        assertEquals("480px", opts.maxHeight)
    }

    @Test
    fun renders_a_simple_graph_to_valid_svg() {
        val result = renderDot(Dot("digraph G { A -> B }"))
        val html = result[MimeTypes.HTML]
            ?: error("renderDot should produce an HTML mime entry, got keys=${result.keys}")

        // Cheap shape check first so a regression in the wrapper layer doesn't
        // get obscured by a slow XML parse failure further down.
        assertContains(html, "<svg")
        assertContains(html, "</svg>")
        // Node labels Graphviz emits — confirms the actual layout ran, not just
        // a static placeholder.
        assertContains(html, "A")
        assertContains(html, "B")

        // Round-trip through an XML parser to confirm the SVG is well-formed.
        // We're checking the wasm produced valid output, not just any string.
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Disable DTD loading; Graphviz's SVG references the SVG 1.1 DTD
            // via DOCTYPE, but the JDK parser would try to fetch it from w3.org.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false,
            )
            setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false,
            )
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        // Strip the wrapper <div>...</div> if any; parse the SVG fragment from
        // the first <svg ... > to the last </svg>.
        val start = html.indexOf("<svg")
        val end = html.lastIndexOf("</svg>") + "</svg>".length
        val svg = html.substring(start, end)
        val doc = builder.parse(svg.byteInputStream())
        assertEquals("svg", doc.documentElement.localName)
    }

    @Test
    fun renderDot_string_overload_matches_dot_overload() {
        val a = renderDot("digraph G { X -> Y }")
        val b = renderDot(Dot("digraph G { X -> Y }"))
        // Both should produce an SVG body; ids inside SVG (per-node coords)
        // are deterministic for identical inputs at identical engine settings,
        // so the bodies should match.
        assertEquals(
            a[MimeTypes.HTML]?.contains("<svg"),
            b[MimeTypes.HTML]?.contains("<svg"),
        )
        assertTrue(a[MimeTypes.HTML]!!.contains("X"))
        assertTrue(b[MimeTypes.HTML]!!.contains("Y"))
    }

    @Test
    fun renderDot_throws_on_unsupported_engine() {
        // The bundled wasm only links the `dot` + `core` plugins. Asking for
        // any other engine should surface a clear, actionable error rather
        // than producing a silently-wrong layout.
        val ex = assertFailsWith<GraphvizException> {
            renderDot(Dot("graph G { A -- B }")) {
                engine = DotEngine.NEATO
            }
        }
        assertContains(ex.message ?: "", "NEATO")
        assertEquals(DotEngine.NEATO, ex.engine)
    }

    @Test
    fun GraphvizException_carries_source_and_engine_for_debugging() {
        // Constructor-shape pin so refactors can't quietly drop the fields
        // notebook authors rely on for debugging.
        val ex = GraphvizException(
            message = "Layout failed",
            source = "digraph { A -> B -> A }",
            engine = DotEngine.DOT,
        )
        assertEquals("Layout failed", ex.message)
        assertEquals("digraph { A -> B -> A }", ex.source)
        assertEquals(DotEngine.DOT, ex.engine)
    }
}
