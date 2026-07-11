package be.supsecu.app.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import be.supsecu.app.core.AccessibleSignal
import be.supsecu.app.core.NodeRole
import be.supsecu.app.core.PageZone
import be.supsecu.app.core.UrlNormalizer
import java.util.ArrayDeque
import java.util.Locale

class BrowserPageReader(
    private val urlNormalizer: UrlNormalizer,
) {
    fun read(root: AccessibilityNodeInfo, event: BrowserEventContext?): BrowserSnapshot? {
        val packageName = root.packageName?.toString() ?: return null
        val rootBounds = Rect().also(root::getBoundsInScreen)
        if (rootBounds.width() <= 0 || rootBounds.height() <= 0) return null

        val nodes = collectNodes(root)
        val hasWebContent = nodes.any { node -> isWebContentNode(node.className?.toString()) }
        val knownBrowser = packageName in KNOWN_BROWSER_PACKAGES ||
            KNOWN_BROWSER_PREFIXES.any(packageName::startsWith)

        val addressCandidate = nodes
            .asSequence()
            .mapNotNull { node -> scoreAddressCandidate(node, rootBounds, knownBrowser, hasWebContent) }
            .maxByOrNull(AddressCandidate::score)
            ?.takeIf { it.score >= MIN_ADDRESS_SCORE }
            ?: return null

        val pageRoot = nodes.firstOrNull { node -> isWebContentNode(node.className?.toString()) } ?: root
        val pageBounds = Rect().also(pageRoot::getBoundsInScreen)
        val signals = collectSignals(
            pageRoot = pageRoot,
            pageBounds = pageBounds.takeIf { it.height() > 0 } ?: rootBounds,
            excludedAddressText = addressCandidate.text,
            event = event,
        )

        return BrowserSnapshot(
            url = addressCandidate.text,
            packageName = packageName,
            windowId = root.windowId,
            signals = signals,
        )
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(MAX_NODE_COUNT)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && result.size < MAX_NODE_COUNT) {
            val node = queue.removeFirst()
            result += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
    }

    private fun scoreAddressCandidate(
        node: AccessibilityNodeInfo,
        rootBounds: Rect,
        knownBrowser: Boolean,
        hasWebContent: Boolean,
    ): AddressCandidate? {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || urlNormalizer.normalize(text) == null) return null

        val id = node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
        val className = node.className?.toString().orEmpty()
        val description = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
        val bounds = Rect().also(node::getBoundsInScreen)
        val knownAddressId = ADDRESS_ID_HINTS.any { hint -> hint in id }
        val editable = node.isEditable || className.endsWith("EditText")
        val nearTop = bounds.centerY() <= rootBounds.top + (rootBounds.height() * ADDRESS_ZONE_RATIO).toInt()
        val describedAsAddress = ADDRESS_DESCRIPTION_HINTS.any { hint -> hint in description }

        if (!knownAddressId && !(editable && nearTop && (knownBrowser || hasWebContent))) return null

        var score = 30
        if (knownAddressId) score += 100
        if (editable) score += 35
        if (nearTop) score += 25
        if (node.isFocused) score += 12
        if (describedAsAddress) score += 20
        if (knownBrowser) score += 15
        if (hasWebContent) score += 10
        if (text.startsWith("http://") || text.startsWith("https://")) score += 10

        return AddressCandidate(text = text, score = score)
    }

    private fun collectSignals(
        pageRoot: AccessibilityNodeInfo,
        pageBounds: Rect,
        excludedAddressText: String,
        event: BrowserEventContext?,
    ): List<AccessibleSignal> {
        val signals = ArrayList<AccessibleSignal>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(pageRoot)
        var totalCharacters = 0

        while (queue.isNotEmpty() && signals.size < MAX_SIGNAL_COUNT && totalCharacters < MAX_TEXT_CHARACTERS) {
            val node = queue.removeFirst()
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
            if (!node.isVisibleToUser) continue

            val texts = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
            ).map(String::trim).filter(String::isNotEmpty)

            for (text in texts) {
                if (text == excludedAddressText || text.length > MAX_SINGLE_SIGNAL_LENGTH) continue
                val key = "${node.className}|$text"
                if (!seen.add(key)) continue

                val bounds = Rect().also(node::getBoundsInScreen)
                val role = roleFor(node, text == node.contentDescription?.toString())
                signals += AccessibleSignal(
                    text = text,
                    role = role,
                    zone = zoneFor(bounds, pageBounds),
                    visibleToUser = true,
                    interactive = node.isClickable || role == NodeRole.BUTTON || role == NodeRole.LINK,
                    passwordField = node.isPassword,
                )
                totalCharacters += text.length
                if (signals.size >= MAX_SIGNAL_COUNT || totalCharacters >= MAX_TEXT_CHARACTERS) break
            }
        }

        if (event?.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.title?.trim()?.takeIf { title ->
                title.isNotEmpty() && title != excludedAddressText && title.length <= MAX_SINGLE_SIGNAL_LENGTH
            }?.let { title ->
                signals.add(
                    0,
                    AccessibleSignal(
                        text = title,
                        role = NodeRole.PAGE_TITLE,
                        zone = PageZone.HEADER,
                    ),
                )
            }
        }
        return signals
    }

    private fun roleFor(node: AccessibilityNodeInfo, usedDescription: Boolean): NodeRole {
        val className = node.className?.toString().orEmpty()
        return when {
            className.endsWith("Image") || className.endsWith("ImageView") -> NodeRole.IMAGE_DESCRIPTION
            usedDescription && !node.isEditable -> NodeRole.IMAGE_DESCRIPTION
            node.isPassword || node.isEditable || className.endsWith("EditText") -> NodeRole.INPUT
            className.endsWith("Button") -> NodeRole.BUTTON
            node.isClickable && className.endsWith("TextView") -> NodeRole.LINK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isHeading -> NodeRole.HEADING
            else -> NodeRole.BODY
        }
    }

    private fun zoneFor(bounds: Rect, pageBounds: Rect): PageZone {
        if (bounds.height() <= 0 || pageBounds.height() <= 0) return PageZone.UNKNOWN
        val relativeCenter = (bounds.centerY() - pageBounds.top).toFloat() / pageBounds.height()
        return when {
            relativeCenter <= 0.25f -> PageZone.HEADER
            relativeCenter >= 0.80f -> PageZone.FOOTER
            else -> PageZone.MAIN
        }
    }

    private fun isWebContentNode(className: String?): Boolean {
        val value = className?.lowercase(Locale.ROOT).orEmpty()
        return "webview" in value || "geckoview" in value || "browserview" in value
    }

    private data class AddressCandidate(
        val text: String,
        val score: Int,
    )

    companion object {
        private const val MAX_NODE_COUNT = 650
        private const val MAX_SIGNAL_COUNT = 500
        private const val MAX_TEXT_CHARACTERS = 64 * 1024
        private const val MAX_SINGLE_SIGNAL_LENGTH = 1_024
        private const val MIN_ADDRESS_SCORE = 85
        private const val ADDRESS_ZONE_RATIO = 0.38f

        private val ADDRESS_ID_HINTS = setOf(
            "url_bar",
            "address_bar",
            "location_bar",
            "url_field",
            "toolbar_url",
            "browser_toolbar_url",
            "omnibox",
        )
        private val ADDRESS_DESCRIPTION_HINTS = setOf(
            "adresse",
            "address",
            "url",
            "website",
            "site web",
        )
        private val KNOWN_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.opera.browser",
            "com.vivaldi.browser",
        )
        private val KNOWN_BROWSER_PREFIXES = setOf(
            "org.chromium.",
            "com.kiwibrowser.",
        )
    }
}
