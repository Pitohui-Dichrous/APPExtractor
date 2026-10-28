package app.appextractor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Official Lucide SVG geometry, vendored as Android vectors; see docs/ICONS.md. */
@Composable
internal fun Glyph(
    kind: String,
    description: String?,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val resource = when (kind) {
        "refresh" -> R.drawable.ic_lucide_refresh
        "settings" -> R.drawable.ic_lucide_settings
        "search" -> R.drawable.ic_lucide_search
        "close" -> R.drawable.ic_lucide_close
        "check" -> R.drawable.ic_lucide_check
        "sort" -> R.drawable.ic_lucide_sort
        "download" -> R.drawable.ic_lucide_download
        "folder" -> R.drawable.ic_lucide_folder
        else -> R.drawable.ic_lucide_package
    }
    Icon(
        painter = painterResource(resource),
        contentDescription = description,
        modifier = modifier.size(24.dp),
        tint = color,
    )
}
