package com.example.ledger.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ledger.LedgerApplication
import com.example.ledger.ui.add.AddViewModel
import com.example.ledger.ui.debts.DebtsViewModel
import com.example.ledger.ui.home.HomeViewModel
import com.example.ledger.ui.products.ProductsViewModel
import com.example.ledger.ui.records.RecordsViewModel
import com.example.ledger.ui.report.ReportViewModel
import com.example.ledger.ui.settings.SettingsViewModel

private fun CreationExtras.ledgerApp(): LedgerApplication =
    this[APPLICATION_KEY] as LedgerApplication

/** 统一创建 ViewModel，并把 Repository / 设置传进去。 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(ledgerApp().repository, ledgerApp().settings) }
        initializer { RecordsViewModel(ledgerApp().repository) }
        initializer { AddViewModel(ledgerApp().repository) }
        initializer { ProductsViewModel(ledgerApp().repository) }
        initializer { DebtsViewModel(ledgerApp().repository) }
        initializer { ReportViewModel(ledgerApp().repository) }
        initializer {
            SettingsViewModel(
                repository = ledgerApp().repository,
                settings = ledgerApp().settings,
                backup = ledgerApp().backupManager,
            )
        }
    }
}