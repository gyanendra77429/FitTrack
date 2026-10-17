package com.example.ui.reports

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.dashboard.WeeklyStepBarChart
import com.example.ui.theme.BlueDistance
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAchievement
import com.example.ui.theme.LimeTertiary
import com.example.ui.theme.OrangeBurn
import com.example.ui.theme.PurpleTime
import com.example.ui.theme.TealSecondary
import com.example.ui.viewmodel.FitTrackViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: FitTrackViewModel,
    modifier: Modifier = Modifier
) {
    val allActivities by viewModel.allActivities.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    // Calculate Longest Streak
    val longestStreak = remember(allActivities) {
        calculateLongestStreak(allActivities)
    }

    // Calculate Average Daily Steps
    val avgDailySteps = remember(allActivities) {
        if (allActivities.isNotEmpty()) {
            allActivities.map { it.steps }.average().toLong()
        } else 0L
    }

    // Calculate Goal Completion Rate
    val goalCompletionRate = remember(allActivities) {
        if (allActivities.isNotEmpty()) {
            val metCount = allActivities.count { it.goalReached }
            ((metCount.toDouble() / allActivities.size) * 100).toInt()
        } else 0
    }

    // Weekly Comparison (Current 7 days vs Previous 7 days)
    val (thisWeekTotal, prevWeekTotal) = remember(allActivities) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        var currentSum = 0L
        var prevSum = 0L

        allActivities.forEach { act ->
            try {
                val d = dateFormat.parse(act.date)
                if (d != null) {
                    val diffDays = ((System.currentTimeMillis() - d.time) / (1000 * 60 * 60 * 24)).toInt()
                    if (diffDays in 0..6) {
                        currentSum += act.steps
                    } else if (diffDays in 7..13) {
                        prevSum += act.steps
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        Pair(currentSum, prevSum)
    }

    val isKm = userProfile.preferredUnit == "km"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(bottom = 90.dp, top = 16.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Reports & Analytics",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "In-depth step trends, streak records, and comparisons",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3 Key KPI Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Avg Steps",
                    value = NumberFormat.getNumberInstance(Locale.getDefault()).format(avgDailySteps),
                    subtitle = "per day",
                    icon = Icons.Default.ShowChart,
                    color = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Goal Rate",
                    value = "$goalCompletionRate%",
                    subtitle = "targets hit",
                    icon = Icons.Default.CheckCircle,
                    color = LimeTertiary,
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Longest Streak",
                    value = "$longestStreak Days",
                    subtitle = "consecutive",
                    icon = Icons.Default.Whatshot,
                    color = OrangeBurn,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Weekly Comparison Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TealSecondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = TealSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Weekly Comparison",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val diff = thisWeekTotal - prevWeekTotal
                        val pctChange = if (prevWeekTotal > 0) ((diff.toDouble() / prevWeekTotal) * 100).toInt() else 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (diff >= 0) EmeraldPrimary.copy(alpha = 0.15f) else OrangeBurn.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (diff >= 0) "+$pctChange% vs last week" else "$pctChange% vs last week",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (diff >= 0) EmeraldPrimary else OrangeBurn
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = NumberFormat.getNumberInstance(Locale.getDefault()).format(thisWeekTotal),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("This Week (Past 7 Days)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = NumberFormat.getNumberInstance(Locale.getDefault()).format(prevWeekTotal),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Previous 7 Days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Daily Step Chart
        item {
            WeeklyStepBarChart(
                weeklyActivities = allActivities,
                dailyGoal = userProfile.dailyStepGoal
            )
        }

        // Monthly Milestone Progress Card
        item {
            val totalMonthSteps = remember(allActivities) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                allActivities.filter {
                    try {
                        val d = dateFormat.parse(it.date)
                        if (d != null) {
                            val diffDays = ((System.currentTimeMillis() - d.time) / (1000 * 60 * 60 * 24)).toInt()
                            diffDays in 0..29
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }.sumOf { it.steps }
            }
            val targetMonth = 100000L
            val monthProgress = (totalMonthSteps.toFloat() / targetMonth).coerceIn(0f, 1f)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GoldAchievement.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GoldAchievement, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Monthly 100k Benchmark",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${(monthProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { monthProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = EmeraldPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${NumberFormat.getNumberInstance(Locale.getDefault()).format(totalMonthSteps)} / 100,000 steps logged in last 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun calculateLongestStreak(activities: List<DailyActivity>): Int {
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
