package dev.jomu.workflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlin.reflect.KClass

public inline fun <reified RenderingT : Any> composeViewFactory(
    noinline content: @Composable (
        rendering: RenderingT,
        environment: ViewEnvironment
    ) -> Unit
): ViewFactory<RenderingT> = composeViewFactory(RenderingT::class, content)

@PublishedApi
internal fun <RenderingT : Any> composeViewFactory(
    type: KClass<RenderingT>,
    content: @Composable (
        rendering: RenderingT,
        environment: ViewEnvironment
    ) -> Unit
): ViewFactory<RenderingT> = object : ViewFactory<RenderingT> {
    override val type: KClass<in RenderingT> = type
    @Composable override fun Content(
        rendering: RenderingT,
        viewEnvironment: ViewEnvironment
    ) {
        content(rendering, viewEnvironment)
    }
}

public interface ViewFactory<in RenderingT: Any> {
    val type: KClass<in RenderingT>

    @Composable
    fun Content(
        rendering: RenderingT,
        viewEnvironment: ViewEnvironment
    )
}

public class ViewEnvironment(
    val map: Map<ViewEnvironmentKey<*>, Any> = emptyMap()
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: ViewEnvironmentKey<T>): T = map[key] as? T ?: key.default

    operator fun <T : Any> plus(pair: Pair<ViewEnvironmentKey<T>, T>): ViewEnvironment =
        ViewEnvironment(map + pair)

    operator fun plus(other: ViewEnvironment): ViewEnvironment =
        ViewEnvironment(map + other.map)

    override fun toString(): String = "ViewEnvironment($map)"

    override fun equals(other: Any?): Boolean =
        (other as? ViewEnvironment)?.let { it.map == map } ?: false

    override fun hashCode(): Int = map.hashCode()
}

/**
 * Defines a value that can be provided by a [ViewEnvironment] map, specifying its [type]
 * and [default] value.
 */
public abstract class ViewEnvironmentKey<T : Any>(
    private val type: KClass<T>
) {
    abstract val default: T

    final override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other != null && this::class != other::class -> false
        else -> type == (other as ViewEnvironmentKey<*>).type
    }

    final override fun hashCode(): Int = type.hashCode()

    override fun toString(): String {
        return "ViewEnvironmentKey($type)-${super.toString()}"
    }
}

public interface ViewRegistry {
    fun <RenderingT : Any> getFactoryFor(
        renderingType: KClass<out RenderingT>
    ): ViewFactory<RenderingT>?

    companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
        override val default: ViewRegistry get() = ViewRegistry()
    }
}

public fun ViewRegistry(vararg bindings: ViewFactory<*>): ViewRegistry =
    TypedViewRegistry(*bindings)

fun <RenderingT : Any>
        ViewRegistry.getFactoryForRendering(rendering: RenderingT): ViewFactory<RenderingT> {
    @Suppress("UNCHECKED_CAST")
    return getFactoryFor(rendering::class)
        ?: throw IllegalArgumentException(
            "A ${ViewFactory::class.qualifiedName} should have been registered to display " +
                    "${rendering::class.qualifiedName} instances."
        )
}

internal class TypedViewRegistry private constructor(
    private val bindings: Map<KClass<*>, ViewFactory<*>>
) : ViewRegistry {

    constructor(vararg bindings: ViewFactory<*>) : this(
        bindings.associateBy { it.type }
            .apply {
                check(keys.size == bindings.size) {
                    "${bindings.map { it.type }} must not have duplicate entries."
                }
            } as Map<KClass<*>, ViewFactory<*>>
    )

    override fun <RenderingT : Any> getFactoryFor(
        renderingType: KClass<out RenderingT>
    ): ViewFactory<RenderingT>? {
        @Suppress("UNCHECKED_CAST")
        return bindings[renderingType] as? ViewFactory<RenderingT>
    }
}

public interface Compatible {
    val compatibilityKey: String

    companion object {
        fun keyFor(
            value: Any,
            name: String = ""
        ): String {
            return ((value as? Compatible)?.compatibilityKey ?: value::class.qualifiedName) +
                    if (name.isEmpty()) "" else "+$name"
        }
    }
}

@Composable
public fun WorkflowRendering(
    rendering: Any,
    viewEnvironment: ViewEnvironment,
    modifier: Modifier = Modifier
) {
    // This will fetch a new view factory any time the new rendering is incompatible with the previous
    // one, as determined by Compatible. This corresponds to WorkflowViewStub's canShowRendering
    // check.
    val renderingCompatibilityKey = Compatible.keyFor(rendering)

    key(renderingCompatibilityKey) {
        val viewFactory = remember {
            viewEnvironment[ViewRegistry].getFactoryForRendering(rendering)
        }

        // We need to propagate min constraints because one of the likely uses for the modifier passed
        // into this function is to directly control the layout of the child view – which means
        // minimum constraints are likely to be significant.
        Box(modifier, propagateMinConstraints = true) {
            viewFactory.Content(rendering, viewEnvironment)
        }
    }
}