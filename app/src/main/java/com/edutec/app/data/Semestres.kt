package com.edutec.app.data

import java.util.Calendar

/**
 * Semestres para o seletor, do atual para trás (ex.: em outubro de 2026: 2026.2, 2026.1, 2025.2...).
 * Calculado pela data do aparelho; não é dado de exemplo.
 */
fun semestresRecentes(hoje: Calendar = Calendar.getInstance(), quantidade: Int = 4): List<String> {
    var ano = hoje.get(Calendar.YEAR)
    var semestre = if (hoje.get(Calendar.MONTH) >= Calendar.JULY) 2 else 1
    return List(quantidade) {
        "$ano.$semestre".also {
            if (semestre == 2) semestre = 1 else { semestre = 2; ano-- }
        }
    }
}
