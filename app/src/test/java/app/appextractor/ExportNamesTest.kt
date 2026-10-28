package app.appextractor

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNamesTest {
    @Test fun unusedNameIsPreserved() {
        assertEquals("应用 1.0.apk", uniqueExportName("应用 1.0.apk", setOf("other.apk")))
    }
    @Test fun collisionPreservesExtensionAndExistingFiles() {
        val existing = setOf("应用.apk", "应用 (2).apk", "应用 (3).apk")
        assertEquals("应用 (4).apk", uniqueExportName("应用.apk", existing))
        assertEquals(3, existing.size)
    }
    @Test fun collisionComparisonIsCaseInsensitive() {
        assertEquals("App (3).APKS", uniqueExportName("App.APKS", setOf("app.apks", "APP (2).apks")))
    }
    @Test fun batchArchiveAndExtensionlessNamesArePreserved() {
        assertEquals("APPExtractor_2_apps (2).zip", uniqueExportName("APPExtractor_2_apps.zip", setOf("APPExtractor_2_apps.zip")))
        assertEquals("export (2)", uniqueExportName("export", setOf("export")))
    }
}
