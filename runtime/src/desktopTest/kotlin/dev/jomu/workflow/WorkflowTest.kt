package dev.jomu.workflow

import androidx.compose.runtime.*
import app.cash.turbine.test
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Test {
    @Test
    fun `example test`() {
        runBlocking {
            val job = Job(coroutineContext[Job])
            val context = coroutineContext + job

            val scope = this + context
            val renderings = HelloWorkflow.renderAsFlow(scope, Unit) {}

            renderings.test {
                val rendering = awaitItem()
                assertEquals(rendering.title, "Hello")
                rendering.onClick()

                val rendering2 = awaitItem()
                assertEquals(rendering2.title, "Goodbye")
                rendering2.onClick()

                assertEquals(rendering.onClick, rendering2.onClick)

                assertEquals(awaitItem().title, "Hello")

                val events = cancelAndConsumeRemainingEvents()
                assertContentEquals(events, emptyList())
            }
            println("testing is over bro")
            scope.cancel()
        }
    }
}

data class HelloRendering(val title: String, val onClick: () -> Unit)

object HelloWorkflow : Workflow<Unit, Nothing, HelloRendering>() {
    @Composable
    override fun render(context: RenderContext, props: Unit): HelloRendering {
        var title by remember { mutableStateOf(true) }

        return HelloRendering(if (title) "Hello" else "Goodbye", onClick = context.eventHandler {
            println("calling back $title")
            title = !title
        }).also { println(it) }
    }
}
