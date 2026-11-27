package com.example.ledger.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.domain.formatCents
import com.example.ledger.domain.product.ProductInsight
import com.example.ledger.domain.product.ProductInsights
import com.example.ledger.domain.product.ProductProfit
import com.example.ledger.ui.AppViewModelProvider
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProductsScreen(
    viewModel: ProductsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 固定头部：标题 + 商品数 + 一键删除全部
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            ) {
                Text("商品利润排行", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "毛利 = 销售额 − 卖出数量 × 平均进价，按全部记录估算，仅供参考。" +
                        "库存和压货金额按记账数量估算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (items.isEmpty()) "暂无商品" else "共 ${items.size} 个商品",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { confirmClearAll = true },
                        enabled = items.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("一键删除全部")
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有商品数据。\n点「记一笔」时填上商品和数量，这里就会显示每样货赚多少。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                ) {
                    items(items, key = { it.profit.productId }) { row ->
                        ProductRow(row.profit, row.insight)
                        HorizontalDivider()
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            // 抬到「记一笔」按钮上方，别被按钮压住
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("删除全部商品？") },
            text = {
                Text(
                    "将删除全部 ${items.size} 个商品。账单会保留，但账单里填的商品和数量会一起清掉，" +
                        "删除后无法恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.deleteAll { deleted ->
                        scope.launch {
                            snackbarHostState.showSnackbar("已删除 $deleted 个商品")
                        }
                    }
                }) { Text("全部删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProductRow(p: ProductProfit, insight: ProductInsight?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                when {
                    insight?.hot == true -> Tag("畅销", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    insight?.slowMoving == true -> Tag("滞销", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            val profit = p.grossProfitCents
            if (profit != null) {
                Text(
                    text = "毛利 ¥" + formatCents(profit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (profit >= 0) incomeColor() else expenseColor(),
                )
            } else {
                Text(
                    text = "毛利 —",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (insight?.hot == true) {
            Text(
                text = "近 ${ProductInsights.HOT_WINDOW_DAYS} 天卖出 ${insight.soldQtyInWindow} 件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (insight?.slowMoving == true) {
            val value = insight.stockValueCents?.let { "¥" + formatCents(it) } ?: "—"
            Text(
                text = "库存估算约 ${insight.stockQty} 件，已 ${insight.idleDays} 天没卖出，压货约 $value",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = "卖出 ${p.soldQty} 件 · 销售额 ¥" + formatCents(p.revenueCents),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val note = when {
            p.soldQty == 0L -> "还没有卖出记录"
            p.costCents == null -> "缺进货记录，算不出毛利"
            p.costIncomplete -> "卖出多于进货记录，进货可能没记全，毛利偏高"
            else -> null
        }
        val margin = p.marginBp?.let { String.format(Locale.US, "毛利率 %.1f%%", it / 100.0) }
        val line = listOfNotNull(margin, note).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}