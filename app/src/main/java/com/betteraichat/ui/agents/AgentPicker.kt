package com.betteraichat.ui.agents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betteraichat.R
import com.betteraichat.core.db.AgentEntity
import com.betteraichat.ui.rememberContainer

@Composable
fun AgentPickerDialog(
    currentAgentId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val container = rememberContainer()
    val agents by container.agentRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showOnboarding by remember { mutableStateOf(false) }

    if (showOnboarding) {
        AgentOnboardingDialog(
            onDismiss = { showOnboarding = false },
            onSaved = { showOnboarding = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_select_agent)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp)
            ) {
                if (agents.isEmpty()) {
                    Text(
                        stringResource(R.string.agents_empty),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                agents.forEach { agent ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (agent.id == currentAgentId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = agent.id == currentAgentId,
                                onClick = { onSelect(agent.id) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        agent.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (agent.isDefault) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.agents_default),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    "${agent.provider} · ${agent.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showOnboarding = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.agents_new))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
