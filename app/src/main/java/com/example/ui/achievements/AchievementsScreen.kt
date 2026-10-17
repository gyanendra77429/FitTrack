package com.example.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyActivity
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAchievement
import com.example.ui.theme.LimeTertiary
import com.example.ui.theme.OrangeBurn
import com.example.ui.theme.TealSecondary
import com.example.ui.viewmodel.FitTrackViewModel
import java.text.NumberFormat
import java.util.Locale

data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetDescription: String,
    val isSatisfied: (activities: List<DailyActivity>, workoutsCount: Int) -> Boolean,
    val getProgressFraction: (activities: List<DailyActivity>, workoutsCount: Int) -> Float,
    val getProgressLabel: (activities: List<DailyActivity>, workoutsCount: Int) -> String
)

@Composable
fun AchievementsScreen(
    viewModel: FitTrackViewModel,
    modifier: Modifier = Modifier
) {
    val allActivities by viewModel.allActivities.collectAsState()
    val workouts by viewModel.workouts.collectAsState()
    val achievements by viewModel.achievements.collectAsState()

    // 5 Official Badges
    val badgeDefinitions = remember {
        listOf(
            BadgeDefinition(
                id = "first_activity",
                title = "First Step Taken",
                description = "Log your first activity or step record.",
                icon = Icons.Default.DirectionsWalk,
                targetDescription = "1 activity recorded",
                isSatisfied = { acts, wks -> acts.any { it.steps > 0 } || wks > 0 },
                getProgressFraction = { acts, wks -> if (acts.any { it.steps > 0 } || wks > 0) 1f else 0f },
                getProgressLabel = { acts, wks -> if (acts.any { it.steps > 0 } || wks > 0) "Completed" else "0 / 1 activity" }
            ),
            BadgeDefinition(
                id = "steps_5k",
                title = "5,000 Steps Benchmark",
                description = "Walk at least 5,000 steps in a single day.",
                icon = Icons.Default.Star,
                targetDescription = "5,000 steps in 1 day",
                isSatisfied = { acts, _ -> acts.any { it.steps >= 5000 } },
                getProgressFraction = { acts, _ ->
                    val maxSteps = acts.maxOfOrNull { it.steps } ?: 0L
                    (maxSteps.toFloat() / 5000f).coerceIn(0f, 1f)
                },
                getProgressLabel = { acts, _ ->
                    val maxSteps = acts.maxOfOrNull { it.steps } ?: 0L
                    if (maxSteps >= 5000) "Completed" else "${NumberFormat.getNumberInstance().format(maxSteps)} / 5,000 steps"
                }
            ),
            BadgeDefinition(
                id = "steps_10k",
                title = "10,000 Steps Champion",
                description = "Achieve the gold-standard 10,000 daily steps.",
                icon = Icons.Default.EmojiEvents,
                targetDescription = "10,000 steps in 1 day",
                isSatisfied = { acts, _ -> acts.any { it.steps >= 10000 } },
                getProgressFraction = { acts, _ ->
                    val maxSteps = acts.maxOfOrNull { it.steps } ?: 0L
                    (maxSteps.toFloat() / 10000f).coerceIn(0f, 1f)
                },
                getProgressLabel = { acts, _ ->
                    val maxSteps = acts.maxOfOrNull { it.steps } ?: 0L
                    if (maxSteps >= 10000) "Completed" else "${NumberFormat.getNumberInstance().format(maxSteps)} / 10,000 steps"
                }
            ),
            BadgeDefinition(
                id = "streak_7d",
                title = "7-Day Goal Streak",
                description = "Reach your daily step goal 7 days in a row.",
                icon = Icons.Default.Whatshot,
                targetDescription = "7 consecutive goal days",
                isSatisfied = { acts, _ -> calculateStreak(acts) >= 7 },
                getProgressFraction = { acts, _ ->
                    (calculateStreak(acts).toFloat() / 7f).coerceIn(0f, 1f)
                },
                getProgressLabel = { acts, _ ->
                    val streak = calculateStreak(acts)
                    if (streak >= 7) "Completed" else "$streak / 7 days streak"
                }
            ),
            BadgeDefinition(
                id = "month_100k",
                title = "Monthly 100,000 Club",
                description = "Accumulate 100,000 total steps across your history.",
                icon = Icons.Default.CalendarMonth,
                targetDescription = "100,000 cumulative steps",
                isSatisfied = { acts, _ -> acts.sumOf { it.steps } >= 100000 },
                getProgressFraction = { acts, _ ->
                    (acts.sumOf { it.steps }.toFloat() / 100000f).coerceIn(0f, 1f)
                },
                getProgressLabel = { acts, _ ->
                    val total = acts.sumOf { it.steps }
                    if (total >= 100000) "Completed" else "${NumberFormat.getNumberInstance().format(total)} / 100k"
                }
            )
        )
    }

    // Auto-unlock badges if activity records satisfy them
    LaunchedEffect(allActivities, workouts) {
        badgeDefinitions.forEach { def ->
            if (def.isSatisfied(allActivities, workouts.size)) {
                val alreadyUnlocked = achievements.find { it.badgeId == def.id }?.isUnlocked == true
                if (!alreadyUnlocked) {
                    viewModel.stepTrackerManager.simulateSteps(0) // trigger sync
                }
            }
        }
    }

    val unlockedCount = badgeDefinitions.count { def ->
        def.isSatisfied(allActivities, workouts.size) || achievements.find { it.badgeId == def.id }?.isUnlocked == true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("achievements_screen"),
        contentPadding = PaddingValues(bottom = 90.dp, top = 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Achievements & Badges",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Badges unlock automatically when your verified activity meets each milestone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Milestones Unlocked",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$unlockedCount of ${badgeDefinitions.size} badges earned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(GoldAchievement.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldAchievement,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        // List of Badge Cards
        items(badgeDefinitions, key = { it.id }) { def ->
            val isUnlocked = def.isSatisfied(allActivities, workouts.size) ||
                    achievements.find { it.badgeId == def.id }?.isUnlocked == true
            val fraction = def.getProgressFraction(allActivities, workouts.size)
            val progressLabel = def.getProgressLabel(allActivities, workouts.size)

            BadgeItemCard(
                definition = def,
                isUnlocked = isUnlocked,
                progressFraction = fraction,
                progressLabel = progressLabel
            )
        }
    }
}

@Composable
private fun BadgeItemCard(
    definition: BadgeDefinition,
    isUnlocked: Boolean,
    progressFraction: Float,
    progressLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUnlocked) GoldAchievement.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = definition.icon,
                            contentDescription = null,
                            tint = if (isUnlocked) GoldAchievement else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = definition.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = definition.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isUnlocked) EmeraldPrimary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.Check else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isUnlocked) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isUnlocked) "Unlocked" else "Locked",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isUnlocked) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { if (isUnlocked) 1f else progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isUnlocked) EmeraldPrimary else TealSecondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Requirement: ${definition.targetDescription}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = progressLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) EmeraldPrimary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun calculateStreak(activities: List<DailyActivity>): Int {
    if (activities.isEmpty()) return 0
    val sorted = activities.sortedBy { it.date }
    var maxStreak = 0
    var currentStreak = 0

    sorted.forEach { act ->
        if (act.goalReached) {
            currentStreak++
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
        } else {
            currentStreak = 0
        }
    }
    return maxStreak
}
