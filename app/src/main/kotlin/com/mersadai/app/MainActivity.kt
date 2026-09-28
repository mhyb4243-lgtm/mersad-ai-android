package com.mersadai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mersadai.app.core.AppContainer
import com.mersadai.app.domain.model.ThemeMode
import com.mersadai.app.feature.explore.ExploreViewModel
import com.mersadai.app.navigation.MersadNavHost
import com.mersadai.app.ui.theme.MersadTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.mersadai.app.domain.model.AppSettings(),
            )
            val exploreViewModel: ExploreViewModel = viewModel(
                factory = ExploreViewModelFactory(container),
            )
            MersadTheme(themeMode = settings.themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MersadNavHost(
                        viewModel = exploreViewModel,
                        settings = settings,
                        settingsRepository = container.settingsRepository,
                        onManualSync = container.syncScheduler::enqueueManualSync,
                    )
                }
            }
        }
    }
}

private class ExploreViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExploreViewModel(container.contentRepository, container.networkMonitor) as T
}
