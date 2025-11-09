package com.syncone.health.presentation.monitor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncone.health.domain.model.SmsThread
import com.syncone.health.domain.model.enums.UrgencyLevel
import com.syncone.health.presentation.theme.*
import com.syncone.health.util.DateTimeFormatter
import com.syncone.health.util.PhoneNumberFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsMonitorScreen(
    onThreadClick: (Long) -> Unit,
    viewModel: SmsMonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = OnPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            FilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) }
            )

            // Thread list
            when (val state = uiState) {
                is SmsMonitorViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SmsMonitorViewModel.UiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No threads yet", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is SmsMonitorViewModel.UiState.Success -> {
                    ThreadList(
                        threads = state.threads,
                        onThreadClick = onThreadClick
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    selectedFilter: SmsMonitorViewModel.ThreadFilter,
    onFilterSelected: (SmsMonitorViewModel.ThreadFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmsMonitorViewModel.ThreadFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
fun ThreadList(
    threads: List<SmsThread>,
    onThreadClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(threads, key = { it.id }) { thread ->
            ThreadListItem(
                thread = thread,
                onClick = { onThreadClick(thread.id) }
            )
        }
    }
}

@Composable
fun ThreadListItem(
    thread: SmsThread,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val phoneFormatter = remember { PhoneNumberFormatter(context) }

    val backgroundColor = when (thread.urgencyLevel) {
        UrgencyLevel.CRITICAL -> UrgencyCriticalBg
        UrgencyLevel.URGENT -> UrgencyUrgentBg
        UrgencyLevel.NORMAL -> Surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = phoneFormatter.formatForDisplay(thread.phoneNumber),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${thread.phoneNumber}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Phone, "Call")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = thread.lastMessage.take(100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeFormatter.formatRelative(thread.lastMessageAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thread.urgencyLevel != UrgencyLevel.NORMAL) {
                        Badge {
                            Text(
                                text = thread.urgencyLevel.name,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
