package com.yuanguangbb.ledger.ui.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuanguangbb.ledger.data.db.CustomerWithDebt
import com.yuanguangbb.ledger.data.db.DebtEntity
import com.yuanguangbb.ledger.domain.formatCents
import com.yuanguangbb.ledger.domain.parseYuanToCents
import com.yuanguangbb.ledger.ui.AppViewModelProvider
import com.yuanguangbb.ledger.ui.theme.expenseColor
import com.yuanguangbb.ledger.ui.theme.incomeColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val amountPattern = Regex("""\d{0,9}(\.\d{0,2})?""")

@Composable
fun DebtsScreen(
    viewModel: DebtsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var showAddCustomer by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        val id = selectedId
        if (id != null) {
            CustomerDetail(
                customerId = id,
                name = selectedName,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onBack = { selectedId = null },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                val total = customers.sumOf { it.outstandingCents }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("赊账本", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "还没收回来 ¥" + formatCents(total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (total > 0) expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "记赊账会同时记一笔收入（赊出去就算卖了），收款只冲欠款、不重复记收入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showAddCustomer = true }) { Text("新增客户") }
                }

                if (customers.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize()
                            .padding(start = 32.dp, end = 32.dp, bottom = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有赊账客户。\n先「新增客户」，再给他记一笔赊账。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    ) {
                        items(customers, key = { it.customerId }) { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedId = c.customerId
                                        selectedName = c.name
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        buildString {
                                            if (c.phone.isNotEmpty()) append(c.phone).append("  ·  ")
                                            append("${c.debtCount} 笔")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = if (c.outstandingCents > 0) {
                                        "欠 ¥" + formatCents(c.outstandingCents)
                                    } else {
                                        "已还清"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (c.outstandingCents > 0) expenseColor() else incomeColor(),
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        )
    }

    if (showAddCustomer) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCustomer = false },
            title = { Text("新增客户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名字/称呼") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("电话（可不填）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addCustomer(name, phone) { showAddCustomer = false }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddCustomer = false }) { Text("取消") } },
        )
    }

    // 选中客户后，若客户被删了（列表里找不到），退回列表
    LaunchedEffect(customers, selectedId) {
        val id = selectedId
        if (id != null && customers.isNotEmpty() && customers.none { it.customerId == id }) {
            selectedId = null
        }
    }
}

@Composable
private fun CustomerDetail(
    customerId: Long,
    name: String,
    viewModel: DebtsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val flow = remember(customerId) { viewModel.debtsOf(customerId) }
    val debts by flow.collectAsStateWithLifecycle(emptyList<DebtEntity>())
    var showAddDebt by remember { mutableStateOf(false) }
    var confirmDeleteCustomer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outstanding = debts.filter { it.settledAtMillis == null }.sumOf { it.amountCents }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "还没收回来 ¥" + formatCents(outstanding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outstanding > 0) expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { confirmDeleteCustomer = true }) {
                Text("删客户", color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedButton(
            onClick = { showAddDebt = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) { Text("记一笔赊账") }

        if (debts.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "这个客户还没有赊账记录。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
                items(debts, key = { it.id }) { d ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "¥" + formatCents(d.amountCents),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (d.settledAtMillis == null) {
                                TextButton(onClick = {
                                    viewModel.settle(d.id)
                                    scope.launch { snackbarHostState.showSnackbar("已记收款") }
                                }) { Text("收款") }
                            } else {
                                TextButton(onClick = { viewModel.unsettle(d.id) }) { Text("撤销收款") }
                            }
                            TextButton(onClick = { viewModel.deleteDebt(d.id) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            Instant.ofEpochMilli(d.timeMillis).atZone(ZoneId.systemDefault())
                                .format(dayFormatter) +
                                if (d.settledAtMillis != null) "  ·  已还清" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (d.settledAtMillis != null) incomeColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (d.note.isNotEmpty()) {
                            Text(
                                d.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDebt) {
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        val cents = parseYuanToCents(amountText)
        AlertDialog(
            onDismissRequest = { showAddDebt = false },
            title = { Text("记一笔赊账") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (amountPattern.matches(it)) amountText = it },
                        label = { Text("金额（元）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（可不填）") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = cents ?: return@TextButton
                        viewModel.addDebt(
                            customerId = customerId,
                            customerName = name,
                            amountCents = value,
                            timeMillis = System.currentTimeMillis(),
                            note = note,
                        ) { showAddDebt = false }
                    },
                    enabled = cents != null,
                ) { Text("记上") }
            },
            dismissButton = { TextButton(onClick = { showAddDebt = false }) { Text("取消") } },
        )
    }

    if (confirmDeleteCustomer) {
        AlertDialog(
            onDismissRequest = { confirmDeleteCustomer = false },
            title = { Text("删除这个客户？") },
            text = { Text("客户和他的 ${debts.size} 笔赊账记录会一起删掉，赊账时自动记的那笔收入会进回收站，删除后可以从回收站恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomer(customerId)
                    confirmDeleteCustomer = false
                    onBack()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCustomer = false }) { Text("取消") }
            },
        )
    }
}