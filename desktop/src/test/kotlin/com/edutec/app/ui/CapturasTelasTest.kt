package com.edutec.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Capturas de todas as telas, nos dois temas, a partir dos próprios `@Preview` (sem login, sem rede).
 * Não roda no `./gradlew :desktop:test` comum; para gerar:
 *
 *     LUMINI_CAPTURAS=/tmp/capturas ./gradlew :desktop:test --tests '*CapturasTelasTest*'
 *
 * Saída: `<pasta>/<claro|escuro>/<Arquivo>-<Preview>.png` (1280×800, o tamanho padrão da janela).
 */
class CapturasTelasTest {
    private val arquivos = listOf(
        "LoginScreen", "MinhasTurmasScreen", "TurmaScreen", "NovaAtividadeScreen", "GerandoAtividade",
        "RevisaoAtividade", "ProjecaoScreen", "JuntarConceitos", "ConviteDialogo", "AbasTurma",
    )

    @Test
    fun capturar() {
        val pasta = System.getenv("LUMINI_CAPTURAS")?.takeIf { it.isNotBlank() }
        assumeTrue("defina LUMINI_CAPTURAS para gerar as capturas", pasta != null)
        val previews = arquivos.flatMap { nome ->
            val classe = runCatching { Class.forName("com.edutec.app.ui.${nome}Kt") }.getOrNull() ?: return@flatMap emptyList()
            classe.declaredMethods.filter {
                Modifier.isStatic(it.modifiers) && it.name.startsWith("Preview") &&
                    it.parameterTypes.contentEquals(arrayOf(Composer::class.java, Int::class.javaPrimitiveType))
            }.sortedBy { it.name }.map { nome to it.apply { isAccessible = true } }
        }
        for (tema in listOf("claro", "escuro")) {
            System.setProperty("lumini.tema", tema)
            val destino = File(pasta, tema).apply { mkdirs() }
            for ((arquivo, metodo) in previews) {
                // As telas de celular (Preview*Celular) ficam fora: o desktop tem largura mínima de 1024.
                if (metodo.name.endsWith("Celular")) continue
                val cena = ImageComposeScene(1280, 800, Density(1f)) { Chamar(metodo) }
                try {
                    cena.render() // primeiro quadro: carrega fontes e imagens
                    val imagem = cena.render(500_000_000L)
                    val png = imagem.encodeToData(EncodedImageFormat.PNG) ?: continue
                    File(destino, "$arquivo-${metodo.name.removePrefix("Preview")}.png").writeBytes(png.bytes)
                } finally {
                    cena.close()
                }
            }
        }
        System.clearProperty("lumini.tema")
    }
}

@Composable
private fun Chamar(metodo: Method) {
    metodo.invoke(null, currentComposer, 0)
}
