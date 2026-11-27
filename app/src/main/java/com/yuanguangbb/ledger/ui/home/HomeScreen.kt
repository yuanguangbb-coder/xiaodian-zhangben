package com.yuanguangbb.ledger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuanguangbb.ledger.domain.formatCents
import com.yuanguangbb.ledger.domain.product.ProductInsights
import com.yuanguangbb.ledger.domain.tax.ThresholdLevel
import com.yuanguangbb.ledger.ui.AppViewModelProvider
import com.yuanguangbb.ledger.ui.theme.expenseColor
import com.yuanguangbb.ledger.ui.theme.incomeColor

@Composable
fun HomeScreen(
    onOpenReport: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 每次回到首页都重算一次本月/本季度，跨月不会显示上个月的数
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 底部多留一截，避免最后一张卡片被右下角「记一笔」按钮挡住
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.month}月利润",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenReport) { Text("看报表 ›") }
            }
            Text(
                text = "¥" + formatCents(state.profitCents),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AmountTile("收入", state.incomeCents, incomeColor(), Modifier.weight(1f))
            AmountTile("支出", state.expenseCents, expenseColor(), Modifier.weight(1f))
        }

        // 畅销 / 滞销提醒：两边都没有就整张不显示
        val alerts = state.alerts
        if (!alerts.isEmpty) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("进货提醒", style = MaterialTheme.typography.titleSmall)
                    if (alerts.hot.isNotEmpty()) {
                        Text("卖得快的", style = MaterialTheme.typography.labelLarge)
                        alerts.hot.forEach { h ->
                            Text(
                                text = "${h.name} · 近 ${ProductInsights.HOT_WINDOW_DAYS} 天卖出 ${h.soldQtyInWindow} 件",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (alerts.slow.isNotEmpty()) {
                        Text("压着没卖动的", style = MaterialTheme.typography.labelLarge)
                        alerts.slow.forEach { s ->
                            Text(
                                text = "${s.name} · 库存估算约 ${s.stockQty} 件，已 ${s.idleDays} 天没卖出",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        text = "按记账数据估算，仅供参考",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        val th = state.quarterThreshold
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (th.level) {
                ThresholdLevel.SAFE -> MaterialTheme.colorScheme.surfaceVariant
                ThresholdLevel.NEAR -> MaterialTheme.colorScheme.tertiaryContainer
                ThresholdLevel.OVER -> MaterialTheme.colorScheme.errorContainer
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("第${state.quarter}季度 · 增值税起征点", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "已收入 ¥" + formatCents(th.incomeCents) +
                        " / ¥" + formatCents(th.limitCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when (th.level) {
                        ThresholdLevel.SAFE -> "距起征点还剩 ¥" + formatCents(th.remainingCents)
                        ThresholdLevel.NEAR -> "快到起征点了，还剩 ¥" + formatCents(th.remainingCents)
                        ThresholdLevel.OVER -> "已达到起征点，本季度可能需要缴纳增值税"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "仅适用于小规模纳税人，按账本收入粗略对比，不等于申报销售额。" +
                        "请以当地税务局规定为准。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("本月税额估算", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "¥" + formatCents(state.taxCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "按设置里的规则估算，仅供参考。请以当地税务局最新规定为准。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AmountTile(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = color)
            Text(
                text = "¥" + formatCents(cents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
