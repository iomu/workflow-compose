package dev.jomu.workflow

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SkippingTest {
    @Test
    fun `skips workflow render if props haven't changed`() {
        runBlocking {
            val job = Job(coroutineContext[Job])
            val context = coroutineContext + job

            val scope = this + context

            val constant = RenderCountingWorkflow()
            val changing = RenderCountingWorkflow()

            val renderings = HostWorkflow(constant, changing).renderAsFlow(scope, Unit) {}

            renderings.test {
                var rendering = awaitItem()

                assertEquals(1, rendering.constant)
                assertEquals(1, rendering.changing)

                rendering.invalidate()
                rendering = awaitItem()

                assertEquals(1, rendering.constant)
                assertEquals(2, rendering.changing)

                rendering.invalidate()
                rendering = awaitItem()

                assertEquals(1, rendering.constant)
                assertEquals(3, rendering.changing)

                val events = cancelAndConsumeRemainingEvents()
                assertContentEquals(events, emptyList())
            }
            println("testing is over bro")
            scope.cancel()
        }
    }

    @Test
    fun `does not skip unstable workflows`() {
        runBlocking {
            val job = Job(coroutineContext[Job])
            val context = coroutineContext + job

            val scope = this + context

            val constant = UnstableRenderCountingWorkflow()
            val changing = RenderCountingWorkflow()

            val renderings = UnstableHostWorkflow(constant, changing).renderAsFlow(scope, Unit) {}

            renderings.test {
                var rendering = awaitItem()

                assertEquals(1, rendering.constant)
                assertEquals(1, rendering.changing)

                rendering.invalidate()
                rendering = awaitItem()

                assertEquals(2, rendering.constant)
                assertEquals(2, rendering.changing)

                rendering.invalidate()
                rendering = awaitItem()

                assertEquals(3, rendering.constant)
                assertEquals(3, rendering.changing)

                val events = cancelAndConsumeRemainingEvents()
                assertContentEquals(events, emptyList())
            }
            println("testing is over bro")
            scope.cancel()
        }
    }
}

data class CountRendering(val constant: Int, val changing: Int, val invalidate: () -> Unit)

class HostWorkflow(val constantInput: Workflow<String, Nothing, Int>, val changingInput: Workflow<String, Nothing, Int>) : Workflow<Unit, Nothing, CountRendering>() {
    @Composable
    override fun render(context: RenderContext, props: Unit): CountRendering {
        var count by remember { mutableStateOf(0) }
        val constant by context.renderChild(constantInput, "constant")
        val changing by context.renderChild(changingInput, "Count: $count")

        return CountRendering(constant, changing, context.eventHandler { count++ }).also { println(it) }
    }
}

class UnstableHostWorkflow(val constantInput: UnstableRenderCountingWorkflow, val changingInput: RenderCountingWorkflow) : Workflow<Unit, Nothing, CountRendering>() {
    @Composable
    override fun render(context: RenderContext, props: Unit): CountRendering {
        var count by remember { mutableStateOf(0) }
        val constant by context.renderChild(constantInput, "constant")
        val changing by context.renderChild(changingInput, "Count: $count")

        Snapshot.sendApplyNotifications()
        return CountRendering(constant, changing, context.eventHandler { count++ }).also { println(it) }
    }
}

class Counter(var value: Int)

open class RenderCountingWorkflow : Workflow<String, Nothing, Int>() {

    @Composable
    override fun render(context: RenderContext, props: String): Int {
        val count = remember { Counter(0) }

        count.value++

        return count.value
    }
}

class UnstableRenderCountingWorkflow(public var iAmUnstable: Int = 0) : Workflow<String, Nothing, Int>() {
    @Composable
    override fun render(context: RenderContext, props: String): Int {
        val count = remember { Counter(0) }

        count.value++

        return count.value + iAmUnstable
    }
}