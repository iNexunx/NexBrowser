package com.nexun.nexbrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small, hand-picked Material Symbol paths so the app never needs the huge
 * material-icons-extended artifact. Each is a single Vector path (24dp grid).
 */
object AppIcons {
    private fun vec(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    val Block: ImageVector = vec("Block") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.48f, 22f, 12f, 22f)
            reflectiveCurveTo(22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(4f, 12f)
            curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
            curveTo(13.85f, 4f, 15.54f, 4.63f, 16.9f, 5.69f)
            lineTo(5.69f, 16.9f)
            curveTo(4.63f, 15.54f, 4f, 13.85f, 4f, 12f)
            close()
            moveTo(12f, 20f)
            curveTo(10.15f, 20f, 8.46f, 19.37f, 7.1f, 18.31f)
            lineTo(18.31f, 7.1f)
            curveTo(19.37f, 8.46f, 20f, 10.15f, 20f, 12f)
            curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
            close()
        }
    }

    val Download: ImageVector = vec("Download") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            close()
            moveTo(19f, 9f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            lineTo(12f, 16f)
            lineTo(19f, 9f)
            close()
        }
    }

    val VisibilityOff: ImageVector = vec("VisibilityOff") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 12.65f, 16.87f, 13.26f, 16.64f, 13.83f)
            lineTo(19.56f, 16.75f)
            curveTo(21.07f, 15.49f, 22.26f, 13.86f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            curveTo(10.6f, 4.5f, 9.26f, 4.75f, 8.02f, 5.2f)
            lineTo(10.17f, 7.35f)
            curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
            close()
            moveTo(2f, 4.27f)
            lineTo(4.28f, 6.55f)
            lineTo(4.73f, 7f)
            curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f)
            lineTo(16.8f, 19.08f)
            lineTo(19.73f, 22f)
            lineTo(21f, 20.73f)
            lineTo(3.27f, 3f)
            lineTo(2f, 4.27f)
            close()
            moveTo(7.53f, 9.8f)
            lineTo(9.08f, 11.35f)
            curveTo(9.03f, 11.56f, 9f, 11.78f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(12.22f, 15f, 12.44f, 14.97f, 12.65f, 14.92f)
            lineTo(14.2f, 16.47f)
            curveTo(13.53f, 16.8f, 12.79f, 17f, 12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 11.21f, 7.2f, 10.47f, 7.53f, 9.8f)
            close()
        }
    }

    val LockOpen: ImageVector = vec("LockOpen") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 17f)
            curveTo(13.1f, 17f, 14f, 16.1f, 14f, 15f)
            reflectiveCurveTo(13.1f, 13f, 12f, 13f)
            reflectiveCurveTo(10f, 13.9f, 10f, 15f)
            reflectiveCurveTo(10.9f, 17f, 12f, 17f)
            close()
            moveTo(18f, 8f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            reflectiveCurveTo(7f, 3.24f, 7f, 6f)
            horizontalLineTo(9f)
            curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f)
            reflectiveCurveTo(15f, 4.34f, 15f, 6f)
            verticalLineTo(8f)
            horizontalLineTo(6f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(18f, 20f)
            horizontalLineTo(6f)
            verticalLineTo(10f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            close()
        }
    }

    val Code: ImageVector = vec("Code") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.4f, 16.6f)
            lineTo(4.8f, 12f)
            lineTo(9.4f, 7.4f)
            lineTo(8f, 6f)
            lineTo(2f, 12f)
            lineTo(8f, 18f)
            lineTo(9.4f, 16.6f)
            close()
            moveTo(14.6f, 16.6f)
            lineTo(19.2f, 12f)
            lineTo(14.6f, 7.4f)
            lineTo(16f, 6f)
            lineTo(22f, 12f)
            lineTo(16f, 18f)
            lineTo(14.6f, 16.6f)
            close()
        }
    }

    val DarkMode: ImageVector = vec("DarkMode") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.37f, 21.5f)
            curveTo(13.66f, 21.5f, 17.15f, 18.01f, 17.15f, 13.72f)
            curveTo(17.15f, 9.66f, 14.02f, 6.34f, 9.96f, 6.06f)
            curveTo(10.98f, 7.44f, 11.6f, 9.09f, 11.6f, 10.9f)
            curveTo(11.6f, 15.19f, 8.11f, 18.68f, 3.82f, 18.68f)
            curveTo(3.26f, 18.68f, 2.72f, 18.62f, 2.19f, 18.5f)
            curveTo(3.56f, 20.39f, 5.74f, 21.5f, 9.37f, 21.5f)
            close()
        }
    }

    val LightMode: ImageVector = vec("LightMode") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            reflectiveCurveTo(14.76f, 17f, 12f, 17f)
            reflectiveCurveTo(7f, 14.76f, 7f, 12f)
            reflectiveCurveTo(9.24f, 7f, 12f, 7f)
            close()
            moveTo(12f, 5f)
            verticalLineTo(1f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            close()
            moveTo(12f, 19f)
            verticalLineTo(23f)
            horizontalLineTo(11f)
            verticalLineTo(19f)
            close()
            moveTo(5f, 11f)
            horizontalLineTo(1f)
            verticalLineTo(12f)
            horizontalLineTo(5f)
            close()
            moveTo(23f, 11f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            horizontalLineTo(23f)
            close()
            moveTo(6.34f, 4.93f)
            lineTo(4.93f, 6.34f)
            lineTo(7.05f, 8.46f)
            lineTo(8.46f, 7.05f)
            close()
            moveTo(16.95f, 15.54f)
            lineTo(15.54f, 16.95f)
            lineTo(17.66f, 19.07f)
            lineTo(19.07f, 17.66f)
            close()
            moveTo(4.93f, 17.66f)
            lineTo(6.34f, 19.07f)
            lineTo(8.46f, 16.95f)
            lineTo(7.05f, 15.54f)
            close()
            moveTo(15.54f, 7.05f)
            lineTo(16.95f, 8.46f)
            lineTo(19.07f, 6.34f)
            lineTo(17.66f, 4.93f)
            close()
        }
    }

    val DesktopWindows: ImageVector = vec("DesktopWindows") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 2f)
            horizontalLineTo(3f)
            curveTo(1.9f, 2f, 1f, 2.9f, 1f, 4f)
            verticalLineTo(16f)
            curveTo(1f, 17.1f, 1.9f, 18f, 3f, 18f)
            horizontalLineTo(8f)
            verticalLineTo(20f)
            horizontalLineTo(16f)
            verticalLineTo(18f)
            horizontalLineTo(21f)
            curveTo(22.1f, 18f, 23f, 17.1f, 23f, 16f)
            verticalLineTo(4f)
            curveTo(23f, 2.9f, 22.1f, 2f, 21f, 2f)
            close()
            moveTo(21f, 16f)
            horizontalLineTo(3f)
            verticalLineTo(4f)
            horizontalLineTo(21f)
            verticalLineTo(16f)
            close()
        }
    }

    val DeleteSweep: ImageVector = vec("DeleteSweep") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15f, 16f)
            horizontalLineTo(5f)
            verticalLineTo(18f)
            horizontalLineTo(15f)
            verticalLineTo(16f)
            close()
            moveTo(19f, 8f)
            horizontalLineTo(5f)
            verticalLineTo(10f)
            horizontalLineTo(19f)
            verticalLineTo(8f)
            close()
            moveTo(15f, 12f)
            horizontalLineTo(5f)
            verticalLineTo(14f)
            horizontalLineTo(15f)
            verticalLineTo(12f)
            close()
            moveTo(21f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(21f)
            verticalLineTo(4f)
            close()
        }
    }

    val FindInPage: ImageVector = vec("FindInPage") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.89f, 22f, 5.99f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(8f)
            lineTo(14f, 2f)
            close()
            moveTo(18f, 20f)
            horizontalLineTo(6f)
            verticalLineTo(4f)
            horizontalLineTo(13f)
            verticalLineTo(9f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            close()
        }
    }

    val RestartAlt: ImageVector = vec("RestartAlt") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 5f)
            verticalLineTo(2f)
            lineTo(8f, 6f)
            lineTo(12f, 10f)
            verticalLineTo(7f)
            curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f)
            reflectiveCurveTo(15.31f, 19f, 12f, 19f)
            reflectiveCurveTo(6f, 16.31f, 6f, 13f)
            horizontalLineTo(4f)
            curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f)
            reflectiveCurveTo(20f, 17.42f, 20f, 13f)
            reflectiveCurveTo(16.42f, 5f, 12f, 5f)
            close()
        }
    }

    val Image: ImageVector = vec("Image") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 13.5f)
            lineTo(11f, 16.51f)
            lineTo(14.5f, 12f)
            lineTo(19f, 18f)
            horizontalLineTo(5f)
            lineTo(8.5f, 13.5f)
            close()
        }
    }

    val MusicNote: ImageVector = vec("MusicNote") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            verticalLineTo(13.55f)
            curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
            curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
            reflectiveCurveTo(7.79f, 21f, 10f, 21f)
            reflectiveCurveTo(14f, 19.21f, 14f, 17f)
            verticalLineTo(7f)
            horizontalLineTo(18f)
            verticalLineTo(3f)
            horizontalLineTo(12f)
            close()
        }
    }

    val Videocam: ImageVector = vec("Videocam") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17f, 10.5f)
            verticalLineTo(7f)
            curveTo(17f, 5.9f, 16.1f, 5f, 15f, 5f)
            horizontalLineTo(4f)
            curveTo(2.9f, 5f, 2f, 5.9f, 2f, 7f)
            verticalLineTo(17f)
            curveTo(2f, 18.1f, 2.9f, 19f, 4f, 19f)
            horizontalLineTo(15f)
            curveTo(16.1f, 19f, 17f, 18.1f, 17f, 17f)
            verticalLineTo(13.5f)
            lineTo(22f, 18.5f)
            verticalLineTo(5.5f)
            lineTo(17f, 10.5f)
            close()
        }
    }

    val Cookie: ImageVector = vec("Cookie") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21.95f, 12.05f)
            curveTo(21.85f, 13.22f, 20.9f, 14.11f, 19.72f, 14.11f)
            curveTo(18.35f, 14.11f, 17.24f, 15.22f, 17.24f, 16.59f)
            curveTo(17.24f, 17.24f, 17.5f, 17.84f, 17.92f, 18.28f)
            curveTo(17.08f, 19.35f, 15.76f, 20f, 14.3f, 20f)
            curveTo(11.48f, 20f, 9.2f, 17.72f, 9.2f, 14.9f)
            curveTo(9.2f, 14.36f, 9.28f, 13.83f, 9.43f, 13.34f)
            curveTo(9.15f, 13.38f, 8.87f, 13.4f, 8.58f, 13.4f)
            curveTo(5.17f, 13.4f, 2.4f, 10.63f, 2.4f, 7.22f)
            curveTo(2.4f, 5.87f, 2.84f, 4.62f, 3.59f, 3.61f)
            curveTo(3.86f, 4.38f, 4.6f, 4.93f, 5.47f, 4.93f)
            curveTo(6.58f, 4.93f, 7.48f, 4.03f, 7.48f, 2.92f)
            curveTo(7.48f, 2.84f, 7.47f, 2.76f, 7.46f, 2.69f)
            curveTo(9.04f, 2.13f, 10.83f, 2.24f, 12.35f, 2.85f)
            curveTo(12.22f, 3.2f, 12.15f, 3.58f, 12.15f, 3.97f)
            curveTo(12.15f, 5.75f, 13.6f, 7.2f, 15.38f, 7.2f)
            curveTo(15.73f, 7.2f, 16.06f, 7.14f, 16.37f, 7.04f)
            curveTo(17.47f, 7.95f, 18.2f, 9.27f, 18.31f, 10.76f)
            curveTo(18.93f, 10.55f, 19.54f, 10.5f, 20.15f, 10.6f)
            curveTo(20.87f, 10.72f, 21.51f, 11.03f, 22f, 11.48f)
            curveTo(21.99f, 11.67f, 21.97f, 11.86f, 21.95f, 12.05f)
            close()
        }
    }

    val Tab: ImageVector = vec("Tab") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 3f)
            horizontalLineTo(3f)
            curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
            verticalLineTo(19f)
            curveTo(1f, 20.1f, 1.9f, 21f, 3f, 21f)
            horizontalLineTo(21f)
            curveTo(22.1f, 21f, 23f, 20.1f, 23f, 19f)
            verticalLineTo(5f)
            curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
            close()
            moveTo(21f, 19f)
            horizontalLineTo(3f)
            verticalLineTo(8f)
            horizontalLineTo(10f)
            lineTo(12f, 10f)
            horizontalLineTo(21f)
            verticalLineTo(19f)
            close()
        }
    }

    /** Compass — modern browser brand mark. */
    val Compas: ImageVector = vec("Compass") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.48f, 22f, 12f, 22f)
            reflectiveCurveTo(22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
            reflectiveCurveTo(7.59f, 4f, 12f, 4f)
            reflectiveCurveTo(20f, 7.59f, 20f, 12f)
            reflectiveCurveTo(16.41f, 20f, 12f, 20f)
            close()
            moveTo(16.24f, 7.76f)
            lineTo(9.19f, 9.19f)
            lineTo(7.76f, 16.24f)
            lineTo(14.81f, 14.81f)
            close()
            moveTo(12.5f, 12.5f)
            lineTo(10.5f, 13.5f)
            lineTo(11.5f, 11.5f)
            lineTo(13.5f, 10.5f)
            close()
        }
    }

    /** Refresh / sync arrows. */
    val RefreshIcon: ImageVector = vec("Refresh") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            reflectiveCurveTo(7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            horizontalLineTo(17.65f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            reflectiveCurveTo(8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            close()
        }
    }

    /** Shield — adblock / privacy. */
    val Shield: ImageVector = vec("Shield") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineTo(11f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
            verticalLineTo(5f)
            lineTo(12f, 1f)
            close()
            moveTo(12f, 11.99f)
            horizontalLineTo(19f)
            curveTo(18.47f, 16.11f, 15.68f, 19.84f, 12f, 20.93f)
            verticalLineTo(12f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            lineTo(12f, 7.12f)
            verticalLineTo(11.99f)
            close()
        }
    }

    /** Hamburger menu. */
    val Menu: ImageVector = vec("Menu") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 6f)
            horizontalLineTo(21f)
            verticalLineTo(8f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 11f)
            horizontalLineTo(21f)
            verticalLineTo(13f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 16f)
            horizontalLineTo(21f)
            verticalLineTo(18f)
            horizontalLineTo(3f)
            close()
        }
    }
}
