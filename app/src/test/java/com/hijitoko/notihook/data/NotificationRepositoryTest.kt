package com.hijitoko.notihook.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {

    @Test
    fun `should include regular user-installed app`() {
        assertTrue(
            NotificationRepository.shouldIncludeApp(
                packageName = "com.example.app",
                appFlags = 0,
                currentPackageName = "com.hijitoko.notihook"
            )
        )
    }

    @Test
    fun `should exclude current app`() {
        assertFalse(
            NotificationRepository.shouldIncludeApp(
                packageName = "com.hijitoko.notihook",
                appFlags = 0,
                currentPackageName = "com.hijitoko.notihook"
            )
        )
    }

    @Test
    fun `should exclude pure system app`() {
        assertFalse(
            NotificationRepository.shouldIncludeApp(
                packageName = "com.android.system",
                appFlags = ApplicationInfo.FLAG_SYSTEM,
                currentPackageName = "com.hijitoko.notihook"
            )
        )
    }

    @Test
    fun `should include updated system app`() {
        assertTrue(
            NotificationRepository.shouldIncludeApp(
                packageName = "com.android.updated",
                appFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                currentPackageName = "com.hijitoko.notihook"
            )
        )
    }
}
