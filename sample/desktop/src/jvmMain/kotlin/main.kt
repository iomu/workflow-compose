import dev.jomu.common.App
import androidx.compose.desktop.Window
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.application

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    Window {
        App()
    }
}