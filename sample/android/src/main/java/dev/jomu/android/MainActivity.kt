package dev.jomu.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.SavedStateHandle
import dev.jomu.common.App
import dev.jomu.common.BackstackScreen
import dev.jomu.common.FunnyHostWorkflow
import dev.jomu.workflow.WorkflowViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model: AppModel by viewModels()
        setContent {
            App(model.renderings.collectAsState().value)
        }
    }
}

class AppModel(savedState: SavedStateHandle) :
    WorkflowViewModel<String, BackstackScreen, Unit>(FunnyHostWorkflow, "Initial", savedState, {})