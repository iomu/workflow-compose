package dev.jomu.common
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import dev.jomu.workflow.Workflow
import dev.jomu.workflow.asStateFlow
import dev.jomu.workflow.renderChild
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val rendering by remember { HostWorkflow.asStateFlow(scope, Unit) {} }.collectAsState()

    MaterialTheme {
        Latency(rendering)
    }
}

@Composable
fun Latency(rendering: LatencyRendering) {
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
        Text(rendering.text)

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

data class LatencyRendering(
    val text: String = "",
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

        val count1 by context.renderChild(CountWorkflow, 1000)
        val count2 by context.renderChild(CountWorkflow, 10000)

        return LatencyRendering(
            "Count $count1 $count2",
            context.eventHandler {
                pressedAt.value = System.currentTimeMillis()
            },
            pressedAt.value,
        )
    }
}

object HostWorkflow : Workflow<Unit, Nothing, LatencyRendering>() {
    @Composable
    override fun render(context: RenderContext, props: Unit): LatencyRendering {
        val state = context.renderChild(LatencyWorkflow, props) {}
        return state.value
    }
}
