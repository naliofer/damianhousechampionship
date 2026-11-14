package com.campeonato.duplas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campeonato.duplas.model.Match
import com.campeonato.duplas.model.Standing
import com.campeonato.duplas.model.Team
import com.campeonato.duplas.ui.theme.AppChampion
import com.campeonato.duplas.ui.theme.AppDivider
import com.campeonato.duplas.ui.theme.AppLoss
import com.campeonato.duplas.ui.theme.AppMutedText
import com.campeonato.duplas.ui.theme.AppPrimary
import com.campeonato.duplas.ui.theme.AppWin

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppMutedText)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppMutedText)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TeamEditorCard(
    index: Int,
    team: Team,
    label: String,
    onPersonAChange: (String) -> Unit,
    onPersonBChange: (String) -> Unit
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                Text("Dupla ${index + 1}", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
                value = team.personA,
                onValueChange = onPersonAChange,
                label = { Text("Pessoa 1") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = team.personB,
                onValueChange = onPersonBChange,
                label = { Text("Pessoa 2") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Nome da dupla: $label",
                style = MaterialTheme.typography.bodySmall,
                color = AppMutedText
            )
        }
    }
}

@Composable
fun MatchCard(
    match: Match,
    labelA: String,
    labelB: String,
    isConfirmed: Boolean,
    onDecreaseA: () -> Unit,
    onIncreaseA: () -> Unit,
    onDecreaseB: () -> Unit,
    onIncreaseB: () -> Unit,
    onConfirm: () -> Unit,
    derivedLabel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("$labelA × $labelB", style = MaterialTheme.typography.titleMedium)
            Text("Placar: ${match.lossesB} × ${match.lossesA}", style = MaterialTheme.typography.bodyLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreEditor(
                    title = labelA,
                    kills = match.lossesB,
                    onDecrease = onDecreaseA,
                    onIncrease = onIncreaseA,
                    modifier = Modifier.weight(1f)
                )
                ScoreEditor(
                    title = labelB,
                    kills = match.lossesA,
                    onDecrease = onDecreaseB,
                    onIncrease = onIncreaseB,
                    modifier = Modifier.weight(1f)
                )
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Resultado: $derivedLabel") },
                leadingIcon = { Icon(Icons.Rounded.SportsScore, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.surface)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onConfirm) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isConfirmed) "Atualizar" else "Confirmar")
                }

                AnimatedVisibility(visible = isConfirmed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AppWin)
                        Spacer(Modifier.width(4.dp))
                        Text("Confirmado", color = AppWin, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Text(
                "Derrotas individuais: $labelA = ${match.lossesA} | $labelB = ${match.lossesB}",
                style = MaterialTheme.typography.bodySmall,
                color = AppMutedText
            )
        }
    }
}

@Composable
private fun ScoreEditor(
    title: String,
    kills: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDecrease) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(kills.toString(), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onIncrease) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

@Composable
fun StandingCard(position: Int, standing: Standing, label: String) {
    val isChampion = position == 0
    val alpha by animateFloatAsState(targetValue = if (isChampion) 1f else 0.95f, label = "rankAlpha")
    val accent = when (position) {
        0 -> AppChampion
        1 -> AppWin
        2 -> AppPrimary
        else -> AppDivider
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (isChampion) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = AppChampion)
                } else {
                    Text("${position + 1}", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${position + 1}º • $label", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${standing.points} pontos • ${standing.wins} vitórias • ${standing.draws} empates • ${standing.losses} derrotas",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppMutedText
                )
                Text("Pessoais: ${standing.individualWins} eliminações realizadas • ${standing.individualLosses} sofridas", style = MaterialTheme.typography.bodySmall)
                Text("Buchholz: ${standing.buchholz}", style = MaterialTheme.typography.bodySmall, color = AppMutedText)
            }
        }
    }
}

@Composable
fun ResultBadge(result: Int, modifier: Modifier = Modifier) {
    val (text, color) = when (result) {
        0, 1 -> "Vitória" to AppWin
        2 -> "Empate" to AppChampion
        else -> "Pendente" to AppLoss
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = color.copy(alpha = 0.15f), disabledLabelColor = color)
    )
}

@Composable
fun EmptyStateCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AppMutedText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoundHeader(round: Int) {
    Text(
        "Rodada $round",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppDivider.copy(alpha = 0.2f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
