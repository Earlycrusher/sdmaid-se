package eu.darken.sdmse.stats.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.ReportId

@Entity(tableName = "affected_paths")
data class AffectedPathEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "report_id") override val reportId: ReportId,
    @ColumnInfo(name = "action") override val action: AffectedPath.Action,
    @ColumnInfo(name = "path") override val path: APath,
) : AffectedPath {
    companion object {
        fun from(report: AffectedPath) = AffectedPathEntity(
            reportId = report.reportId,
            action = report.action,
            path = report.path,
        )
    }
}