package com.example.newtube.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageLabelsTest {
    @Test
    fun `language codes resolve to full Ukrainian names`() {
        assertEquals("Українська", languageDisplayName("uk"))
        assertEquals("Російська", languageDisplayName("ru"))
        assertEquals("Англійська", languageDisplayName("en"))
        assertEquals("Польська", languageDisplayName("pl"))
    }

    @Test
    fun `unknown language code is shown as is`() {
        assertEquals("xx", languageDisplayName("xx"))
    }
}
