package dev.jomu.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import dev.jomu.workflow.Workflow
import dev.jomu.workflow.renderChild
import dev.jomu.workflow.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun App(rendering: Any) {
    val viewEnvironment = remember {
        ViewEnvironment(
            mapOf(
                ViewRegistry to ViewRegistry(LatencyViewFactory, BackstackViewFactory, FunnyFactory)
            )
        )
    }

    MaterialTheme {
        WorkflowRendering(rendering, viewEnvironment)
    }
}

object LatencyViewFactory : ViewFactory<LatencyRendering> by composeViewFactory({ rendering, environment ->
    Latency(rendering)
})

@OptIn(ExperimentalAnimationApi::class)
object BackstackViewFactory : ViewFactory<BackstackScreen> by (composeViewFactory { rendering, environment ->
    val holder = rememberSaveableStateHolder()
    Column {
        AnimatedContent(rendering.top, transitionSpec = {
            val direction =
                if (initialState !in rendering.screens) AnimatedContentScope.SlideDirection.Right else AnimatedContentScope.SlideDirection.Left
            val x = targetState.value
            slideIntoContainer(direction, animationSpec = tween(90)) with slideOutOfContainer(
                direction,
                animationSpec = tween(90)
            )
        }) {
            key(it) {
                holder.SaveableStateProvider(currentCompositeKeyHash) {
                    WorkflowRendering(it.value, environment)
                }
            }
        }
        rendering.onBack?.let {
            TextButton(it) {
                Text("Go really back please")
            }
        }
    }
})

@Composable
fun Latency(rendering: LatencyRendering) {
    println("Outer latency composable")
    val times = remember { mutableStateListOf<Long>() }
    val renderedAt = remember(rendering.pressedAt) {
        rendering.pressedAt?.let {
            times.add(System.currentTimeMillis() - it)
        }
        System.currentTimeMillis()
    }

    val average = remember {
        derivedStateOf { times.average() }
    }
    Column {
        Text("Average: ${average.value}")
        Text(rendering.text.value)

        rendering.pressedAt?.let {
            Text("Rendered after ${renderedAt - it}ms")
        }
        Button(onClick = rendering.onClick) {
            Text("Press me")
        }
    }
}

object CountWorkflow : Workflow<Long, Nothing, Int>() {
    @Composable
    override fun render(context: RenderContext, props: Long): Int {
        println("${System.currentTimeMillis()} count workflow $props")
        var count by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (isActive) {
                count++
                delay(props)
            }
        }
        return count
    }
}

class LatencyRendering(
    val text: State<String>,
    val onClick: () -> Unit = {},
    val pressedAt: Long? = null,
)

object LatencyWorkflow : Workflow<Unit, Unit, LatencyRendering>() {
    @Composable
    override fun render(
        context: RenderContext,
        props: Unit,
    ): LatencyRendering {
        println("${System.currentTimeMillis()} latency workflow $props")
        val pressedAt = remember { mutableStateOf<Long?>(null) }

        val count1 = context.renderChild(CountWorkflow, 1000)
        val count2 = context.renderChild(CountWorkflow, 10000)

        return LatencyRendering(
            derivedStateOf {
                "Count ${count1.value} ${count2.value}"
            },
            context.eventHandler {
                pressedAt.value = System.currentTimeMillis()
            },
            pressedAt.value,
        )
    }
}

object HostWorkflow : Workflow<Unit, Nothing, Any>() {
    @Composable
    override fun render(context: RenderContext, props: Unit): Any {
        println("${System.currentTimeMillis()} host workflow $props")
        val state = context.renderChild(LatencyWorkflow, props) {}
        return BackstackScreen(listOf(state))
    }
}

@OptIn(ExperimentalStdlibApi::class)
data class BackstackScreen(
    private val bottom: List<State<Any>>,
    internal val next: State<BackstackScreen>? = null,
    val ownOnBack: (() -> Unit)? = null
) {
    val screens: List<State<Any>>
        get() = buildList {
            addAll(bottom)
            next?.let { addAll(it.value.screens) }
        }

    val top: State<Any>
        get() = next?.value?.top ?: bottom.last()

    val onBack: (() -> Unit)?
        get() = next?.value?.ownOnBack ?: ownOnBack
}