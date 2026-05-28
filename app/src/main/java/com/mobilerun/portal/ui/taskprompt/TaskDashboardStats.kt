package com.mobilerun.portal.ui.taskprompt

import com.mobilerun.portal.taskprompt.PortalCloudClient
import com.mobilerun.portal.taskprompt.PortalTaskHistoryItem
import com.mobilerun.portal.taskprompt.PortalTaskTimestampSupport
import com.mobilerun.portal.taskprompt.PortalTaskTracking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayActivity(
    val date: LocalDate,
    val label: String,
    val count: Int,
)

data class StatusCount(
    val status: String,
    val label: String,
    val count: Int,
    val color: Int,
)

data class DashboardStats(
    val totalRuns: Int,
    val completedCount: Int,
    val failedCount: Int,
    val successRate: Double?,
    val statusCounts: List<StatusCount>,
    val activityByDay: List<DayActivity>,
    val avgDurationMs: Long?,
    val avgSteps: Int?,
    val topModel: String?,
    val lastTaskAgoMs: Long?,
) {
    companion object {
        private const val SPARKLINE_DAYS = 14
        private val DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        private val STATUS_COLORS = mapOf(
            PortalTaskTracking.STATUS_COMPLETED to 0xFF0D9373.toInt(),
            PortalTaskTracking.STATUS_FAILED to 0xFFC0392B.toInt(),
            PortalTaskTracking.STATUS_CANCELLED to 0xFF888888.toInt(),
            PortalTaskTracking.STATUS_CANCELLING to 0xFFCCA335.toInt(),
            PortalTaskTracking.STATUS_RUNNING to 0xFFCCA335.toInt(),
            PortalTaskTracking.STATUS_CREATED to 0xFF4A90D9.toInt(),
            PortalTaskTracking.STATUS_PAUSED to 0xFFB8A060.toInt(),
            PortalTaskTracking.STATUS_TRACKING_TIMEOUT to 0xFF888888.toInt(),
        )

        private val STATUS_LABELS = mapOf(
            PortalTaskTracking.STATUS_COMPLETED to "Completed",
            PortalTaskTracking.STATUS_FAILED to "Failed",
            PortalTaskTracking.STATUS_CANCELLED to "Cancelled",
            PortalTaskTracking.STATUS_CANCELLING to "Cancelling",
            PortalTaskTracking.STATUS_RUNNING to "Running",
            PortalTaskTracking.STATUS_CREATED to "Created",
            PortalTaskTracking.STATUS_PAUSED to "Paused",
            PortalTaskTracking.STATUS_TRACKING_TIMEOUT to "Tracking stopped",
        )

        fun compute(
            items: List<PortalTaskHistoryItem>,
            total: Int,
            nowMs: Long = System.currentTimeMillis(),
        ): DashboardStats {
            val completed = items.count { it.status == PortalTaskTracking.STATUS_COMPLETED }
            val failed = items.count { it.status == PortalTaskTracking.STATUS_FAILED }
            val finished = completed + failed
            val successRate = if (finished > 0) completed.toDouble() / finished * 100.0 else null

            val countsByStatus = items.groupingBy { it.status }.eachCount()
            val statusCounts = countsByStatus
                .map { (status, count) ->
                    StatusCount(
                        status = status,
                        label = STATUS_LABELS[status] ?: status.replaceFirstChar { it.uppercase() },
                        count = count,
                        color = STATUS_COLORS[status] ?: 0xFF555555.toInt(),
                    )
                }
                .sortedByDescending { it.count }

            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val buckets = LinkedHashMap<LocalDate, Int>()
            for (i in SPARKLINE_DAYS - 1 downTo 0) {
                buckets[today.minusDays(i.toLong())] = 0
            }
            for (item in items) {
                val epochMs = PortalTaskTimestampSupport.parseEpochMillis(item.createdAt) ?: continue
                val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
                if (buckets.containsKey(date)) {
                    buckets[date] = buckets.getValue(date) + 1
                }
            }
            val activityByDay = buckets.map { (date, count) ->
                DayActivity(date, date.format(DATE_LABEL_FORMAT), count)
            }

            val durations = items.mapNotNull { item ->
                val start = PortalTaskTimestampSupport.parseEpochMillis(item.createdAt) ?: return@mapNotNull null
                val end = PortalTaskTimestampSupport.parseEpochMillis(item.finishedAt) ?: return@mapNotNull null
                val ms = end - start
                if (ms > 0) ms else null
            }
            val avgDurationMs = if (durations.isNotEmpty()) durations.sum() / durations.size else null

            val stepValues = items.mapNotNull { it.steps }
            val avgSteps = if (stepValues.isNotEmpty()) {
                (stepValues.sum().toDouble() / stepValues.size).toInt()
            } else null

            val topModel = items
                .mapNotNull { it.llmModel?.trim()?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            val lastTaskEpochMs = items.mapNotNull {
                PortalTaskTimestampSupport.parseEpochMillis(it.createdAt)
            }.maxOrNull()
            val lastTaskAgoMs = if (lastTaskEpochMs != null) {
                (nowMs - lastTaskEpochMs).coerceAtLeast(0L)
            } else null

            return DashboardStats(
                totalRuns = total,
                completedCount = completed,
                failedCount = failed,
                successRate = successRate,
                statusCounts = statusCounts,
                activityByDay = activityByDay,
                avgDurationMs = avgDurationMs,
                avgSteps = avgSteps,
                topModel = topModel,
                lastTaskAgoMs = lastTaskAgoMs,
            )
        }

        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }

        fun formatModelLabel(modelId: String): String {
            return PortalCloudClient.formatModelLabel(modelId)
        }

        fun formatTimeAgo(ms: Long): String {
            val seconds = ms / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "${days}d ago"
                hours > 0 -> "${hours}h ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "just now"
            }
        }
    }
}
