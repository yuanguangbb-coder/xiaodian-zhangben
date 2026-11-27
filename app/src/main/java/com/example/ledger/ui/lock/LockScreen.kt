package com.example.ledger.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ledger.domain.lock.LockGuard
import com.example.ledger.domain.lock.LockPolicy
import kotlinx.coroutines.delay

/** 应用锁：输 4 位数字才能进账本；连错 5 次锁 30 秒（计数落盘，重启也绕不过） */
@Composable
fun LockScreen(
    verify: (String) -> Boolean,
    guard: LockGuard,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(guard.failedAttempts) }
    var cooldownLeft by remember { mutableIntStateOf(0) }

    // 冷却剩余秒数：每秒跟一次，时间到就解锁输入
    LaunchedEffect(Unit) {
        while (true) {
            val left = guard.cooldownUntilMillis - System.currentTimeMillis()
            cooldownLeft = if (left > 0) ((left + 999) / 1000).toInt() else 0
            if (left <= 0 && guard.failedAttempts == 0) attempts = 0
            delay(500)
        }
    }

    fun tryUnlock() {
        if (cooldownLeft > 0) return
        if (verify(pin)) {
            guard.clearFailures()
            onUnlocked()
            return
        }
        error = true
        pin = ""
        guard.recordFailure(System.currentTimeMillis())
        attempts = guard.failedAttempts
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("小店账本", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (cooldownLeft > 0) "密码错太多次，${cooldownLeft} 秒后再试"
            else "请输入 4 位数字密码",
            style = MaterialTheme.typography.bodyMedium,
            color = if (cooldownLeft > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { text ->
                if (cooldownLeft > 0) return@OutlinedTextField
                if (text.length <= 6 && text.all { it.isDigit() }) {
                    pin = text
                    error = false
                }
            },
            label = { Text("密码") },
            enabled = cooldownLeft == 0,
            isError = error,
            supportingText = {
                if (error && cooldownLeft == 0) {
                    Text("密码不对，还可以试 ${(LockPolicy.MAX_ATTEMPTS - attempts).coerceAtLeast(1)} 次")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { tryUnlock() },
            enabled = pin.length in 4..6 && cooldownLeft == 0,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("解锁") }
    }
}