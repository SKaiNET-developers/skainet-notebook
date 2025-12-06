package sk.ainet.app.notebook.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class DisplayOptionsTest {
    @Test
    fun `defaults are sensible`() {
        val opts = DisplayOptions()
        assertNull(opts.width)
        assertNull(opts.height)
        assertNull(opts.alt)
        assertFalse(opts.border)
        assertNull(opts.cssClass)
    }

    @Test
    fun `can set fields`() {
        val opts = DisplayOptions().apply {
            width = 320
            height = 200
            alt = "sample"
            border = true
            cssClass = "img-thumb"
        }

        assertEquals(320, opts.width)
        assertEquals(200, opts.height)
        assertEquals("sample", opts.alt)
        assertEquals(true, opts.border)
        assertEquals("img-thumb", opts.cssClass)
    }
}
