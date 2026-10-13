package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SyncStatus
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel, onShare = { uri, mimeType ->
                    shareFile(uri, mimeType)
                })
            }
        }
    }

    private fun shareFile(uri: Uri, mimeType: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Laporan Keuangan"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    onShare: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authStateMessage by viewModel.authStateMessage.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val exportedUri by viewModel.exportedFileUri.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Transaksi, 1: Laporan, 2: Sinkronisasi, 3: Profil
    var showAddDialog by remember { mutableStateOf(false) }

    // Trigger share sheet when file is exported
    LaunchedEffect(exportedUri) {
        exportedUri?.let { uri ->
            val isPdf = uri.toString().lowercase().endsWith("pdf")
            val mime = if (isPdf) "application/pdf" else "text/csv"
            onShare(uri, mime)
            viewModel.clearExportedUri()
            Toast.makeText(context, "Laporan berhasil diekspor!", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Auth Toast Messages
    LaunchedEffect(authStateMessage) {
        authStateMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.setAuthMessage(null)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentUser != null) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "MyFeeNotes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "CLOUD SYNCED",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Initials circle (JD equivalent)
                    val initials = currentUser?.name?.trim()?.split("\\s+".toRegex())
                        ?.mapNotNull { it.firstOrNull()?.toString() }
                        ?.joinToString("")
                        ?.take(2)
                        ?.uppercase() ?: "US"

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (currentUser != null) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Transaksi") },
                        label = { Text("Transaksi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.PieChart, contentDescription = "Laporan") },
                        label = { Text("Laporan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sinkronisasi") },
                        label = { Text("Sync", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    NavigationBarItem(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profil") },
                        label = { Text("Profil", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentUser != null && activeTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Transaksi", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    viewModel = viewModel,
                    isLoading = isAuthLoading
                )
            } else {
                when (activeTab) {
                    0 -> DashboardTab(viewModel = viewModel)
                    1 -> ReportTab(viewModel = viewModel)
                    2 -> SyncTab(viewModel = viewModel)
                    3 -> ProfileTab(user = user, viewModel = viewModel)
                }
            }

            if (showAddDialog) {
                AddTransactionDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { amount, type, category, note, date ->
                        viewModel.addTransaction(amount, type, category, note, date)
                        showAddDialog = false
                        Toast.makeText(context, "Transaksi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    isLoading: Boolean
) {
    var isRegisterState by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showGoogleAccounts by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App branding header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = "MyFeeNotes",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Solusi Cerdas Pengelolaan Cashflow Keuangan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic Auth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isRegisterState) "Buat Akun Baru" else "Masuk Akun",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isRegisterState) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Lengkap") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Alamat Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Kata Sandi") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Button(
                            onClick = {
                                if (isRegisterState) {
                                    viewModel.registerWithEmail(email, name, password)
                                } else {
                                    viewModel.loginWithEmail(email, password)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isRegisterState) "Daftar Sekarang" else "Masuk")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRegisterState) "Sudah punya akun? " else "Belum punya akun? ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (isRegisterState) "Masuk" else "Daftar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable {
                                isRegisterState = !isRegisterState
                            }
                        )
                    }
                }
            }

            Text(
                text = "atau masuk cepat dengan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // Google account selection simulation
            OutlinedButton(
                onClick = { showGoogleAccounts = true },
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Google Account")
                }
            }
        }
    }

    if (showGoogleAccounts) {
        Dialog(onDismissRequest = { showGoogleAccounts = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pilih Akun Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Profile User 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loginWithGoogle("Rachsindutama@gmail.com", "Rach Sindutama")
                                showGoogleAccounts = false
                            }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Rach Sindutama", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Rachsindutama@gmail.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    // Profile User 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loginWithGoogle("aistudio.tester@gmail.com", "AI Studio Tester")
                                showGoogleAccounts = false
                            }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("AI Studio Tester", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("aistudio.tester@gmail.com", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showGoogleAccounts = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardTab(viewModel: MainViewModel) {
    val txList by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val rawList by viewModel.transactions.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val balance = totalIncome - totalExpense

    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()

    var showMonthFilter by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Geometric Balance KPI Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
            ) {
                // Spherical Geometric decoration in background
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 32.dp, y = 32.dp)
                        .size(128.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Saldo Bersih",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "IDR",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = formatIndoRupiah(balance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Income Block
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "PEMASUKAN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = formatIndoRupiah(totalIncome),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Expense Block
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "PENGELUARAN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = formatIndoRupiah(totalExpense),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter Header badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterBadge(
                title = if (selectedMonth == "Semua Bulan") "Bulan: Semua" else "Bulan: ${viewModel.formatMonthYear(selectedMonth)}",
                isActive = selectedMonth != "Semua Bulan",
                onClick = { showMonthFilter = true }
            )
            FilterBadge(
                title = if (selectedCategory == "Semua Kategori") "Kategori: Semua" else "Kat: $selectedCategory",
                isActive = selectedCategory != "Semua Kategori",
                onClick = { showCategoryFilter = true }
            )
        }

        // Dynamic Logs / Transaction List with beautiful visual accents
        Text(
            text = "Daftar Transaksi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (txList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text("Belum Ada Transaksi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Silakan tambah baru melalu tombol (+) atau lakukan Sinkronisasi di navigasi Sync untuk mengunduh cloud data.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(txList) { tx ->
                    TransactionItemRow(transaction = tx, onDelete = {
                        viewModel.deleteTransaction(tx)
                    })
                }
            }
        }
    }

    // Modal month list filter dialog
    if (showMonthFilter) {
        SimpleFilterDialog(
            title = "Pilih Periode Laporan",
            items = listOf("Semua Bulan") + availableMonths,
            formatLabel = { code ->
                if (code == "Semua Bulan") "Semua Bulan" else viewModel.formatMonthYear(code)
            },
            onSelect = {
                viewModel.selectMonth(it)
                showMonthFilter = false
            },
            onDismiss = { showMonthFilter = false }
        )
    }

    // Modal category filter dialog
    if (showCategoryFilter) {
        SimpleFilterDialog(
            title = "Pilih Kategori",
            items = listOf("Semua Kategori") + availableCategories,
            formatLabel = { it },
            onSelect = {
                viewModel.selectCategory(it)
                showCategoryFilter = false
            },
            onDismiss = { showCategoryFilter = false }
        )
    }
}

@Composable
fun ReportTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val txList by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }

    val incomeCategories = txList.filter { it.type == "INCOME" }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    val expenseCategories = txList.filter { it.type == "EXPENSE" }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Periode Saat Ini", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (selectedMonth == "Semua Bulan") "Seluruh Transaksi" else viewModel.formatMonthYear(selectedMonth),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { showExportSheet = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ekspor", fontSize = 13.sp)
                    }
                }
            }
        }

        // Section Income Category Statistics
        item {
            Text("Pemasukan Berdasarkan Kategori", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (incomeCategories.isEmpty()) {
            item {
                Text("Tidak ada transaksi pemasukan pada periode ini.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            items(incomeCategories) { (cat, valSum) ->
                val progressVal = if (totalIncome > 0) (valSum / totalIncome).toFloat() else 0f
                CategoryProgressCard(category = cat, amount = valSum, progress = progressVal, isIncome = true)
            }
        }

        // Section Expense Category Statistics
        item {
            Text("Pengeluaran Berdasarkan Kategori", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (expenseCategories.isEmpty()) {
            item {
                Text("Tidak ada transaksi pengeluaran pada periode ini.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            items(expenseCategories) { (cat, valSum) ->
                val progressVal = if (totalExpense > 0) (valSum / totalExpense).toFloat() else 0f
                CategoryProgressCard(category = cat, amount = valSum, progress = progressVal, isIncome = false)
            }
        }
    }

    if (showExportSheet) {
        Dialog(onDismissRequest = { showExportSheet = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Ekspor Laporan Cashflow",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Silakan pilih format penyimpanan file laporan cashflow untuk dibagikan ke media sosial, PDF, Excel atau diunduh langsung.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // PDF Option
                        Button(
                            onClick = {
                                viewModel.exportReport(context, "pdf")
                                showExportSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF Format")
                        }

                        // CSV Option
                        Button(
                            onClick = {
                                viewModel.exportReport(context, "csv")
                                showExportSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CSV Format")
                        }
                    }

                    TextButton(
                        onClick = { showExportSheet = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncTab(viewModel: MainViewModel) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (unsyncedCount > 0) MaterialTheme.colorScheme.errorContainer 
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (unsyncedCount > 0) Icons.Default.CloudQueue else Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (unsyncedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Sinkronisasi Cloud",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (unsyncedCount > 0) {
                        "Terdapat $unsyncedCount transaksi lokal yang belum diunggah ke cloud database."
                    } else {
                        "Semua data transaksi tersinkronisasi sempurna pada Cloud!"
                    },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (syncState is SyncStatus.Syncing) {
                    CircularProgressIndicator()
                    Text("Sinkronisasi sedang berlangsung...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = { viewModel.forceSync() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulasikan Sinkron Sekarang")
                    }
                }
            }
        }

        // Live logs and info cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Informasi Status Sinkronisasi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when (val state = syncState) {
                    is SyncStatus.Success -> {
                        SyncLogItem(label = "Detail Berhasil", content = "Pushed: ${state.pushed} tx | Pulled: ${state.pulled} tx", color = Color.Green)
                        SyncLogItem(label = "Waktu Sukses", content = SimpleDateFormat("HH:mm:ss dd-MM-yyyy", Locale.getDefault()).format(Date(state.time)), color = Color.Gray)
                    }
                    is SyncStatus.Error -> {
                        SyncLogItem(label = "Kesalahan teknis", content = state.message, color = Color.Red)
                    }
                    else -> {
                        SyncLogItem(label = "Status", content = "Belum memulai sinkronisasi", color = Color.Gray)
                    }
                }

                SyncLogItem(label = "Metode Eksternal", content = "Secure Simulated Cross-Device Ledger Engine v1.2", color = Color.Gray)
            }
        }
    }
}

@Composable
fun SyncLogItem(label: String, content: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(content, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ProfileTab(
    user: com.example.data.session.UserSession,
    viewModel: MainViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        }

        Text(
            text = user.name,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pendaftaran Akun", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(if (user.isGoogle) "Terkoneksi Google" else "Email Tradisional", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                horizontalProfileLine()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Email", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(user.email, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(0.7f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Keluar Akun")
        }
    }
}

@Composable
fun horizontalProfileLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun FilterBadge(title: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant, 
                RoundedCornerShape(50.dp)
            )
            .border(
                1.dp, 
                if (isActive) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, 
                RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold, 
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.ArrowDropDown, 
                contentDescription = null, 
                modifier = Modifier.size(16.dp), 
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryProgressCard(
    category: String,
    amount: Double,
    progress: Float,
    isIncome: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isIncome) Color.Green else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(category, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(formatIndoRupiah(amount), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isIncome) Color(0xFF2E7D32) else Color(0xFFC62828),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = String.format("%.1f%% dari total", progress * 100),
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: Transaction,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    val iconGroup = when (transaction.category) {
        "Gaji" -> Icons.Default.AccountBalanceWallet to Color(0xFF2E7D32)
        "Investasi" -> Icons.Default.TrendingUp to Color(0xFF009688)
        "Bisnis / Jualan" -> Icons.Default.Storefront to Color(0xFFF57C00)
        "Makanan" -> Icons.Default.Restaurant to Color(0xFFC62828)
        "Belanja" -> Icons.Default.ShoppingBag to Color(0xFF9C27B0)
        "Transportasi" -> Icons.Default.DirectionsCar to Color(0xFF2196F3)
        "Utilitas" -> Icons.Default.ReceiptLong to Color(0xFF607D8B)
        "Hiburan" -> Icons.Default.Movie to Color(0xFFE91E63)
        else -> Icons.Default.MoreHoriz to Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showConfirmDelete = true }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        iconGroup.first,
                        contentDescription = transaction.category,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = transaction.note.ifBlank { transaction.category },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(transaction.category, fontSize = 10.sp, color = Color.Gray)
                        Text("•", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(transaction.date)),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val isInc = transaction.type == "INCOME"
                Text(
                    text = "${if (isInc) "+" else "-"} ${formatIndoRupiah(transaction.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (isInc) Color(0xFF2E7D32) else Color(0xFFC62828)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (transaction.isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (transaction.isSynced) Color.Green else Color.Gray
                )
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Hapus Transaksi", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus transaksi '${transaction.note.ifBlank { transaction.category }}' ini?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun SimpleFilterDialog(
    title: String,
    items: List<String>,
    formatLabel: (String) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.heightIn(max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(formatLabel(item), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }

                TextButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String, Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("INCOME") } // INCOME or EXPENSE
    var selectionCategory by remember { mutableStateOf("Gaji") }
    var note by remember { mutableStateOf("") }
    
    val currentCalendar = Calendar.getInstance()
    var selectedDate by remember { mutableStateOf(currentCalendar.timeInMillis) }

    val incomeCats = listOf("Gaji", "Investasi", "Bisnis / Jualan", "Lainnya")
    val expenseCats = listOf("Makanan", "Belanja", "Transportasi", "Utilitas", "Hiburan", "Lainnya")

    LaunchedEffect(type) {
        selectionCategory = if (type == "INCOME") "Gaji" else "Makanan"
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Tambah Transaksi Baru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // INCOME / EXPENSE switch
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (type == "INCOME") Color.Green.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { type = "INCOME" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Pemasukan", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "INCOME") Color.DarkGray else Color.Gray)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (type == "EXPENSE") Color.Red.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { type = "EXPENSE" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Pengeluaran", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (type == "EXPENSE") Color.DarkGray else Color.Gray)
                        }
                    }
                }

                // Amount Field
                item {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Jumlah (Rp)") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Category selection buttons
                item {
                    Text("Pilih Kategori", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val currentCats = if (type == "INCOME") incomeCats else expenseCats
                        currentCats.forEach { cat ->
                            val isChosen = cat == selectionCategory
                            Box(
                                modifier = Modifier
                                    .clickable { selectionCategory = cat }
                                    .background(
                                        if (isChosen) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(50.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    cat, 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
                                )
                            }
                        }
                    }
                }

                // Date Picker Simulation
                item {
                    Text("Tanggal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(selectedDate)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Easy helper options for selecting Today, Yesterday, or custom
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Kemarin",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, -1)
                                        selectedDate = cal.timeInMillis
                                    }
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )

                            Text(
                                "Hari ini",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        selectedDate = System.currentTimeMillis()
                                    }
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Notes Description Field
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Keterangan Tambahan") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Action buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onDismiss() }) {
                            Text("Batal")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                val parseAmt = amount.toDoubleOrNull() ?: 0.0
                                if (parseAmt > 0.0) {
                                    onConfirm(parseAmt, type, selectionCategory, note, selectedDate)
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

// Helpers
fun formatIndoRupiah(amount: Double): String {
    return "Rp " + String.format(Locale("id", "ID"), "%,.0f", amount)
}
