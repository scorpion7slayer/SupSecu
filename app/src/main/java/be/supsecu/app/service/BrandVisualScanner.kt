package be.supsecu.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.os.Build
import be.supsecu.app.core.AccessibleSignal
import be.supsecu.app.core.NodeRole
import be.supsecu.app.core.PageZone
import java.util.ArrayDeque

/**
 * Reconnaissance locale et conservatrice du logo Lidl.
 *
 * Le détecteur cherche un composant bleu presque carré contenant une grande zone jaune
 * et une petite zone rouge. Aucun bitmap n'est conservé ou envoyé hors de l'appareil.
 */
class BrandVisualScanner {
    @TargetApi(Build.VERSION_CODES.R)
    fun scan(screenshot: AccessibilityService.ScreenshotResult): List<AccessibleSignal> {
        val bitmap = try {
            val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            hardware?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            screenshot.hardwareBuffer.close()
        } ?: return emptyList()

        return try {
            if (containsLidlLogo(bitmap)) {
                listOf(
                    AccessibleSignal(
                        text = "Lidl",
                        role = NodeRole.IMAGE_DESCRIPTION,
                        zone = PageZone.HEADER,
                        visibleToUser = true,
                    ),
                )
            } else {
                emptyList()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun containsLidlLogo(bitmap: Bitmap): Boolean {
        val sampledWidth = bitmap.width / SAMPLE_STEP
        val sampledHeight = ((bitmap.height * MAX_VERTICAL_RATIO).toInt() / SAMPLE_STEP)
        if (sampledWidth < MIN_COMPONENT_SIZE || sampledHeight < MIN_COMPONENT_SIZE) return false

        val blue = BooleanArray(sampledWidth * sampledHeight)
        for (y in 0 until sampledHeight) {
            for (x in 0 until sampledWidth) {
                blue[y * sampledWidth + x] = isBlue(bitmap.getPixel(x * SAMPLE_STEP, y * SAMPLE_STEP))
            }
        }

        val visited = BooleanArray(blue.size)
        val queue = ArrayDeque<Int>()
        for (origin in blue.indices) {
            if (!blue[origin] || visited[origin]) continue
            visited[origin] = true
            queue.add(origin)
            var count = 0
            var minX = sampledWidth
            var minY = sampledHeight
            var maxX = 0
            var maxY = 0

            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % sampledWidth
                val y = index / sampledWidth
                count++
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                addBlueNeighbor(x - 1, y, sampledWidth, sampledHeight, blue, visited, queue)
                addBlueNeighbor(x + 1, y, sampledWidth, sampledHeight, blue, visited, queue)
                addBlueNeighbor(x, y - 1, sampledWidth, sampledHeight, blue, visited, queue)
                addBlueNeighbor(x, y + 1, sampledWidth, sampledHeight, blue, visited, queue)
            }

            val width = maxX - minX + 1
            val height = maxY - minY + 1
            val area = width * height
            val aspect = width.toFloat() / height.coerceAtLeast(1)
            if (width < MIN_COMPONENT_SIZE || height < MIN_COMPONENT_SIZE ||
                width.toFloat() > sampledWidth * MAX_COMPONENT_WIDTH_RATIO ||
                aspect !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO ||
                count.toFloat() / area < MIN_BLUE_DENSITY
            ) {
                continue
            }

            var yellowCount = 0
            var redCount = 0
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val color = bitmap.getPixel(x * SAMPLE_STEP, y * SAMPLE_STEP)
                    if (isYellow(color)) yellowCount++
                    if (isRed(color)) redCount++
                }
            }
            val yellowRatio = yellowCount.toFloat() / area
            val redRatio = redCount.toFloat() / area
            if (yellowRatio in MIN_YELLOW_RATIO..MAX_YELLOW_RATIO && redRatio in MIN_RED_RATIO..MAX_RED_RATIO) {
                return true
            }
        }
        return false
    }

    private fun addBlueNeighbor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        blue: BooleanArray,
        visited: BooleanArray,
        queue: ArrayDeque<Int>,
    ) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * width + x
        if (blue[index] && !visited[index]) {
            visited[index] = true
            queue.add(index)
        }
    }

    private fun isBlue(color: Int): Boolean {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        return blue >= 105 && blue * 10 >= red * 14 && blue * 10 >= green * 12
    }

    private fun isYellow(color: Int): Boolean {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        return red >= 185 && green >= 160 && blue <= 105 && red - blue >= 90
    }

    private fun isRed(color: Int): Boolean {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        return red >= 145 && green <= 115 && blue <= 115 && red >= green + 65
    }

    companion object {
        private const val SAMPLE_STEP = 4
        private const val MAX_VERTICAL_RATIO = 0.48f
        private const val MIN_COMPONENT_SIZE = 9
        private const val MAX_COMPONENT_WIDTH_RATIO = 0.35f
        private const val MIN_ASPECT_RATIO = 0.72f
        private const val MAX_ASPECT_RATIO = 1.38f
        private const val MIN_BLUE_DENSITY = 0.18f
        private const val MIN_YELLOW_RATIO = 0.16f
        private const val MAX_YELLOW_RATIO = 0.68f
        private const val MIN_RED_RATIO = 0.008f
        private const val MAX_RED_RATIO = 0.18f
    }
}
