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
    fun <ChildPropsT, ChildOutputT, ChildRenderingT : Any> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        handler: EventHandlerScope<OutputT>.(ChildOutputT) -> Unit
    ): State<ChildRenderingT>

    interface TransformedValueScope<T, in OutputT> : EventHandlerScope<OutputT> {
        val value: T
    }

    @Composable
    fun <T, U> rememberTransformedValue(value: T, block: TransformedValueScope<T, OutputT>.() -> U): U

    @Composable
    fun eventHandler(func: EventHandlerScope<OutputT>.() -> Unit): () -> Unit = rememberTransformedValue(func) {
        {
            value()
        }
    }

    @Composable
    fun <A> eventHandler(func: EventHandlerScope<OutputT>.(A) -> Unit): (A) -> Unit = rememberTransformedValue(func) {
        { a -> value(a) }
    }

    @Composable
    fun <A, B> eventHandler(func: EventHandlerScope<OutputT>.(A, B) -> Unit): (A, B) -> Unit =
        rememberTransformedValue(func) {
            { a, b -> value(a, b) }
        }

    @Composable
    fun <A, B, C> eventHandler(func: EventHandlerScope<OutputT>.(A, B, C) -> Unit): (A, B, C) -> Unit =
        rememberTransformedValue(func) {
            { a, b, c -> value(a, b, c) }
        }

    @Composable
    fun <A, B, C, D> eventHandler(func: EventHandlerScope<OutputT>.(A, B, C, D) -> Unit): (A, B, C, D) -> Unit =
        rememberTransformedValue(func) {
            { a, b, c, d -> value(a, b, c, d) }
        }
}

@Composable
public fun <PropsT, RenderingT : Any> BaseRenderContext<*>.renderChild(
    child: Workflow<PropsT, Nothing, RenderingT>,
    props: PropsT,
): State<RenderingT> {
    return renderChild(child, props) {}
}

@Composable
public fun <RenderingT : Any> BaseRenderContext<*>.renderChild(
    child: Workflow<Unit, Nothing, RenderingT>,
): State<RenderingT> {
    return renderChild(child, Unit) {}
}

@Composable
public fun <OutputT, ChildOutputT, RenderingT : Any> BaseRenderContext<OutputT>.renderChild(
    child: Workflow<Unit, ChildOutputT, RenderingT>,
    handler: EventHandlerScope<OutputT>.(ChildOutputT) -> Unit
): State<RenderingT> {
    return renderChild(child, Unit, handler)
}

public abstract class Workflow<PropsT, OutputT, RenderingT : Any> {
    @Stable
    inner class RenderContext internal constructor(
        baseContext: BaseRenderContext<OutputT>
    ) : BaseRenderContext<@UnsafeVariance OutputT> by baseContext

    @Composable
    abstract fun render(
        context: RenderContext,
        props: PropsT,
    ): RenderingT
}