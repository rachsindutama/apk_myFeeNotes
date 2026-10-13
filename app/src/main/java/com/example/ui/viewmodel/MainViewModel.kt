package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Transaction
import com.example.data.model.User
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UserRepository
import com.example.data.session.SessionManager
import com.example.data.session.UserSession
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.SyncResult
import com.example.util.ReportExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(db.userDao())
    private val transactionRepository = TransactionRepository(db.transactionDao())
    private val sessionManager = SessionManager(application)

    // Auth States
    private val _currentUser = MutableStateFlow<UserSession?>(sessionManager.getLoggedInUser())
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private val _authStateMessage = MutableStateFlow<String?>(null)
    val authStateMessage: StateFlow<String?> = _authStateMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // Sync States
    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    // Filtering State
    private val _selectedMonth = MutableStateFlow<String>("Semua Bulan") // format "MM-yyyy" or "Semua Bulan"
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String>("Semua Kategori")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Export State
    private val _exportedFileUri = MutableStateFlow<Uri?>(null)
    val exportedFileUri: StateFlow<Uri?> = _exportedFileUri.asStateFlow()

    // Raw Transactions Flow for logged in user
    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = _currentUser
        .flatMapLatest { session ->
            if (session != null) {
                transactionRepository.getTransactionsByUser(session.email)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Unsynced count Flow
    val unsyncedCount: StateFlow<Int> = transactions
        .combine(flowOf(0)) { list, _ ->
            list.count { !it.isSynced }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Filtered Transactions Flow
    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        transactions,
        _selectedMonth,
        _selectedCategory
    ) { txList, month, category ->
        var filtered = txList

        if (month != "Semua Bulan") {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            filtered = filtered.filter { tx ->
                val txMonthStr = sdf.format(Date(tx.date))
                txMonthStr == month
            }
        }

        if (category != "Semua Kategori") {
            filtered = filtered.filter { tx ->
                tx.category.lowercase() == category.lowercase()
            }
        }

        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Month lists extracted from existing transactions
    val availableMonths: StateFlow<List<String>> = transactions
        .combine(flowOf(emptyList<String>())) { list, _ ->
            val sdf = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            list.map { sdf.format(Date(it.date)) }.distinct().sortedDescending()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category lists extracted from transactions
    val availableCategories: StateFlow<List<String>> = transactions
        .combine(flowOf(emptyList<String>())) { list, _ ->
            list.map { it.category }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Income & Expense details for KPI
    val totalIncome: StateFlow<Double> = filteredTransactions
        .combine(flowOf(0.0)) { list, _ ->
            list.filter { it.type == "INCOME" }.sumOf { it.amount }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = filteredTransactions
        .combine(flowOf(0.0)) { list, _ ->
            list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        // Automatically seed mock cloud database if user logs in
        _currentUser.value?.let {
            CloudSyncManager.seedCloudData(it.email)
        }
    }

    // AUTH METHODS
    fun setAuthMessage(text: String?) {
        _authStateMessage.value = text
    }

    fun loginWithEmail(email: String, pwhash: String) {
        if (email.isBlank() || pwhash.isBlank()) {
            _authStateMessage.value = "Email dan password tidak boleh kosong!"
            return
        }
        _isAuthLoading.value = true
        viewModelScope.launch {
            val user = userRepository.getUserByEmail(email.lowercase().trim())
            if (user == null) {
                _authStateMessage.value = "Akun belum terdaftar!"
            } else if (user.isGoogleAccount) {
                _authStateMessage.value = "Gunakan login akun Google untuk email ini!"
            } else if (user.passwordHash != pwhash) {
                _authStateMessage.value = "Password salah!"
            } else {
                sessionManager.saveUserSession(user.email, user.name, false)
                _currentUser.value = sessionManager.getLoggedInUser()
                CloudSyncManager.seedCloudData(user.email)
                _authStateMessage.value = "Login berhasil!"
            }
            _isAuthLoading.value = false
        }
    }

    fun registerWithEmail(email: String, name: String, pwhash: String) {
        if (email.isBlank() || name.isBlank() || pwhash.isBlank()) {
            _authStateMessage.value = "Semua field harus diisi!"
            return
        }
        if (pwhash.length < 5) {
            _authStateMessage.value = "Password minimal terdiri dari 5 karakter!"
            return
        }
        _isAuthLoading.value = true
        viewModelScope.launch {
            val exist = userRepository.getUserByEmail(email.lowercase().trim())
            if (exist != null) {
                _authStateMessage.value = "Email ini sudah terdaftar!"
            } else {
                val newUser = User(
                    email = email.lowercase().trim(),
                    name = name.trim(),
                    passwordHash = pwhash,
                    isGoogleAccount = false
                )
                userRepository.insertUser(newUser)
                sessionManager.saveUserSession(newUser.email, newUser.name, false)
                _currentUser.value = sessionManager.getLoggedInUser()
                CloudSyncManager.seedCloudData(newUser.email)
                _authStateMessage.value = "Registrasi Berhasil!"
            }
            _isAuthLoading.value = false
        }
    }

    fun loginWithGoogle(email: String, name: String) {
        _isAuthLoading.value = true
        viewModelScope.launch {
            val targetEmail = email.lowercase().trim()
            var user = userRepository.getUserByEmail(targetEmail)
            if (user == null) {
                user = User(
                    email = targetEmail,
                    name = name.trim(),
                    passwordHash = "",
                    isGoogleAccount = true
                )
                userRepository.insertUser(user)
            }
            sessionManager.saveUserSession(user.email, user.name, true)
            _currentUser.value = sessionManager.getLoggedInUser()
            CloudSyncManager.seedCloudData(user.email)
            _authStateMessage.value = "Login Akun Google Berhasil!"
            _isAuthLoading.value = false
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _currentUser.value = null
        _syncState.value = SyncStatus.Idle
        _selectedMonth.value = "Semua Bulan"
        _selectedCategory.value = "Semua Kategori"
    }

    // TRANSACTION METHODS
    fun addTransaction(amount: Double, type: String, category: String, note: String, customDate: Long? = null) {
        val email = _currentUser.value?.email ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            val tx = Transaction(
                userId = email,
                amount = amount,
                type = type,
                category = category,
                date = customDate ?: System.currentTimeMillis(),
                note = note.trim(),
                isSynced = false
            )
            transactionRepository.insertTransaction(tx)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
        }
    }

    // FILTER ACTIONS
    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    // SYNC METHODS
    fun forceSync() {
        val user = _currentUser.value ?: return
        if (_syncState.value == SyncStatus.Syncing) return

        _syncState.value = SyncStatus.Syncing
        viewModelScope.launch {
            val unsynced = transactionRepository.getUnsyncedTransactions(user.email)
            when (val result = CloudSyncManager.syncData(user.email, unsynced)) {
                is SyncResult.Success -> {
                    // Update local database with items downloaded
                    transactionRepository.insertTransactions(result.pulledItems)
                    transactionRepository.markAllAsSynced(user.email)
                    userRepository.updateSyncTime(user.email, System.currentTimeMillis())
                    
                    _syncState.value = SyncStatus.Success(
                        pushed = result.pushedCount,
                        pulled = result.pulledItems.size,
                        time = System.currentTimeMillis()
                    )
                }
                is SyncResult.Error -> {
                    _syncState.value = SyncStatus.Error(result.message)
                }
            }
        }
    }

    // EXPORT REPORT METHODS
    fun exportReport(context: Context, format: String) {
        val txList = filteredTransactions.value
        val periodName = _selectedMonth.value.let { m ->
            if (m == "Semua Bulan") "Seluruh Transaksi" else formatMonthYear(m)
        }
        
        viewModelScope.launch {
            val uri = if (format.lowercase() == "pdf") {
                ReportExporter.exportToPdf(context, txList, periodName)
            } else {
                ReportExporter.exportToCsv(context, txList)
            }
            _exportedFileUri.value = uri
        }
    }

    fun clearExportedUri() {
        _exportedFileUri.value = null
    }

    // Helper formatting UI labels
    fun formatMonthYear(monthYearCode: String): String {
        if (monthYearCode == "Semua Bulan") return "Semua Bulan"
        return try {
            val parser = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            val formatter = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
            val date = parser.parse(monthYearCode) ?: return monthYearCode
            formatter.format(date)
        } catch (e: Exception) {
            monthYearCode
        }
    }
}

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val pushed: Int, val pulled: Int, val time: Long) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
