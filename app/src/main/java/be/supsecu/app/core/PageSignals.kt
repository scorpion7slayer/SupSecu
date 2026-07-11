package be.supsecu.app.core

enum class NodeRole {
    PAGE_TITLE,
    HEADING,
    IMAGE_DESCRIPTION,
    BODY,
    FOOTER,
    BUTTON,
    LINK,
    INPUT,
    INPUT_LABEL,
}

enum class PageZone {
    HEADER,
    MAIN,
    FOOTER,
    UNKNOWN,
}

data class AccessibleSignal(
    val text: String,
    val role: NodeRole,
    val zone: PageZone,
    val visibleToUser: Boolean = true,
    val interactive: Boolean = false,
    val passwordField: Boolean = false,
)
