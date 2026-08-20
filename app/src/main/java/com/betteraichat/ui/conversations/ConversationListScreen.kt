package com.betteraichat.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betteraichat.core.db.ConversationEntity
import com.betteraichat.core.mode.AppMode
import com.betteraichat.core.model.ProviderId
import com.betteraichat.ui.rememberContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CONV_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStarred: () -> Unit
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val activeConversations by container.repository.observeActiveConversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedConversations by container.repository.observeArchivedConversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val query = searchQuery.trim()
    var contentMatchIds by remember { mutableStateOf(emptyList<Long>()) }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            contentMatchIds = container.repository.searchByContent(query)
        } else {
            contentMatchIds = emptyList()
        }
    }
    val filteredActive = if (query.isEmpty()) activeConversations
    else {
        val byTitle = activeConversations.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.model.contains(query, ignoreCase = true)
        }
        val byContent = activeConversations.filter { it.id in contentMatchIds }
        (byTitle + byContent).distinctBy { it.id }
    }
    val filteredArchived = if (query.isEmpty()) archivedConversations
    else archivedConversations.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.model.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BetterAIChat") },
                actions = {
                    IconButton(onClick = onOpenStarred) {
                        Icon(Icons.Filled.Star, contentDescription = "收藏")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenChat(-1L) }) {
                Icon(Icons.Filled.Add, contentDescription = "新对话")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索会话…") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                }
            )
            if (filteredActive.isEmpty() && filteredArchived.isEmpty()) {
                EmptyHint(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredActive, key = { "a${it.id}" }) { c ->
                        SwipeableConversationCard(
                            conversation = c,
                            onClick = { onOpenChat(c.id) },
                            onRename = {
                                renameTarget = c
                                renameText = c.title
                            },
                            onTogglePin = {
                                scope.launch { container.repository.setPinned(c.id, !c.pinned) }
                            },
                            onToggleArchive = {
                                scope.launch { container.repository.setArchived(c.id, !c.archived) }
                            },
                            onClearMessages = {
                                scope.launch { container.repository.clearMessages(c.id) }
                            },
                            onDelete = { scope.launch { container.repository.deleteConversation(c.id) } }
                        )
                    }
                    if (archivedConversations.isNotEmpty() && query.isEmpty()) {
                        item {
                            TextButton(onClick = { showArchived = !showArchived }) {
                                Text(
                                    if (showArchived) "收起已归档（${archivedConversations.size}）"
                                    else "已归档（${archivedConversations.size}）"
                                )
                            }
                        }
                        if (showArchived) {
                            items(filteredArchived, key = { "r${it.id}" }) { c ->
                                SwipeableConversationCard(
                                    conversation = c,
                                    onClick = { onOpenChat(c.id) },
                                    onRename = {
                                        renameTarget = c
                                        renameText = c.title
                                    },
                                    onTogglePin = {
                                        scope.launch { container.repository.setPinned(c.id, !c.pinned) }
                                    },
                                    onToggleArchive = {
                                        scope.launch { container.repository.setArchived(c.id, !c.archived) }
                                    },
                                    onClearMessages = {
                                        scope.launch { container.repository.clearMessages(c.id) }
                                    },
                                    onDelete = { scope.launch { container.repository.deleteConversation(c.id) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty()) {
                        scope.launch { container.repository.updateTitle(c.id, name) }
                    }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("还没有对话", style = MaterialTheme.typography.titleMedium)
        Text(
            "点击右下角 + 开始第一次对话",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableConversationCard(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onClearMessages: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )
    Box {
        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = { }
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = { menuOpen = true }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(conversation.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            buildString {
                                val provider = runCatching { ProviderId.valueOf(conversation.provider) }
                                    .getOrDefault(ProviderId.OPENAI_COMPAT)
                                append(provider.displayName)
                                append(" · ")
                                append(conversation.model)
                                runCatching { AppMode.valueOf(conversation.mode) }.getOrNull()?.let {
                                    append(" · ")
                                    append(it.displayName)
                                }
                                append(" · ")
                                append(
                                    CONV_TIME_FORMAT.format(Date(conversation.updatedAt))
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (conversation.pinned) "取消置顶" else "置顶") },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (conversation.archived) "取消归档" else "归档") },
                onClick = {
                    menuOpen = false
                    onToggleArchive()
                }
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuOpen = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("清除聊天记录") },
                onClick = {
                    menuOpen = false
                    onClearMessages()
                }
            )
            DropdownMenuItem(
                text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}
