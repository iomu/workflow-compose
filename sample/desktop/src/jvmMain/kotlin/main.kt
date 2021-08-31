import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import dev.jomu.common.App
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.jomu.common.HostWorkflow
import dev.jomu.workflow.renderAsFlow
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Workflow Example",
        state = rememberWindowState(width = 300.dp, height = 300.dp)
    ) {

        val scope = rememberCoroutineScope()
        val saveableStateRegistry = LocalSaveableStateRegistry.current
        val renderings = remember { HostWorkflow.renderAsFlow(scope, MutableStateFlow(Unit), saveableStateRegistry) {} }
        App(renderings.collectAsState().value)
    }
}
