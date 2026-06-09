package com.nam.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_FONT_SIZE = intPreferencesKey("font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        val KEY_PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val KEY_CHAR_SPACING = floatPreferencesKey("char_spacing")
        val KEY_THEME_INDEX = intPreferencesKey("theme_index")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_TEXT_JUSTIFY = booleanPreferencesKey("text_justify")
        val KEY_TEXT_INDENT = booleanPreferencesKey("text_indent")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_READING_MODE = intPreferencesKey("reading_mode") // 0: Scroll, 1: Page Flip
        val KEY_AUTO_SCROLL_SPEED = intPreferencesKey("auto_scroll_speed") // 0: Off, 1-10: Speed
        
        val KEY_MARGIN_LEFT = intPreferencesKey("margin_left")
        val KEY_MARGIN_RIGHT = intPreferencesKey("margin_right")
        val KEY_MARGIN_TOP = intPreferencesKey("margin_top")
        val KEY_MARGIN_BOTTOM = intPreferencesKey("margin_bottom")
        val KEY_BRIGHTNESS = floatPreferencesKey("brightness")
        val KEY_TEXT_INDENT_VALUE = floatPreferencesKey("text_indent_value")
        val KEY_TEXT_ALIGN = intPreferencesKey("text_align")
        val KEY_EYE_COMFORT = booleanPreferencesKey("eye_comfort")
        val KEY_DOUBLE_PAGE = booleanPreferencesKey("double_page")
    }

    val fontSize: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_FONT_SIZE] ?: 18
    }

    val lineHeight: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_LINE_HEIGHT] ?: 1.6f
    }

    val paragraphSpacing: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_PARAGRAPH_SPACING] ?: 1.0f
    }

    val charSpacing: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_CHAR_SPACING] ?: 0.0f
    }

    val themeIndex: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_INDEX] ?: 0 // 0: Vintage/Giấy vàng, 1: Trắng, 2: Xanh lá, 3: Xám, 4: Đêm, 5: Gỗ
    }

    val fontFamily: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_FONT_FAMILY] ?: "serif"
    }

    val textJustify: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TEXT_JUSTIFY] ?: true
    }

    val textIndent: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_TEXT_INDENT] ?: true
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEEP_SCREEN_ON] ?: false
    }

    val readingMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_READING_MODE] ?: 0
    }

    val autoScrollSpeed: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SCROLL_SPEED] ?: 0
    }

    val marginLeft: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MARGIN_LEFT] ?: 20
    }
    val marginRight: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MARGIN_RIGHT] ?: 20
    }
    val marginTop: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MARGIN_TOP] ?: 24
    }
    val marginBottom: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_MARGIN_BOTTOM] ?: 24
    }

    val brightness: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_BRIGHTNESS] ?: -1f
    }

    val textIndentValue: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_TEXT_INDENT_VALUE] ?: 1.5f
    }

    val textAlign: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_TEXT_ALIGN] ?: 3
    }

    val eyeComfort: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_EYE_COMFORT] ?: false
    }

    val doublePage: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DOUBLE_PAGE] ?: false
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = size
        }
    }

    suspend fun setLineHeight(height: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LINE_HEIGHT] = height
        }
    }

    suspend fun setParagraphSpacing(spacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PARAGRAPH_SPACING] = spacing
        }
    }

    suspend fun setCharSpacing(spacing: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CHAR_SPACING] = spacing
        }
    }

    suspend fun setThemeIndex(index: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_INDEX] = index
        }
    }

    suspend fun setFontFamily(font: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FONT_FAMILY] = font
        }
    }

    suspend fun setTextJustify(justify: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TEXT_JUSTIFY] = justify
        }
    }

    suspend fun setTextIndent(indent: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TEXT_INDENT] = indent
        }
    }

    suspend fun setKeepScreenOn(keep: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = keep
        }
    }

    suspend fun setReadingMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_READING_MODE] = mode
        }
    }

    suspend fun setAutoScrollSpeed(speed: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_SCROLL_SPEED] = speed
        }
    }

    suspend fun setMargins(left: Int, right: Int, top: Int, bottom: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MARGIN_LEFT] = left
            preferences[KEY_MARGIN_RIGHT] = right
            preferences[KEY_MARGIN_TOP] = top
            preferences[KEY_MARGIN_BOTTOM] = bottom
        }
    }

    suspend fun setBrightness(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BRIGHTNESS] = value
        }
    }

    suspend fun setTextIndentValue(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TEXT_INDENT_VALUE] = value
        }
    }

    suspend fun setTextAlign(align: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TEXT_ALIGN] = align
        }
    }

    suspend fun setEyeComfort(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EYE_COMFORT] = enabled
        }
    }

    suspend fun setDoublePage(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DOUBLE_PAGE] = enabled
        }
    }
}
