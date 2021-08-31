package dev.jomu.workflow

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


private class RenderContextImpl<in OutputT>(private val onOutput: (OutputT) -> Unit) :
    BaseRenderContext<OutputT> {
    private val handlerScope = object : EventHandlerScope<OutputT> {
        override fun setOutput(output: OutputT) {
            onOutput(output)
        }
    }

    @Composable
    override fun <ChildPropsT, ChildOutputT, ChildRenderingT : Any> renderChild(
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

    inner class Scope<T>(initial: T, private val handlerScope: EventHandlerScope<OutputT>) :
        BaseRenderContext.TransformedValueScope<T, OutputT> {
        var current: T = initial
        override fun setOutput(output: OutputT) {
            handlerScope.setOutput(output)
        }

        override val value: T
            get() = current

    }

    @Composable
    override fun <T, U> rememberTransformedValue(value: T, block: BaseRenderContext.TransformedValueScope<T, OutputT>.() -> U): U {
        val scope = remember {
            Scope(value, handlerScope)
        }.apply {
            this.current = value
        }

        return remember {
            scope.block()
        }
    }
}

@Composable
private fun <PropsT, RenderingT : Any, OutputT> ProducerScope<RenderingT>.produceWorkflowState(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    context: Workflow<PropsT, OutputT, RenderingT>.RenderContext,
    props: PropsT,
) {
    emit(workflow.render(context, props))
}

@Composable
private fun <PropsT, RenderingT : Any, OutputT> renderWorkflowToState(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    context: Workflow<PropsT, OutputT, RenderingT>.RenderContext,
    props: PropsT,
    state: MutableState<RenderingT>
) {
    state.value = workflow.render(context, props)
}

@Composable
private fun <PropsT, RenderingT : Any, OutputT> renderWorkflow(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: (OutputT) -> Unit
): State<RenderingT> {
    // will always be set to a non-null value before we return
    val state = remember { mutableStateOf<RenderingT?>(null) as MutableState<RenderingT> }

    val context = remember(workflow) {
        val base = RenderContextImpl(onOutput)
        with(workflow) {
            RenderContext(base)
        }
    }

    renderWorkflowToState(workflow, context, props, state)

    return state
}

@Stable
internal interface ProducerScope<T> {
    fun emit(value: T)
}

class Ref<T>(var value: T)

private object ImmediateClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        return onFrame(0)
    }
}

public fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.renderAsFlow(
    scope: CoroutineScope,
    props: PropsT,
    saveableStateRegistry: SaveableStateRegistry? = null,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    return renderAsFlow(scope, MutableStateFlow(props), saveableStateRegistry, onOutput)
}

public fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.renderAsFlow(
    scope: CoroutineScope,
    props: StateFlow<PropsT>,
    saveableStateRegistry: SaveableStateRegistry? = null,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    val handle = Snapshot.registerGlobalWriteObserver {
        Snapshot.sendApplyNotifications()
    }

    val job = Job(scope.coroutineContext[Job])
    val composeContext = scope.coroutineContext + ImmediateClock + job

    val recomposer = Recomposer(composeContext)

    lateinit var initial: RenderingT

    val callback = Ref { rendering: RenderingT -> initial = rendering }

    val composition = Composition(ForbiddenApplier, recomposer).apply {
        setContent {
            val prop by props.collectAsState()
            CompositionLocalProvider(LocalSaveableStateRegistry provides saveableStateRegistry) {
                val state = renderWorkflow(this@renderAsFlow, props = prop, onOutput)
                DisposableEffect(state.value) {
                    callback.value.invoke(state.value)
                    onDispose { }
                }
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
            handle.dispose()
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