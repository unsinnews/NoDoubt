package com.nodoubt.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodoubt.platform.capture.CaptureService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturePermissionContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun authorizationLaunchUsesATemporaryTaskAndNeverRequestsAutomaticCapture() {
        val intent = CapturePermissionActivity.createIntent(context, 42L)
        assertEquals(ComponentName(context, CapturePermissionActivity::class.java), intent.component)
        assertEquals(42L, intent.getLongExtra(CaptureService.REQUEST_ID, 0))
        assertTrue(intent.hasExtra(CaptureService.AUTO_CAPTURE))
        assertFalse(intent.getBooleanExtra(CaptureService.AUTO_CAPTURE, true))
        listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).forEach { flag ->
            assertTrue("Authorization must have task flag $flag", intent.flags and flag != 0)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun permissionHostHasIndependentAffinityAndSurvivesTheSystemConsentDialog() {
        val manager = context.packageManager
        val permission = manager.getActivityInfo(ComponentName(context, CapturePermissionActivity::class.java), 0)
        val home = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("${context.packageName}.capture", permission.taskAffinity)
        assertNotEquals(home.taskAffinity, permission.taskAffinity)
        assertFalse(permission.exported)
        assertTrue(permission.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        // noHistory would remove the host while the system dialog is above it, losing its result.
        assertEquals(0, permission.flags and ActivityInfo.FLAG_NO_HISTORY)
    }
}
