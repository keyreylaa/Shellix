package com.rk.shellix.ui.screens.terminal

/**
 * Built-in terminal color schemes. Each scheme is rendered to a termux
 * `colors.properties` file (see [TerminalThemes.applyScheme]).
 *
 * Colors are 6-digit hex (RRGGBB). `null` entries fall back to the termux
 * default for that slot.
 */
data class ColorScheme(
    val name: String,
    val background: String? = null,
    val foreground: String? = null,
    val cursor: String? = null,
    val colors: List<String> = List(16) { "#000000" }
) {
    fun toProperties(): String = buildString {
        background?.let { appendLine("background=#$it") }
        foreground?.let { appendLine("foreground=#$it") }
        cursor?.let { appendLine("cursor=#$it") }
        colors.forEachIndexed { i, c -> appendLine("color$i=#$c") }
    }
}

object TerminalColorSchemes {
    val DEFAULT = ColorScheme("Default")

    val DRACULA = ColorScheme(
        name = "Dracula",
        background = "282a36",
        foreground = "f8f8f2",
        cursor = "f8f8f2",
        colors = listOf(
            "21222c", "ff5555", "50fa7b", "f1fa8c",
            "bd93f9", "ff79c6", "8be9fd", "f8f8f2",
            "6272a4", "ff6e6e", "69ff94", "ffffa5",
            "d6acff", "ff92df", "a4ffff", "ffffff"
        )
    )

    val NORD = ColorScheme(
        name = "Nord",
        background = "2e3440",
        foreground = "d8dee9",
        cursor = "d8dee9",
        colors = listOf(
            "3b4252", "bf616a", "a3be8c", "ebcb8b",
            "81a1c1", "b48ead", "88c0d0", "e5e9f0",
            "4c566a", "bf616a", "a3be8c", "ebcb8b",
            "81a1c1", "b48ead", "8fbcbb", "eceff4"
        )
    )

    val ONE_DARK = ColorScheme(
        name = "One Dark",
        background = "282c34",
        foreground = "abb2bf",
        cursor = "abb2bf",
        colors = listOf(
            "282c34", "e06c75", "98c379", "e5c07b",
            "61afef", "c678dd", "56b6c2", "abb2bf",
            "5c6370", "e06c75", "98c379", "e5c07b",
            "61afef", "c678dd", "56b6c2", "ffffff"
        )
    )

    val TOKYO_NIGHT = ColorScheme(
        name = "Tokyo Night",
        background = "1a1b26",
        foreground = "c0caf5",
        cursor = "c0caf5",
        colors = listOf(
            "15161e", "f7768e", "9ece6a", "e0af68",
            "7aa2f7", "bb9af7", "7dcfff", "a9b1d6",
            "414868", "f7768e", "9ece6a", "e0af68",
            "7aa2f7", "bb9af7", "7dcfff", "c0caf5"
        )
    )

    val CATPPPUCCIN = ColorScheme(
        name = "Catppuccin",
        background = "1e1e2e",
        foreground = "cdd6f4",
        cursor = "cdd6f4",
        colors = listOf(
            "45475a", "f38ba8", "a6e3a1", "f9e2af",
            "89b4fa", "cba6f7", "94e2d5", "bac2de",
            "585b70", "f38ba8", "a6e3a1", "f9e2af",
            "89b4fa", "cba6f7", "94e2d5", "cdd6f4"
        )
    )

    val ALL = listOf(DEFAULT, DRACULA, NORD, ONE_DARK, TOKYO_NIGHT, CATPPPUCCIN)

    fun byName(name: String): ColorScheme = ALL.firstOrNull { it.name == name } ?: DEFAULT
}
