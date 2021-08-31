package dev.jomu.workflow

import androidx.compose.runtime.Composable
import app.cash.turbine.test
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PropsTest {
    @Test
    fun `reacts to prop changes`() {
        runBlocking {
            val job = Job(coroutineContext[Job])
            val context = coroutineContext + job

            val scope = this + context

            val props = MutableStateFlow("initial")

            val renderings = PropsMirrorWorkflow.renderAsFlow(scope, props) {}

            renderings.test {

                assertEquals("initial", awaitItem())

                props.value = "second"
                assertEquals("second", awaitItem())

                props.value = "third"
                assertEquals("third", awaitItem())


                val events = cancelAndConsumeRemainingEvents()
                assertContentEquals(events, emptyList())
            }
            println("testing is over bro")
            scope.cancel()
        }
    }
}

object PropsMirrorWorkflow : Workflow<String, Nothing, String>() {
    @Composable
    override fun render(context: RenderContext, props: String): String {
        return props
    }
}