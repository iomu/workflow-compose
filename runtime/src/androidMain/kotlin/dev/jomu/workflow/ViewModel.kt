package dev.jomu.workflow

import android.os.Binder
import android.os.Bundle
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Serializable

fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.asStateFlow(
    scope: CoroutineScope,
    props: PropsT,
    savedState: SavedStateHandle,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    return asStateFlow(scope, MutableStateFlow(props), savedState, onOutput)
}

fun <PropsT, RenderingT : Any, OutputT> Workflow<PropsT, OutputT, RenderingT>.asStateFlow(
    scope: CoroutineScope,
    props: StateFlow<PropsT>,
    savedState: SavedStateHandle,
    onOutput: (OutputT) -> Unit,
): StateFlow<RenderingT> {
    val bundle = savedState.get<Bundle>("restored_state")
    val restored: Map<String, List<Any?>>? = bundle?.toMap()

    val saveableStateRegistry = SaveableStateRegistry(restored) {
        canBeSavedToBundle(it)
    }

    savedState.setSavedStateProvider("restored_state") {
        saveableStateRegistry.performSave().toBundle()
    }

    return renderAsFlow(scope, props, saveableStateRegistry, onOutput)
}

open class WorkflowViewModel<Props, Rendering : Any, OutputT>(workflow: Workflow<Props, OutputT, Rendering>, props: Props, savedState: SavedStateHandle, onOutput: (OutputT) -> Unit) : ViewModel() {
    val renderings: StateFlow<Rendering> by lazy {
        workflow.asStateFlow(viewModelScope, props, savedState, onOutput)
    }
}

private fun canBeSavedToBundle(value: Any): Boolean {
    // SnapshotMutableStateImpl is Parcelable, but we do extra checks
    if (value is SnapshotMutableState<*>) {
        if (value.policy === neverEqualPolicy<Any?>() ||
            value.policy === structuralEqualityPolicy<Any?>() ||
            value.policy === referentialEqualityPolicy<Any?>()
        ) {
            val stateValue = value.value
            return if (stateValue == null) true else canBeSavedToBundle(stateValue)
        } else {
            return false
        }
    }
    for (cl in AcceptableClasses) {
        if (cl.isInstance(value)) {
            return true
        }
    }
    return false
}

/**
 * Contains Classes which can be stored inside [Bundle].
 *
 * Some of the classes are not added separately because:
 *
 * This classes implement Serializable:
 * - Arrays (DoubleArray, BooleanArray, IntArray, LongArray, ByteArray, FloatArray, ShortArray,
 * CharArray, Array<Parcelable, Array<String>)
 * - ArrayList
 * - Primitives (Boolean, Int, Long, Double, Float, Byte, Short, Char) will be boxed when casted
 * to Any, and all the boxed classes implements Serializable.
 * This class implements Parcelable:
 * - Bundle
 *
 * Note: it is simplified copy of the array from SavedStateHandle (lifecycle-viewmodel-savedstate).
 */
private val AcceptableClasses = arrayOf(
    Serializable::class.java,
    Parcelable::class.java,
    String::class.java,
    SparseArray::class.java,
    Binder::class.java,
    Size::class.java,
    SizeF::class.java
)

private fun Bundle.toMap(): Map<String, List<Any?>> {
    val map = mutableMapOf<String, List<Any?>>()
    this.keySet().forEach { key ->
        val list = getParcelableArrayList<Parcelable?>(key) as ArrayList<Any?>
        map[key] = list
    }
    return map
}

private fun Map<String, List<Any?>>.toBundle(): Bundle {
    val bundle = Bundle()
    forEach { (key, list) ->
        val arrayList = if (list is ArrayList<Any?>) list else ArrayList(list)
        bundle.putParcelableArrayList(
            key,
            arrayList as ArrayList<Parcelable?>
        )
    }
    return bundle
}