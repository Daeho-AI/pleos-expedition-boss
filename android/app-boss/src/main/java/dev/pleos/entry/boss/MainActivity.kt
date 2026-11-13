package dev.pleos.entry.boss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pleos.entry.boss.ui.BossApp

class BossViewModel(val graph: BossGraph) : ViewModel()
class MainActivity : ComponentActivity() {
    private val graph get() = (application as BossApplication).graph
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        graph.gateway.refreshPermissions()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: BossViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BossViewModel(graph) as T
            })
            BossApp(model.graph) {
                if (graph.gateway.state.value.setupAllowed || BuildConfig.IS_FIXTURE) {
                    permissionRequest.launch(arrayOf("android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN"))
                }
            }
        }
    }
    override fun onResume() { super.onResume(); graph.visible(true) }
    override fun onPause() { graph.visible(false); super.onPause() }
}
