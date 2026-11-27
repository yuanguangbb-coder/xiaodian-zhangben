package com.example.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 设计思路：给小店老板用，单手、忙的时候也要一眼看清。
// 深青墨色做主色，收入/支出用绿/红；提醒色用暖琥珀，不用 M3 默认的紫。
private val Ink = Color(0xFF1F4E5F)
private val Paper = Color(0xFFF2F5F6)

// 浅色主题下的收入/支出（对比度 4.60 / 5.16，过 AA）
private val IncomeLight = Color(0xFF2E7D4F)
private val ExpenseLight = Color(0xFFB3412F)

// 深色主题下必须提亮，否则小字对比度只有 3.1~3.5
private val IncomeDark = Color(0xFF7ED9A6)
private val ExpenseDark = Color(0xFFFF9C88)

val IncomeColor: Color = IncomeLight
val ExpenseColor: Color = ExpenseLight

/** 跟随主题的收入色（深色模式自动提亮） */
@Composable
fun incomeColor(): Color = if (isSystemInDarkTheme()) IncomeDark else IncomeLight

/** 跟随主题的支出色 */
@Composable
fun expenseColor(): Color = if (isSystemInDarkTheme()) ExpenseDark else ExpenseLight

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E6EC),
    onPrimaryContainer = Color(0xFF0B2A35),

    secondary = Color(0xFF4A6572),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7EC),
    onSecondaryContainer = Color(0xFF12232B),

    // 提醒色：暖琥珀，替代 M3 默认的紫粉
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7BF),
    onTertiaryContainer = Color(0xFF2B1D00),

    error = ExpenseLight,
    onError = Color.White,
    errorContainer = Color(0xFFFBE0DA),
    onErrorContainer = Color(0xFF3F0F08),

    background = Paper,
    onBackground = Color(0xFF1A2A30),
    surface = Paper,
    onSurface = Color(0xFF1A2A30),
    surfaceVariant = Color(0xFFE3E9EB),
    onSurfaceVariant = Color(0xFF44585F),
    outline = Color(0xFF7A8A91),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC3D3),
    onPrimary = Color(0xFF0B2A35),
    primaryContainer = Color(0xFF254E5C),
    onPrimaryContainer = Color(0xFFD3E6EC),

    secondary = Color(0xFFB4C8D0),
    onSecondary = Color(0xFF12232B),
    secondaryContainer = Color(0xFF2A3B42),
    onSecondaryContainer = Color(0xFFDDE7EC),

    tertiary = Color(0xFFFFD08A),
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFE7BF),

    error = ExpenseDark,
    onError = Color(0xFF3F0F08),
    errorContainer = Color(0xFF5A1B12),
    onErrorContainer = Color(0xFFFFDAD3),

    background = Color(0xFF12191C),
    onBackground = Color(0xFFE3EAEC),
    surface = Color(0xFF12191C),
    onSurface = Color(0xFFE3EAEC),
    surfaceVariant = Color(0xFF1E282C),
    onSurfaceVariant = Color(0xFFB7C4C9),
    outline = Color(0xFF6E7F86),
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}