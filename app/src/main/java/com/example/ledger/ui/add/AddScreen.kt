package com.example.ledger.ui.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.data.db.TransactionEntity
import com.example.ledger.domain.formatCents
import com.example.ledger.domain.parseYuanToCents
import com.example.ledger.ui.AppViewModelProvider
import com.example.ledger.ui.common.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val amountPattern = Regex("""\d{0,9}(\.\d{0,2})?""")
private val quantityPattern = Regex("""\d{0,6}""")
private val dateLabelFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** DatePicker 的初始值要的是「所选日期的 UTC 零点」 */
private fun todayUtcMidnightMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** 把本地时间转成 DatePicker 认的 UTC 零点 */
private fun localDateToUtcMillis(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** DatePicker 选出来的是 UTC 零点，显示时按 UTC 取年月日，避免跨时区差一天 */
private fun pickedDateLabel(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(dateLabelFormatter)

/** 日期用选的那天，时分用 [timeSourceMillis] 的时分（新账=现在，改账=原来那一刻） */
private fun combineDateWithTime(utcDateMillis: Long, timeSourceMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val time = Instant.ofEpochMilli(timeSourceMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    onClose: () -> Unit,
    editId: Long? = null,
    viewModel: AddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isIncome by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var productName by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    // 补记/改日期用：null = 今天
    var pickedDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var receiptPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 原账的记账时间，改账时用来保留原来的时分
    var originalTimeMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var loadedId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(editId) {
        editId?.let { viewModel.load(it) }
    }

    // 编辑模式：数据到了填一次表单
    LaunchedEffect(editing, editId, products) {
        val e: TransactionEntity = editing ?: return@LaunchedEffect
        if (editId == null || loadedId == e.id) return@LaunchedEffect
        // 这笔账带商品时，等商品列表加载好再填，否则会丢商品名
        if (products.isEmpty() && e.productId != null) return@LaunchedEffect
        isIncome = e.isIncome
        amountText = formatCents(e.amountCents)
        categoryId = e.categoryId
        accountId = e.accountId
        note = e.note
        productName = products.firstOrNull { it.id == e.productId }?.name.orEmpty()
        quantityText = e.quantity?.toString().orEmpty()
        pickedDateMillis = localDateToUtcMillis(e.timeMillis)
        receiptPath = e.receiptPath
        originalTimeMillis = e.timeMillis
        loadedId = e.id
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { ReceiptStore.save(context, uri) }
                if (saved != null) receiptPath = saved
            }
        }
    }

    val categories = if (isIncome) incomeCategories else expenseCategories
    // 没选过（或切换了收入/支出）时，默认选第一个
    val effectiveCategoryId = categories.firstOrNull { it.id == categoryId }?.id
        ?: categories.firstOrNull()?.id
    val effectiveAccountId = accounts.firstOrNull { it.id == accountId }?.id
        ?: accounts.firstOrNull()?.id
    val amountCents = parseYuanToCents(amountText)
    val productFilled = productName.isNotBlank()
    val quantity = quantityText.toIntOrNull()?.takeIf { it > 0 }
    // 填了商品就必须填数量，否则没法算每件的进价和售价
    val canSave = amountCents != null && effectiveCategoryId != null && effectiveAccountId != null &&
        (!productFilled || quantity != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(if (editing != null) "修改这笔账" else "记一笔", style = MaterialTheme.typography.titleLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("支出") })
            FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("收入") })
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { if (amountPattern.matches(it)) amountText = it },
            label = { Text("金额（元）") },
            prefix = { Text("¥") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("分类", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { c ->
                FilterChip(
                    selected = c.id == effectiveCategoryId,
                    onClick = { categoryId = c.id },
                    label = { Text(c.name) },
                )
            }
        }

        Text("账户", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.id }) { a ->
                FilterChip(
                    selected = a.id == effectiveAccountId,
                    onClick = { accountId = a.id },
                    label = { Text(a.name) },
                )
            }
        }

        Text("日期", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(pickedDateMillis?.let { pickedDateLabel(it) } ?: "今天")
            }
            if (pickedDateMillis != null) {
                TextButton(onClick = { pickedDateMillis = null }) { Text("改回今天") }
            }
        }
        Text(
            text = if (editing != null) {
                "只改日期的话，时分沿用原来那一笔的时间。"
            } else {
                "补记前几天的账就选日期；时分按现在的时间记。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("商品（可不填）", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = productName,
            onValueChange = { productName = it },
            label = { Text("商品名，如：可乐") },
            supportingText = { Text("进货或销售时填上，「商品」页就能看到每样货赚多少") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (products.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { p ->
                    FilterChip(
                        selected = p.name == productName.trim(),
                        onClick = {
                            productName = if (p.name == productName.trim()) "" else p.name
                        },
                        label = { Text(p.name) },
                    )
                }
            }
        }
        if (productFilled) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { if (quantityPattern.matches(it)) quantityText = it },
                label = { Text("数量（件）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("备注（可不填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("单据（可不填）", style = MaterialTheme.typography.titleSmall)
        val bitmapState = remember(receiptPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(receiptPath) {
            bitmapState.value = withContext(Dispatchers.IO) {
                receiptPath?.let { ImageUtils.decodeSampled(it, 720)?.asImageBitmap() }
            }
        }
        val bitmap = bitmapState.value
        val shownBitmap = bitmap
        if (shownBitmap != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    bitmap = shownBitmap,
                    contentDescription = "单据照片",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
                TextButton(onClick = { receiptPath = null }) { Text("移除") }
            }
        } else {
            OutlinedButton(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Text("选一张单据照片")
            }
        }

        Button(
            onClick = {
                if (canSave) {
                    val now = System.currentTimeMillis()
                    val baseTime = originalTimeMillis
                    val timeMillis = when {
                        pickedDateMillis == null -> if (baseTime != null) {
                            // 改账但没动日期：保留原来的精确时间
                            combineDateWithTime(localDateToUtcMillis(baseTime), baseTime)
                        } else {
                            now
                        }
                        else -> combineDateWithTime(pickedDateMillis!!, baseTime ?: now)
                    }
                    viewModel.save(
                        isIncome = isIncome,
                        amountCents = amountCents!!,
                        categoryId = effectiveCategoryId!!,
                        accountId = effectiveAccountId!!,
                        note = note,
                        productName = productName,
                        quantity = quantity,
                        receiptPath = receiptPath,
                        timeMillis = timeMillis,
                    )
                    onClose()
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                if (editing != null) "保存修改" else "保存",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pickedDateMillis ?: todayUtcMidnightMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}