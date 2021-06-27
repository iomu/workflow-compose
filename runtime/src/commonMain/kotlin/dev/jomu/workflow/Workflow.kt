package dev.jomu.workflow

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


private class RenderContextImpl<in OutputT>(private val onOutput: (OutputT) -> Unit) :
    BaseRenderContext<OutputT> {
    private val handlerScope = object : EventHandlerScope<OutputT> {
        override fun setOutput(output: OutputT) {
            onOutput(output)
        }
    }

    @Composable
    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        handler: EventHandlerScope<OutputT>.(ChildOutputT) -> Unit
    ): State<ChildRenderingT> {
        return renderWorkflow(
            child,
            props
        ) {
            handlerScope.handler(it)
        }
    }

    @Composable
    override fun eventHandler(func: EventHandlerScope<OutputT>.() -> Unit): () -> Unit {
        val state = remember {
            Ref(func)
        }.apply {
            value = func
        }

        return remember {
            {
                val handler = state.value
                handlerScope.handler()
            }
        }
    }

    @Composable
    override fun <T> eventHandler(func: EventHandlerScope<OutputT>.(T) -> Unit): (T) -> Unit {
        val state = remember {
            Ref(func)
        }.apply {
            value = func
        }

        return remember {
            {
                val handler = state.value
                handlerScope.handler(it)
            }
        }
    }
}

@Composable
private fun <PropsT, RenderingT, OutputT> ProducerScope<RenderingT>.produceWorkflowState(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    context: Workflow<PropsT, OutputT, RenderingT>.RenderContext,
    props: PropsT,
) {
    emit(workflow.render(context, props))
}

@Composable
private fun <PropsT, RenderingT, OutputT> renderWorkflow(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: (OutputT) -> Unit
): State<RenderingT> {
    val state = remember { mutableStateOf<RenderingT?>(null) }

    val scope: ProducerScope<RenderingT> = remember(state) {
        object : ProducerScope<RenderingT> {
            override fun emit(value: RenderingT) {
                state.value = value
            }
        }
    }

    val context = remember(workflow) {
        val base = RenderContextImpl(onOutput)
        with(workflow) {
            RenderContext(base)
        }
    }

    scope.produceWorkflowState(workflow, context, props)

    return remember {
        derivedStateOf {
            state.value!!
        }
    }
}

@Stable
private interface ProducerScope<T> {
    fun emit(value: T)
}

private class Ref<T>(var value: T)

private object ImmediateClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        return onFrame(0)
    }
}

fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.asStateFlow(
    scope: CoroutineScope,
    props: PropsT,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    return asStateFlow(scope, MutableStateFlow(props), onOutput)
}

fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.asStateFlow(
    scope: CoroutineScope,
    props: StateFlow<PropsT>,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    val job = Job(scope.coroutineContext[Job])
    val composeContext = scope.coroutineContext + ImmediateClock + job

    val recomposer = Recomposer(composeContext)

    lateinit var initial: RenderingT

    val callback = Ref { rendering: RenderingT -> initial = rendering }

    val composition = Composition(ForbiddenApplier, recomposer).apply {
        setContent {
            val prop by props.collectAsState()
            val state = renderWorkflow(this@asStateFlow, props = prop, onOutput)

            DisposableEffect(state.value) {
                callback.value.invoke(state.value)
                onDispose { }
            }
        }
    }

    val result = MutableStateFlow(initial)

    callback.value = {
        result.value = it
    }

    scope.launch(context = composeContext) {
        recomposer.runRecomposeAndApplyChanges()
    }

    scope.launch {
        try {
            awaitCancellation()
        } finally {
            recomposer.close()
            recomposer.join()
            composition.dispose()
        }
    }

    return result
}

private object ForbiddenApplier : AbstractApplier<Unit>(Unit) {
    override fun onClear() {
        throw IllegalStateException("No nodes should be emitted in workflow composition")
    }

    override fun insertBottomUp(index: Int, instance: Unit) {
        throw IllegalStateException("No nodes should be emitted in workflow composition")
    }

    override fun insertTopDown(index: Int, instance: Unit) {
        throw IllegalStateException("No nodes should be emitted in workflow composition")
    }

    override fun move(from: Int, to: Int, count: Int) {
        throw IllegalStateException("No nodes should be emitted in workflow composition")
    }

    override fun remove(index: Int, count: Int) {
        throw IllegalStateException("No nodes should be emitted in workflow composition")
    }
}