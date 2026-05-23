package sk.ainet.app.notebook.display

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Contract tests for the Graphviz cell renderer.
 *
 * The scaffold branch can't render real graphs yet — the wasm artifact lands
 * in the follow-up PR. These tests pin the *contract* (value-class shape,
 * extension functions, exception types) so the follow-up can swap in the real
 * renderer body without changing anything user-facing.
 *
 * Once the wasm is bundled, [renders_a_simple_graph] flips from
 * "throws GraphvizNotBundledException" to "returns SVG and asserts shape".
 */
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
    fun renderDot_throws_GraphvizNotBundledException_until_wasm_is_bundled() {
        // The whole point of the scaffold branch: the contract resolves, the
        // call site compiles, the dependency wiring is real, but the wasm
        // payload hasn't been built yet. This test flips polarity in the
        // follow-up PR — at that point it asserts "returns SVG" instead of
        // "throws". If you're reading this because the test failed, check
        // whether you forgot to drop graphviz.wasm into resources/wasm/ or
        // whether GraphvizWasm.kt still has TODO bodies.
        val ex = assertFailsWith<GraphvizNotBundledException> {
            renderDot(Dot("digraph G { A -> B }"))
        }
        // The thrown message has to point readers at the follow-up so a fresh
        // contributor stumbling on it knows what they're looking at.
        assertContains(ex.message ?: "", "graphviz-wasm-followup")
    }

    @Test
    fun renderDot_string_overload_throws_the_same_way() {
        // Exercises that the convenience overload (`renderDot(String)`) is
        // wired through the same path as `renderDot(Dot)` and not a separate
        // shortcut that bypasses GraphvizWasm.
        assertFailsWith<GraphvizNotBundledException> {
            renderDot("digraph G { A }")
        }
    }

    @Test
    fun GraphvizException_carries_source_and_engine_for_debugging() {
        // We don't throw this in the scaffold, but the type itself has to
        // expose source + engine — otherwise notebook authors have to scrape
        // kernel logs to figure out which cell failed. Pin the constructor
        // shape so refactors can't quietly drop the fields.
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
