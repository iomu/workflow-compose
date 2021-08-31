package dev.jomu.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import dev.jomu.workflow.Workflow
import dev.jomu.workflow.ui.ViewEnvironment
import dev.jomu.workflow.ui.ViewFactory
import kotlin.reflect.KClass

@OptIn(ExperimentalStdlibApi::class)
object FunnyHostWorkflow : Workflow<String, Unit, BackstackScreen>() {
    @Composable
    override fun render(context: RenderContext, props: String): BackstackScreen {
        println("${System.currentTimeMillis()} FunnyHostWorkflow $props")
        var next by rememberSaveable(stateSaver = autoSaver()) { mutableStateOf<String?>(null) }

        val goBack = context.eventHandler { setOutput(Unit) }
        val top = context.renderChild(FunnyWorkflow, props) {
            when(it) {
                FunnyWorkflow.Output.GoBack -> goBack()
                is FunnyWorkflow.Output.Navigate -> next = it.target
            }
        }
        val nextScreens = next?.let {
            context.renderChild(FunnyHostWorkflow, it) { next = null }
        }
        return BackstackScreen(listOf(top), next = nextScreens, ownOnBack = goBack)
    }
}

object FunnyWorkflow : Workflow<String, FunnyWorkflow.Output, FunnyRendering>() {
    sealed class Output {
        object GoBack : Output()
        data class Navigate(val target: String) : Output()
    }
    @Composable
    override fun render(context: RenderContext, props: String): FunnyRendering {
        println("${System.currentTimeMillis()} FunnyWorkflow $props")

        var counter by rememberSaveable(stateSaver = autoSaver()) { mutableStateOf(0) }

        val onNavigate = context.eventHandler<String> { setOutput(Output.Navigate(it)) }
        val goBack = context.eventHandler { setOutput(Output.GoBack) }
        val increment = context.eventHandler { counter++ }

        return FunnyRendering(props, onNavigate, goBack, counter, increment)
    }
}

data class FunnyRendering(val title: String, val onNavigate: (String) -> Unit, val onGoBack: (() -> Unit)?, val count: Int, val increment: () -> Unit)

object FunnyFactory : ViewFactory<FunnyRendering> {
    override val type: KClass<in FunnyRendering> = FunnyRendering::class

    @Composable
    override fun Content(rendering: FunnyRendering, viewEnvironment: ViewEnvironment) {
        println("${System.currentTimeMillis()} FunnyFactory ${rendering.title}")
        Column {
            Text(rendering.title)

            var next by rememberSaveable { mutableStateOf("") }
            TextField(next, onValueChange = {
                next = it
            })

            Button(onClick = {
                rendering.onNavigate(next)
            }) {
                Text("Next")
            }

            Text(rendering.count.toString())
            Button(onClick = {
                rendering.increment()
            }) {
                Text("Count")
            }

            rendering.onGoBack?.let {
                Button(onClick = it) {
                    Text("Go back")
                }
            }
        }
    }
}