package com.shuva.jotter

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.shuva.jotter.ui.LaunchScreen
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import com.shuva.jotter.data.AppPrefs
import com.shuva.jotter.ui.isDark
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.shuva.jotter.data.LockSession
import com.shuva.jotter.ui.EditorViewModel
import com.shuva.jotter.ui.FoldersViewModel
import com.shuva.jotter.ui.JotterRoot
import com.shuva.jotter.ui.JotterTheme
import com.shuva.jotter.ui.NavViewModel
import com.shuva.jotter.ui.NotesViewModel
import com.shuva.jotter.ui.SettingsViewModel
import com.shuva.jotter.ui.sharedIn
import com.shuva.jotter.ui.RecentWidget
import com.shuva.jotter.ui.Screen
import com.shuva.jotter.data.DEFAULT_FOLDER_ID
import com.shuva.jotter.data.newId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** How long the logo shows on a cold start. */
private const val LAUNCH_MS = 900L

@AndroidEntryPoint
// FragmentActivity: the fingerprint prompt needs one.
class MainActivity : FragmentActivity() {
    @Inject lateinit var lockSession: LockSession
    @Inject lateinit var appPrefs: AppPrefs

    private val nav: NavViewModel by viewModels()
    private val folders: FoldersViewModel by viewModels()
    private val notes: NotesViewModel by viewModels()
    private val editor: EditorViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { sharedIn(intent)?.let { nav.incoming = it }; fromWidget(intent) } // not again after rotation
        enableEdgeToEdge()
        // The full logo shows on a cold start from the launcher; a share or a widget tap goes straight in.
        val fromLauncher = savedInstanceState == null && intent?.action == android.content.Intent.ACTION_MAIN &&
            intent?.hasCategory(android.content.Intent.CATEGORY_LAUNCHER) == true
        setContent {
            val appearance by appPrefs.appearance.collectAsState()
            val dark = isDark(appearance.theme)
            // Status and navigation bar icons follow the app's theme, which may differ from the system's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            JotterTheme(appearance) {
                // The app loads underneath while the logo shows, then the logo fades away.
                var launching by rememberSaveable { mutableStateOf(fromLauncher) }
                // The time counts from when the logo is on screen: the first frame of a cold start can itself be slow.
                LaunchedEffect(Unit) {
                    if (!launching) return@LaunchedEffect
                    withFrameNanos { }
                    withFrameNanos { }
                    delay(LAUNCH_MS)
                    launching = false
                }
                Box(Modifier.fillMaxSize()) {
                    JotterRoot(nav, folders, notes, editor, settings)
                    AnimatedVisibility(launching, enter = EnterTransition.None, exit = fadeOut(tween(250))) { LaunchScreen() }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        sharedIn(intent)?.let { nav.incoming = it }
        fromWidget(intent)
    }

    /** A tap on the home-screen widget: open that note, or a new one. */
    private fun fromWidget(intent: android.content.Intent?) {
        val screen = when (intent?.action) {
            RecentWidget.ACTION_OPEN -> intent.getStringExtra(RecentWidget.EXTRA_NOTE)?.let {
                Screen.Editor(it, intent.getStringExtra(RecentWidget.EXTRA_FOLDER) ?: DEFAULT_FOLDER_ID)
            }
            RecentWidget.ACTION_NEW -> Screen.Editor(newId(), DEFAULT_FOLDER_ID)
            else -> null
        } ?: return
        if (nav.current == screen) return
        editor.close()
        if (nav.current is Screen.Editor) nav.pop()
        nav.push(screen)
    }

    override fun onStop() {
        super.onStop()
        editor.flush() // save the open note when the app goes to the background (seals a locked one first)
        if (!isChangingConfigurations) lockSession.lock() // locked notes need the password again
    }
}
