package dev.jomu.workflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State

@Stable
public interface EventHandlerScope<in OutputT> {
    fun setOutput(output: OutputT)
}

@Stable
public interface BaseRenderContext<in OutputT> {
    @Composable
    public fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        handler: EventHandlerScope<OutputT>.(ChildOutputT) -> Unit
    ): State<ChildRenderingT>

    @Composable
    public fun eventHandler(
        func: EventHandlerScope<OutputT>.() -> Unit
    ): () -> Unit

    @Composable
    public fun <T> eventHandler(
        func: EventHandlerScope<OutputT>.(T) -> Unit
    ): (T) -> Unit
}

@Composable
fun <PropsT, RenderingT> BaseRenderContext<*>.renderChild(
    child: Workflow<PropsT, Nothing, RenderingT>,
    props: PropsT,
): State<RenderingT> {
    return renderChild(child, props) {}
}

@Stable
abstract class Workflow<PropsT, OutputT, RenderingT> {
    public inner class RenderContext internal constructor(
        baseContext: BaseRenderContext<OutputT>
    ) : BaseRenderContext<@UnsafeVariance OutputT> by baseContext

    @Composable
    abstract fun render(
        context: RenderContext,
        props: PropsT,
    ): RenderingT
}