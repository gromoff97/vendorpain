package ru.vp.ui

import java.nio.file.Path
import java.util.prefs.Preferences

interface PreferencesStore {
    fun lastConfigPath(): Path?
    fun saveLastConfigPath(path: Path)
}

class UiPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(UiPreferences::class.java),
) : PreferencesStore {
    override fun lastConfigPath(): Path? =
        preferences.get(LAST_CONFIG_PATH, null)?.let(Path::of)

    override fun saveLastConfigPath(path: Path) {
        preferences.put(LAST_CONFIG_PATH, path.toString())
    }

    private companion object {
        const val LAST_CONFIG_PATH = "lastConfigPath"
    }
}
