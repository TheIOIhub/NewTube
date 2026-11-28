package com.example.newtube.ui.video

import com.example.newtube.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiSettingsTest {
    @Test
    fun `default quality label is Auto`() {
        assertEquals("Авто", PlayerUiSettings().qualityLabel())
    }

    @Test
    fun `explicit quality label uses pixels`() {
        assertEquals("720p", PlayerUiSettings(quality = 720).qualityLabel())
    }

    @Test
    fun `speed label shows three times`() {
        assertEquals("3x", PlayerUiSettings(speed = 3f).speedLabel())
    }

    @Test
    fun `resize cycles fit fill zoom`() {
        val fit = PlayerUiSettings(resize = AppSettings.RESIZE_FIT)
        val fill = fit.copy(resize = fit.nextResize())
        val zoom = fill.copy(resize = fill.nextResize())
        assertEquals(AppSettings.RESIZE_FILL, fill.resize)
        assertEquals(AppSettings.RESIZE_ZOOM, zoom.resize)
        assertEquals(AppSettings.RESIZE_FIT, zoom.nextResize())
    }

    @Test
    fun `time formats minutes and hours`() {
        assertEquals("0:27", formatPlayerTime(27_000L))
        assertEquals("1:01:03", formatPlayerTime(3_663_000L))
    }

    @Test
    fun `settings rows use Ukrainian labels`() {
        assertEquals("Якість", PlayerLabels.QUALITY)
        assertEquals("Швидкість", PlayerLabels.SPEED)
        assertEquals("Аудіодоріжка", PlayerLabels.AUDIO)
        assertEquals("Повторювати", PlayerLabels.REPEAT)
        assertEquals("Екран", PlayerLabels.SCREEN)
        assertEquals("Жест яскравості", PlayerLabels.BRIGHTNESS_GESTURE)
        assertEquals("Жест збільшення", PlayerLabels.ZOOM_GESTURE)
        assertEquals("Швидкість під час утримання", PlayerLabels.HOLD_SPEED)
        assertEquals("PiP", PlayerLabels.PIP)
        assertEquals("Субтитри", PlayerLabels.SUBTITLES)
        assertEquals("Таймер", PlayerLabels.TIMER)
        assertEquals("Кастинг", PlayerLabels.CAST)
        assertEquals("Браузер", PlayerLabels.BROWSER)
        assertEquals("Недоступно", PlayerLabels.UNAVAILABLE)
    }
}
