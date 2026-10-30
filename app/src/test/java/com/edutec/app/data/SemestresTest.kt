package com.edutec.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SemestresTest {
    private fun dia(ano: Int, mes: Int) = Calendar.getInstance().apply { set(ano, mes, 15) }

    @Test
    fun `outubro fica no segundo semestre`() {
        assertEquals(listOf("2026.2", "2026.1", "2025.2", "2025.1"), semestresRecentes(dia(2026, Calendar.OCTOBER)))
    }

    @Test
    fun `janeiro fica no primeiro semestre e volta um ano`() {
        assertEquals(listOf("2027.1", "2026.2", "2026.1"), semestresRecentes(dia(2027, Calendar.JANUARY), 3))
    }

    @Test
    fun `julho ja e segundo semestre`() {
        assertEquals("2026.2", semestresRecentes(dia(2026, Calendar.JULY)).first())
    }
}
