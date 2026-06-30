package ir.carepack.release

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.carepack.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ReleaseManifestContractTest {

    private val context:
            Context =
        ApplicationProvider
            .getApplicationContext()

    @Test
    fun manifestDoesNotRequestUnapprovedProductPermissions() {
        val requestedPermissions =
            packageInfoWithPermissions()
                .requestedPermissions
                .orEmpty()
                .toSet()

        APPROVED_PRODUCT_PERMISSIONS.forEach { permission ->
            assertTrue(
                "Missing required permission: $permission",
                requestedPermissions.contains(permission),
            )
        }

        assertFalse(
            requestedPermissions.contains(
                Manifest.permission.INTERNET,
            ),
        )

        val unapprovedPermissions =
            requestedPermissions
                .filterNot { permission ->
                    permission in APPROVED_PRODUCT_PERMISSIONS ||
                            permission == debugDynamicReceiverPermission()
                }

        assertTrue(
            "Unapproved manifest permissions: $unapprovedPermissions",
            unapprovedPermissions.isEmpty(),
        )
    }

    @Test
    fun backupIsDisabledAndExplicitlyExcluded() {
        val applicationInfo =
            applicationInfo()

        assertFalse(
            applicationInfo.flags and
                    ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
        )

        assertEquals(
            REQUIRED_BACKUP_DOMAINS,
            excludeDomainsFrom(
                R.xml.backup_rules,
            ),
        )

        assertTrue(
            excludeDomainsFrom(
                R.xml.data_extraction_rules,
            ).containsAll(
                REQUIRED_BACKUP_DOMAINS,
            ),
        )
    }

    private fun packageInfoWithPermissions():
            PackageInfo {
        val packageManager =
            context.packageManager

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
        }
    }

    private fun applicationInfo():
            ApplicationInfo {
        val packageManager =
            context.packageManager

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                context.packageName,
                0,
            )
        }
    }

    private fun excludeDomainsFrom(
        @XmlRes resourceId: Int,
    ): Set<String> {
        val parser =
            context.resources.getXml(
                resourceId,
            )

        val domains =
            mutableSetOf<String>()

        try {
            while (
                parser.next() !=
                XmlPullParser.END_DOCUMENT
            ) {
                if (
                    parser.eventType ==
                    XmlPullParser.START_TAG &&
                    parser.name == "exclude"
                ) {
                    val domain =
                        parser.getAttributeValue(
                            null,
                            "domain",
                        )

                    if (!domain.isNullOrBlank()) {
                        domains += domain
                    }
                }
            }
        } finally {
            parser.close()
        }

        return domains
    }

    private fun debugDynamicReceiverPermission():
            String {
        return "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    }

    private companion object {

        val APPROVED_PRODUCT_PERMISSIONS =
            setOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.SCHEDULE_EXACT_ALARM,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
            )

        val REQUIRED_BACKUP_DOMAINS =
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
            )
    }
}
