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
 * Saída: `<pasta>/<tela>[-<variante>]-<claro|escuro>.png`, ex.: `minhas-turmas-claro.png`,
 * `turma-sem-respostas-escuro.png`, `minhas-turmas-celular-claro.png`. Os de tablet saem em 1280×800 (o
 * tamanho padrão da janela); os `Preview*Celular` em 412×892.
 */
class CapturasTelasTest {
    private val arquivos = listOf(
        "LoginScreen", "MinhasTurmasScreen", "TurmaScreen", "NovaAtividadeScreen", "GerandoAtividade",
        "RevisaoAtividade", "ProjecaoScreen", "JuntarConceitos",
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
            val destino = File(pasta).apply { mkdirs() }
            for ((arquivo, metodo) in previews) {
                val celular = metodo.name.endsWith("Celular")
                val cena = if (celular) ImageComposeScene(412, 892, Density(1f)) { Chamar(metodo) }
                else ImageComposeScene(1280, 800, Density(1f)) { Chamar(metodo) }
                try {
                    cena.render() // primeiro quadro: carrega fontes e imagens
                    val imagem = cena.render(500_000_000L)
                    val png = imagem.encodeToData(EncodedImageFormat.PNG) ?: continue
                    File(destino, "${nomeCaptura(arquivo, metodo.name)}-$tema.png").writeBytes(png.bytes)
                } finally {
                    cena.close()
                }
            }
        }
        System.clearProperty("lumini.tema")
    }
}

/** Palavras de um nome em CamelCase: "MinhasTurmasScreen" → [Minhas, Turmas, Screen]. */
private fun palavras(nome: String) = Regex("[A-Z][a-z0-9]*").findAll(nome).map { it.value }.toList()

/**
 * "MinhasTurmasScreen" + "PreviewVazioTablet" → "minhas-turmas-vazio". A tela é o nome do arquivo sem
 * "Screen"; a variante é o nome do preview sem "Preview", sem "Tablet" (o padrão do desktop) e sem as
 * palavras que já estão no nome da tela ("PreviewTurmaTablet" → "turma").
 */
internal fun nomeCaptura(arquivo: String, preview: String): String {
    val tela = palavras(arquivo).filter { it != "Screen" }
    val variante = palavras(preview.removePrefix("Preview")).filter { it != "Tablet" && it !in tela }
    return (tela + variante).joinToString("-") { it.lowercase() }
}

@Composable
private fun Chamar(metodo: Method) {
    metodo.invoke(null, currentComposer, 0)
}
