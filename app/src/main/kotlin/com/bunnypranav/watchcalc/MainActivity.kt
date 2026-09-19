package com.bunnypranav.watchcalc

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bunnypranav.watchcalc.state.CalcState
import com.bunnypranav.watchcalc.state.Prefs
import com.bunnypranav.watchcalc.state.Store
import com.bunnypranav.watchcalc.state.Sheet
import com.bunnypranav.watchcalc.ui.ConstantsSheet
import com.bunnypranav.watchcalc.ui.Display
import com.bunnypranav.watchcalc.ui.HistorySheet
import com.bunnypranav.watchcalc.ui.Ink
import com.bunnypranav.watchcalc.ui.Keypad
import com.bunnypranav.watchcalc.ui.PAD_HEIGHT
import com.bunnypranav.watchcalc.ui.PAGES
import com.bunnypranav.watchcalc.ui.PageDots
import kotlinx.coroutines.launch

/**
 * Set true to hold the screen on while the app is in the foreground.
 *
 * A calculator you pause over to read a problem is unusable with the default
 * ~15 s watch timeout, which is why the web build took a wake lock. The cost
 * is battery if you leave it open, so it is one flag rather than a setting.
 */
private const val KEEP_SCREEN_ON = true

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the tile to open a particular keypad. */
        const val EXTRA_PAGE = "com.bunnypranav.watchcalc.PAGE"
    }

    private lateinit var haptics: Haptics

    /** A page the tile asked for, consumed once the pager has moved there. */
    private val requestedPage = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (KEEP_SCREEN_ON) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        haptics = Haptics(this)
        val prefs = Prefs(this)
        requestedPage.value = pageFrom(intent)
        setContent { CalcApp(prefs, haptics, requestedPage) }
    }

    /** launchMode is singleTask, so a second tile tap arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedPage.value = pageFrom(intent)
    }

    private fun pageFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_PAGE, -1)?.takeIf { it >= 0 }
}

/** Short vibrations on key press, mirroring navigator.vibrate on the web. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tap(millis: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

@Composable
fun CalcApp(prefs: Store, haptics: Haptics, requestedPage: MutableState<Int?>? = null) {
    val state = remember { CalcState(prefs) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        // one unit = 1% of the smaller screen side; the whole layout is
        // multiples of it, so the chord widths hold on 396, 450 and 480 px
        val u = minOf(maxWidth, maxHeight) / 100f

        val pager = rememberPagerState(
            initialPage = state.page.coerceIn(0, PAGES.lastIndex),
            pageCount = { PAGES.size }
        )
        val scope = rememberCoroutineScope()

        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.collect { state.goToPage(it) }
        }

        // A tile shortcut jumps straight to its keypad, without animating
        // through the pages in between.
        val pending = requestedPage?.value
        LaunchedEffect(pending) {
            if (pending != null) {
                state.sheet = Sheet.None
                pager.scrollToPage(pending.coerceIn(0, PAGES.lastIndex))
                requestedPage.value = null
            }
        }

        val onPress: (com.bunnypranav.watchcalc.ui.Key, Boolean) -> Unit = { key, long ->
            haptics.tap(if (long) 25L else 10L)
            val from = pager.currentPage
            val outcome = state.press(key, long)
            if (state.shouldReturnHome(from, outcome)) {
                scope.launch { pager.animateScrollToPage(0) }
            }
        }

        // The sheets replace the calculator rather than floating over it, so
        // there is no way for a drag to leak through to the pager underneath.
        when (state.sheet) {
            Sheet.None -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Display(state, u) {
                    val next =
                        if (pager.currentPage >= PAGES.lastIndex) 0 else pager.currentPage + 1
                    scope.launch { pager.animateScrollToPage(next) }
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(u * PAD_HEIGHT)
                ) { index ->
                    Keypad(PAGES[index], u, onPress)
                }
                PageDots(PAGES.size, pager.currentPage, u)
            }

            Sheet.Constants -> {
                BackHandler { state.sheet = Sheet.None }
                ConstantsSheet(
                    u = u,
                    onPick = { symbol ->
                        state.sheet = Sheet.None
                        state.insert(symbol)
                        haptics.tap(10L)
                    },
                    onClose = { state.sheet = Sheet.None }
                )
            }

            Sheet.History -> {
                BackHandler { state.sheet = Sheet.None }
                HistorySheet(
                    u = u,
                    history = state.history,
                    onPick = { entry ->
                        state.sheet = Sheet.None
                        state.reuse(entry)
                        haptics.tap(10L)
                    },
                    onClose = { state.sheet = Sheet.None }
                )
            }
        }
    }
}
