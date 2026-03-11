package com.hijitoko.notihook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationCatcherDao {

    @Insert
    suspend fun insertNotification(record: NotificationRecordEntity): Long

    @Query(
        """
        SELECT * FROM notification_records
        WHERE packageName IN (SELECT packageName FROM app_configs WHERE enabled = 1)
        ORDER BY postedAt DESC
        """
    )
    fun observeEnabledHistories(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): NotificationRecordEntity?

    @Query(
        """
        UPDATE notification_records
        SET forwardStatus = :status,
            forwardError = :error,
            forwardedAt = :forwardedAt
        WHERE id = :id
        """
    )
    suspend fun updateForwardResult(id: Long, status: String, error: String, forwardedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppConfig(config: AppConfigEntity)

    @Query("SELECT * FROM app_configs")
    fun observeAppConfigs(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppConfig(packageName: String): AppConfigEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM app_configs WHERE packageName = :packageName AND enabled = 1)")
    suspend fun isPackageEnabled(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlobalSettings(settings: GlobalSettingsEntity)

    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    fun observeGlobalSettings(): Flow<GlobalSettingsEntity?>

    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    suspend fun getGlobalSettings(): GlobalSettingsEntity?

    @Query(
        """
        DELETE FROM notification_records
        WHERE id NOT IN (
            SELECT id FROM notification_records
            ORDER BY postedAt DESC
            LIMIT :limit
        )
        """
    )
    suspend fun pruneToLimit(limit: Int)
}
