import sk.ainet.lang.types.FP32
import sk.ainet.context.data
import sk.ainet.lang.tensor.dsl.*
import sk.ainet.lang.tensor.pprint
import kotlin.test.Test


class TensorTest {

    @Test
    fun tensor() {
        data {

            val vector = tensor<FP32, Float> {
                shape(5) {
                    ones()
                }
            }
            vector.pprint()

        }
    }

}