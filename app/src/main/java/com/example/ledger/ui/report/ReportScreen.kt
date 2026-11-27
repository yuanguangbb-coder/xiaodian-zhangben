package com.example.ledger.ui.report

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.domain.formatCents
import com.example.ledger.domain.report.CategoryTotal
import com.example.ledger.domain.report.MonthPoint
import com.example.ledger.domain.report.ReportMath
import com.example.ledger.ui.AppViewModelProvider
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("报表", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // 发给会计/家人：微信、短信、邮件都行
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "小店账本 ${state.monthLabel}")
                    putExtra(Intent.EXTRA_TEXT, shareText(state))
                }
                context.startActivity(Intent.createChooser(intent, "发报表给会计或家人"))
            }) { Icon(Icons.Filled.Share, contentDescription = "分享报表") }
        }

        // 月份切换
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { viewModel.previousMonth() }) { Text("‹ 上个月") }
            Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.nextMonth() }, enabled = state.offset > 0) {
                Text("下个月 ›")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportTile("收入", state.incomeCents, incomeColor(), Modifier.weight(1f))
            ReportTile("支出", state.expenseCents, expenseColor(), Modifier.weight(1f))
            ReportTile(
                "利润",
                state.profitCents,
                if (state.profitCents >= 0) incomeColor() else expenseColor(),
                Modifier.weight(1f),
            )
        }

        if (!state.hasData) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("这个月还没有账单。", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "记几笔之后再回来看，这里会有分类占比和近 6 个月的走势。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.expenseSlices.isNotEmpty()) {
            SectionTitle("支出花在哪")
            state.expenseSlices.forEach { slice ->
                CategoryBar(slice, state.expenseCents, expenseColor())
            }
        }

        if (state.incomeSlices.isNotEmpty()) {
            SectionTitle("收入从哪来")
            state.incomeSlices.forEach { slice ->
                CategoryBar(slice, state.incomeCents, incomeColor())
            }
        }

        SectionTitle("近 6 个月利润")
        TrendChart(state.trend)
        Text(
            "按记账数据统计，赊出去的账在记赊账那天就算收入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分享给会计/家人的纯文本报表 */
private fun shareText(state: ReportUi): String = buildString {
    append("小店账本 ").append(state.monthLabel).append("\n")
    append("收入 ¥").append(formatCents(state.incomeCents)).append("\n")
    append("支出 ¥").append(formatCents(state.expenseCents)).append("\n")
    append("利润 ¥").append(formatCents(state.profitCents)).append("\n")
    if (state.expenseSlices.isNotEmpty()) {
        append("\n支出排行：\n")
        state.expenseSlices.forEachIndexed { index, slice ->
            append(index + 1).append(". ").append(slice.name)
                .append(" ¥").append(formatCents(slice.cents))
                .append("（").append(ReportMath.sharePercent(slice.cents, state.expenseCents)).append("%）\n")
        }
    }
    if (state.incomeSlices.isNotEmpty()) {
        append("\n收入来源：\n")
        state.incomeSlices.forEachIndexed { index, slice ->
            append(index + 1).append(". ").append(slice.name)
                .append(" ¥").append(formatCents(slice.cents)).append("\n")
        }
    }
    append("\n（按记账数据统计，赊出去的账在记赊账那天算收入）")
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReportTile(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                "¥" + formatCents(cents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CategoryBar(slice: CategoryTotal, total: Long, color: Color) {
    val percent = ReportMath.sharePercent(slice.cents, total)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            slice.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(84.dp),
        )
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(10.dp)
                    .background(color, RoundedCornerShape(5.dp)),
            )
        }
        Text(
            "¥" + formatCents(slice.cents) + "  $percent%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendChart(points: List<MonthPoint>) {
    if (points.isEmpty()) return
    val maxAbs = points.maxOf { kotlin.math.abs(it.profitCents) }.coerceAtLeast(1L)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp).height(132.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            points.forEach { point ->
                val fraction = (kotlin.math.abs(point.profitCents).toFloat() / maxAbs).coerceIn(0.02f, 1f)
                val color = if (point.profitCents >= 0) incomeColor() else expenseColor()
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        if (point.profitCents == 0L) "" else formatCents(point.profitCents),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction)
                            .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(point.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}