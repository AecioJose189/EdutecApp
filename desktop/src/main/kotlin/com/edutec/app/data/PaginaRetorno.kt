package com.edutec.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.edutec.app.ui.theme.CoresClaras
import com.edutec.app.ui.theme.CoresEscuras
import com.edutec.app.ui.theme.CoresLumini
import java.util.Base64

/**
 * Página que o navegador mostra em localhost:3737/callback ao fim do login Google, com a cara do Lumini:
 * logo e cores dos tokens do Design System (claro ou escuro, conforme o sistema do navegador).
 *
 * Tudo vai embutido no HTML (logo em data URI): o servidor local fecha logo depois do retorno, então a
 * página não pode pedir mais nada a ele.
 */
object PaginaRetorno {
    const val MENSAGEM_OK = "Pronto, você entrou. Pode fechar esta aba e voltar ao Lumini."

    fun html(ok: Boolean, mensagem: String): String {
        val logoClaro = logo("logo/lumini-horizontal-primaria.svg")
        val logoEscuro = logo("logo/lumini-horizontal-branco.svg")
        val titulo = if (ok) "Tudo certo" else "Não foi possível entrar"
        val simbolo = if (ok) "✓" else "!"
        return """
            <!doctype html>
            <html lang="pt-BR"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Lumini · $titulo</title>
            <style>
            :root { ${variaveis(CoresClaras)} color-scheme: light dark; }
            @media (prefers-color-scheme: dark) { :root { ${variaveis(CoresEscuras)} } }
            * { box-sizing: border-box; }
            body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
              background: var(--fundo); color: var(--tinta);
              font-family: "DM Sans", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
            .cartao { background: var(--superficie); border: 1px solid var(--borda); border-radius: 24px;
              padding: 40px 48px; max-width: 460px; margin: 24px; text-align: center; }
            .logo { height: 64px; margin-bottom: 24px; }
            .escuro { display: none; }
            @media (prefers-color-scheme: dark) { .claro { display: none; } .escuro { display: inline; } }
            .selo { width: 48px; height: 48px; border-radius: 50%; margin: 0 auto 16px; display: flex;
              align-items: center; justify-content: center; font-size: 26px; font-weight: 700;
              background: var(--${if (ok) "selo" else "erro"}); color: var(--${if (ok) "sobre-selo" else "sobre-erro"}); }
            h1 { font-family: "Sora", system-ui, sans-serif; font-size: 22px; margin: 0 0 8px; }
            p { font-size: 17px; line-height: 1.5; margin: 0; color: var(--tinta-suave); }
            </style></head>
            <body><main class="cartao">
            <img class="logo claro" alt="Lumini" src="$logoClaro"><img class="logo escuro" alt="Lumini" src="$logoEscuro">
            <div class="selo" aria-hidden="true">$simbolo</div>
            <h1>${titulo.escaparHtml()}</h1>
            <p>${mensagem.escaparHtml()}</p>
            </main></body></html>
        """.trimIndent()
    }

    private fun variaveis(c: CoresLumini) = listOf(
        "fundo" to c.fundo, "superficie" to c.superficie, "tinta" to c.tinta, "tinta-suave" to c.tintaSuave,
        "borda" to c.borda, "selo" to c.primaria, "sobre-selo" to c.sobrePrimaria, "erro" to c.erro,
        "sobre-erro" to c.sobreErro,
    ).joinToString(" ") { (nome, cor) -> "--$nome: ${cor.hex()};" }

    internal fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    private fun logo(caminho: String): String {
        val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(caminho)?.use { it.readBytes() }
            ?: return ""
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun String.escaparHtml() =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
