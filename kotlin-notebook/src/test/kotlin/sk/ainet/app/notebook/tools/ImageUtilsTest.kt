package sk.ainet.app.notebook.tools

import sk.ainet.context.data
import sk.ainet.lang.tensor.dsl.tensor
import sk.ainet.lang.types.FP32
import sk.ainet.lang.types.Int8
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageUtilsTest {

    private fun assertSolid(img: java.awt.image.BufferedImage, color: Color, message: String = "") {
        // Check a few pixels including corners and center
        val coords = listOf(
            0 to 0,
            img.width - 1 to 0,
            0 to img.height - 1,
            img.width - 1 to img.height - 1,
            img.width / 2 to img.height / 2
        )
        for ((x, y) in coords) {
            val argb = img.getRGB(x, y)
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = (argb) and 0xFF
            assertEquals(255, a, "$message alpha")
            assertEquals(color.red, r, "$message red")
            assertEquals(color.green, g, "$message green")
            assertEquals(color.blue, b, "$message blue")
        }
    }

    @Test
    fun toImage_HWC_red_green_blue_28x28_normalized() = data {
        val w = 28; val h = 28

        // Red (HWC) with normalized [0,1] values
        val red = tensor<FP32, Float> {
            shape(h, w, 3) {
                init { idx ->
                    val ch = idx[2]
                    if (ch == 0) 1.0f else 0.0f
                }
            }
        }

        val green = tensor<FP32, Float> {
            shape(h, w, 3) {
                init { idx -> if (idx[2] == 1) 1.0f else 0.0f }
            }
        }

        val blue = tensor<FP32, Float> {
            shape(h, w, 3) {
                init { idx -> if (idx[2] == 2) 1.0f else 0.0f }
            }
        }

        assertSolid(red.toImage(Layout.HWC), Color(255, 0, 0), "HWC red")
        assertSolid(green.toImage(Layout.HWC), Color(0, 255, 0), "HWC green")
        assertSolid(blue.toImage(Layout.HWC), Color(0, 0, 255), "HWC blue")
    }

    @Test
    fun toImage_CHW_red_green_blue_28x28_normalized() = data {
        val w = 28; val h = 28
        val red = tensor<FP32, Float> {
            shape(3, h, w) { // CHW
                init { idx -> if (idx[0] == 0) 1.0f else 0.0f }
            }
        }
        val green = tensor<FP32, Float> {
            shape(3, h, w) { init { idx -> if (idx[0] == 1) 1.0f else 0.0f } }
        }
        val blue = tensor<FP32, Float> {
            shape(3, h, w) { init { idx -> if (idx[0] == 2) 1.0f else 0.0f } }
        }

        assertSolid(red.toImage(Layout.CHW), Color(255, 0, 0), "CHW red")
        assertSolid(green.toImage(Layout.CHW), Color(0, 255, 0), "CHW green")
        assertSolid(blue.toImage(Layout.CHW), Color(0, 0, 255), "CHW blue")
    }

    @Test
    fun toImage_NHWC_first_item_used_colors_can_be_missing() = data {
        val w = 28; val h = 28

        // Batch size 3, but only the first is red; others may be any (even missing colors)
        val nhwc = tensor<FP32, Float> {
            shape(3, h, w, 3) { // N,H,W,C
                init { idx ->
                    val n = idx[0]; val ch = idx[3]
                    when (n) {
                        0 -> if (ch == 0) 1.0f else 0.0f // red
                        1 -> if (ch == 2) 1.0f else 0.0f // blue
                        else -> 0.0f // missing / black
                    }
                }
            }
        }

        val img = nhwc.toImage(Layout.NHWC)
        assertSolid(img, Color(255, 0, 0), "NHWC first batch item should be red")
    }

    @Test
    fun toImage_NCHW_first_item_used_colors_can_be_missing() = data {
        val w = 28; val h = 28

        // Batch size 2; first is green, second is red (to verify only first matters for toImage)
        val nchw = tensor<FP32, Float> {
            shape(2, 3, h, w) { // N,C,H,W
                init { idx ->
                    val n = idx[0]; val c = idx[1]
                    when (n) {
                        0 -> if (c == 1) 1.0f else 0.0f // green
                        else -> if (c == 0) 1.0f else 0.0f // red
                    }
                }
            }
        }

        val img = nchw.toImage(Layout.NCHW)
        assertSolid(img, Color(0, 255, 0), "NCHW first batch item should be green")
    }

    @Test
    fun toImage_HW_grayscale_supported() = data {
        val w = 28; val h = 28
        // Mid-gray, using non-normalized values (0..255 scale)
        val gray = tensor<FP32, Float> {
            shape(h, w) {
                init { _ -> 128.0f }
            }
        }
        val img = gray.toImage(Layout.HW)
        assertSolid(img, Color(128, 128, 128), "HW grayscale")
    }

    @Test
    fun toImage_CHW_grayscale_Int8_Byte_is_treated_as_unsigned() = data {
        val w = 16; val h = 12
        // Use Byte data (Int8 dtype). Values like 200 exceed signed Byte max (127),
        // so they become negative if interpreted as signed. Our converter must treat them as unsigned.
        val intensity = 200 // Provide as Int to satisfy DSL factory

        val chwGray = tensor<Int8, Int> {
            shape(1, h, w) { // C=1, H, W
                init { _ -> intensity }
            }
        }

        val img = chwGray.toImage(Layout.CHW)
        assertSolid(img, Color(200, 200, 200), "CHW grayscale from Int8/Byte should be unsigned")
    }
}
