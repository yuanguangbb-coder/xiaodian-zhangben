package com.example.ledger.ui.records

import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ledger.ui.common.ImageUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.data.db.TransactionWithNames
import com.example.ledger.domain.formatCents
import com.example.ledger.ui.AppViewModelProvider
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 账单日期时间：2026年11月25日 18:03 */
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/** 录入时间精确到秒，方便核对到底什么时候录进账本的 */
private val createdFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss")

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

private fun formatCreatedAt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(createdFormatter)

@Composable
fun RecordsScreen(
    onEdit: (Long) -> Unit = {},
    viewModel: RecordsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val limit by viewModel.limit.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<TransactionWithNames?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var previewReceipt by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val categoryName = allCategories.firstOrNull { it.id == filter.categoryId }?.name
    val accountName = accounts.firstOrNull { it.id == filter.accountId }?.name
    val productName = products.firstOrNull { it.id == filter.productId }?.name
    // 按天分组：同一天的账单放一起，头部显示当天收支小计，翻账快很多
    val groupedByDay = records.groupBy {
        Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 搜索 + 筛选
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = filter.keyword,
                    onValueChange = { viewModel.setKeyword(it) },
                    label = { Text("搜备注、分类、账户、商品") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showFilters = true }) { Text("筛选") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        filter.isActive -> "筛出 ${records.size} 笔"
                        records.isEmpty() -> "暂无账单"
                        else -> "共 ${records.size} 笔账单"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (filter.isActive) {
                    TextButton(onClick = { viewModel.resetFilter() }) { Text("清除筛选") }
                }
                TextButton(
                    onClick = { confirmClearAll = true },
                    enabled = records.isNotEmpty(),
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

            // 当前生效的条件，一眼能看见
            if (categoryName != null || accountName != null || productName != null || filter.range != RecordsRange.ALL) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val rangeLabel = when (filter.range) {
                        RecordsRange.ALL -> null
                        RecordsRange.THIS_MONTH -> "本月"
                        RecordsRange.LAST_MONTH -> "上月"
                    }
                    if (rangeLabel != null) item { FilterChip(selected = true, onClick = { viewModel.setRange(RecordsRange.ALL) }, label = { Text(rangeLabel) }) }
                    if (categoryName != null) item { FilterChip(selected = true, onClick = { viewModel.setCategory(null) }, label = { Text(categoryName) }) }
                    if (accountName != null) item { FilterChip(selected = true, onClick = { viewModel.setAccount(null) }, label = { Text(accountName) }) }
                    if (productName != null) item { FilterChip(selected = true, onClick = { viewModel.setProduct(null) }, label = { Text(productName) }) }
                }
            }

            if (records.isEmpty()) {
                Box(
                    Modifier.fillMaxSize()
                        .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filter.isActive) {
                            "没有符合条件的账单，换个筛选看看。"
                        } else {
                            "还没有账目。点右下角「记一笔」开始记账。"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 底部留白，避免最后一条被「记一笔」按钮挡住
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 8.dp, top = 4.dp, bottom = 96.dp,
                    ),
                ) {
                    groupedByDay.entries.toList().forEachIndexed { index, entry ->
                        val day = entry.key
                        val dayRecords = entry.value
                        // 最后一组可能被分页截断，小计要标注「当天还有更多」
                        val truncated = index == groupedByDay.size - 1 && records.size >= limit
                        item(key = "day-$day") {
                            DayHeader(day = day, records = dayRecords, truncated = truncated)
                        }
                        items(dayRecords, key = { it.id }) { item ->
                            RecordRow(
                                item = item,
                                onClick = { onEdit(item.id) },
                                onPreviewReceipt = { previewReceipt = item.receiptPath },
                                onDelete = { pendingDelete = item },
                            )
                            HorizontalDivider()
                        }
                    }
                    // 一次最多查 200 条，翻到底再点加载
                    if (records.size >= limit) {
                        item(key = "load-more") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(onClick = { viewModel.loadMore() }) { Text("加载更多") }
                            }
                        }
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

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔账？") },
            text = {
                Text(
                    "${item.categoryName} ¥${formatCents(item.amountCents)}\n" +
                        "记账时间 ${formatDateTime(item.timeMillis)}\n" +
                        "会先放进回收站，30 天内还能恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    pendingDelete = null
                    scope.launch { snackbarHostState.showSnackbar("已移入回收站") }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("删除全部账单？") },
            text = {
                Text("将把全部 ${records.size} 笔账单放进回收站，30 天内可以恢复。分类和账户会保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.deleteAll { deleted ->
                        scope.launch {
                            snackbarHostState.showSnackbar("已移入回收站 $deleted 笔")
                        }
                    }
                }) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            },
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("筛选账单") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("时间", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter.range == RecordsRange.ALL,
                            onClick = { viewModel.setRange(RecordsRange.ALL) },
                            label = { Text("全部") },
                        )
                        FilterChip(
                            selected = filter.range == RecordsRange.THIS_MONTH,
                            onClick = { viewModel.setRange(RecordsRange.THIS_MONTH) },
                            label = { Text("本月") },
                        )
                        FilterChip(
                            selected = filter.range == RecordsRange.LAST_MONTH,
                            onClick = { viewModel.setRange(RecordsRange.LAST_MONTH) },
                            label = { Text("上月") },
                        )
                    }
                    Text("分类", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allCategories, key = { it.id }) { c ->
                            FilterChip(
                                selected = filter.categoryId == c.id,
                                onClick = {
                                    viewModel.setCategory(if (filter.categoryId == c.id) null else c.id)
                                },
                                label = { Text(c.name) },
                            )
                        }
                    }
                    Text("账户", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(accounts, key = { it.id }) { a ->
                            FilterChip(
                                selected = filter.accountId == a.id,
                                onClick = {
                                    viewModel.setAccount(if (filter.accountId == a.id) null else a.id)
                                },
                                label = { Text(a.name) },
                            )
                        }
                    }
                    if (products.isNotEmpty()) {
                        Text("商品", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(products, key = { it.id }) { p ->
                                FilterChip(
                                    selected = filter.productId == p.id,
                                    onClick = {
                                        viewModel.setProduct(if (filter.productId == p.id) null else p.id)
                                    },
                                    label = { Text(p.name) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilters = false }) { Text("好了") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetFilter()
                    showFilters = false
                }) { Text("全部清除") }
            },
        )
    }

    previewReceipt?.let { path ->
        val bitmapState = remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(path) {
            bitmapState.value = withContext(Dispatchers.IO) {
                ImageUtils.decodeSampled(path, 1440)?.asImageBitmap()
            }
        }
        val bitmap = bitmapState.value
        Dialog(onDismissRequest = { previewReceipt = null }) {
            Surface(shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shown = bitmap
                    if (shown != null) {
                        Image(
                            bitmap = shown,
                            contentDescription = "单据照片",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text("图片找不到了，可能已经被清理。")
                    }
                    TextButton(onClick = { previewReceipt = null }) { Text("关闭") }
                }
            }
        }
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

/** 一天的头部：日期 + 当天收支小计 */
@Composable
private fun DayHeader(day: LocalDate, records: List<TransactionWithNames>, truncated: Boolean = false) {
    val income = records.filter { it.isIncome }.sumOf { it.amountCents }
    val expense = records.filter { !it.isIncome }.sumOf { it.amountCents }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.format(dayFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "收 ¥${formatCents(income)} · 支 ¥${formatCents(expense)}" + if (truncated) "（当天还有更多）" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (truncated) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordRow(
    item: TransactionWithNames,
    onClick: () -> Unit,
    onPreviewReceipt: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = if (item.isIncome) incomeColor() else expenseColor()
    val sign = if (item.isIncome) "+" else "-"
    // 录入时间与记账时间差超过 1 分钟才单独显示，避免重复信息
    val showCreatedAt = kotlin.math.abs(item.createdAtMillis - item.timeMillis) >= 60_000L

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.categoryName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${item.accountName}  ·  ${formatDateTime(item.timeMillis)}" +
                    if (item.receiptPath != null) "  ·  有单据" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showCreatedAt) {
                Text(
                    text = "录入于 ${formatCreatedAt(item.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.productName != null) {
                val qty = item.quantity?.let { "  ×$it" } ?: ""
                Text(
                    text = "商品：${item.productName}$qty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.note.isNotEmpty()) {
                Text(
                    text = "备注：${item.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.receiptPath != null) {
                TextButton(
                    onClick = onPreviewReceipt,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) { Text("看单据", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Text(
            text = "$sign¥${formatCents(item.amountCents)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}