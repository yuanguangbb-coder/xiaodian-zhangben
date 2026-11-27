package com.example.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.ledger.navigation.LedgerNavHost
import com.example.ledger.ui.lock.LockScreen
import com.example.ledger.ui.theme.LedgerTheme

class MainActivity : ComponentActivity() {

    /** 退到后台超过这个时间，回来就要重新输密码 */
    private val relockAfterMillis = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LedgerApplication
        setContent {
            LedgerTheme {
                val pinSet = remember { app.settings.isPinSet }
                var unlocked by rememberSaveable { mutableStateOf(!pinSet) }
                var backgroundedAt by remember { mutableLongStateOf(0L) }

                // 切到后台记一下时间（拍单据、选文件也会触发，30 秒内回来不锁）
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    backgroundedAt = System.currentTimeMillis()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if (pinSet && backgroundedAt > 0 &&
                        System.currentTimeMillis() - backgroundedAt > relockAfterMillis
                    ) {
                        unlocked = false
                    }
                    backgroundedAt = 0
                }

                if (unlocked) {
                    LedgerNavHost()
                } else {
                    LockScreen(
                        verify = { pin -> app.settings.verifyPin(pin) },
                        guard = app.settings,
                        onUnlocked = { unlocked = true },
                    )
                }
            }
        }
    }
}