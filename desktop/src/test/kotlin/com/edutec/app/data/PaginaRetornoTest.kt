package com.edutec.app.data

import com.edutec.app.data.PaginaRetorno.hex
import com.edutec.app.ui.theme.CoresClaras
import com.edutec.app.ui.theme.CoresEscuras
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginaRetornoTest {
    @Test
    fun `sucesso diz para fechar a aba e voltar ao Lumini`() {
        val html = PaginaRetorno.html(true, PaginaRetorno.MENSAGEM_OK)
        assertTrue(html.contains("Pronto, você entrou. Pode fechar esta aba e voltar ao Lumini."))
        assertTrue(html.contains("✓"))
    }

    @Test
    fun `logo embutido nas duas versoes, sem depender do servidor local`() {
        val html = PaginaRetorno.html(true, PaginaRetorno.MENSAGEM_OK)
        assertTrue(Regex("""src="data:image/svg\+xml;base64,[A-Za-z0-9+/=]{100,}"""").findAll(html).count() == 2)
    }

    @Test
    fun `cores vem dos tokens dos dois temas`() {
        val html = PaginaRetorno.html(true, PaginaRetorno.MENSAGEM_OK)
        assertTrue(html.contains("--fundo: ${CoresClaras.fundo.hex()};"))
        assertTrue(html.contains("--fundo: ${CoresEscuras.fundo.hex()};"))
        assertTrue(html.contains("--selo: ${CoresClaras.primaria.hex()};"))
    }

    @Test
    fun `mensagem de erro do retorno e escapada`() {
        val html = PaginaRetorno.html(false, "<script>alert(1)</script>")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
