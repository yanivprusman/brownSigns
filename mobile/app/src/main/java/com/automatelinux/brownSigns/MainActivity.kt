package com.automatelinux.brownSigns

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automatelinux.brownSigns.data.model.Site
import com.automatelinux.brownSigns.ui.BrownSignsScreen
import com.automatelinux.brownSigns.ui.BrownSignsViewModel
import com.automatelinux.brownSigns.ui.feedback.FeedbackHost
import com.automatelinux.brownSigns.ui.theme.AppTheme
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: BrownSignsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        viewModel.onPermissionResult(
            granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect(::handle)
            }
        }

        setContent {
            // Hebrew-first: the whole UI is RTL whatever the device locale is.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme {
                    FeedbackHost {
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        BrownSignsScreen(state = state, actions = viewModel)
                    }
                }
            }
        }
    }

    private fun handle(effect: BrownSignsViewModel.Effect) = when (effect) {
        is BrownSignsViewModel.Effect.AskLocationPermission -> permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        is BrownSignsViewModel.Effect.OpenUrl -> open(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
        is BrownSignsViewModel.Effect.Navigate -> open(Intent(Intent.ACTION_VIEW, geoUri(effect.site)))
    }

    /**
     * A geo: URI, so the phone offers whichever navigator the user actually uses
     * — Waze, Google Maps, Organic Maps — instead of the app picking one for them.
     */
    private fun geoUri(site: Site): Uri {
        val label = Uri.encode(site.he)
        return Uri.parse("geo:${site.lat},${site.lon}?q=${site.lat},${site.lon}($label)")
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "אין באיזו אפליקציה לפתוח את זה", Toast.LENGTH_SHORT).show()
        }
    }
}
