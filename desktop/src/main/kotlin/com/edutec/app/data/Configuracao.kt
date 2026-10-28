package com.edutec.app.data

import java.io.File
import java.util.Properties

/**
 * URL e chave pública (publishable) do Supabase no desktop. O app instalado já vem com as do projeto
 * Lumini ([URL_PADRAO], [CHAVE_PUBLICA_PADRAO]); a chave pública pode ir no app e no git (quem protege os
 * dados é o RLS). Chave secreta (`sb_secret_`/`service_role`) nunca entra aqui.
 *
 * Ordem de procura (o primeiro que tiver o valor vence; sem nenhum, valem os padrões embutidos):
 * 1. variáveis de ambiente `SUPABASE_URL` e `SUPABASE_CHAVE_PUBLICA`;
 * 2. o arquivo indicado em `LUMINI_CONFIG`;
 * 3. `lumini.properties` na pasta de configuração do usuário
 *    (Linux `~/.config/lumini/`, Windows `%APPDATA%\Lumini\`, macOS `~/Library/Application Support/Lumini/`);
 * 4. `lumini.properties` na pasta atual.
 */
object Configuracao {
    const val ARQUIVO = "lumini.properties"
    const val URL_PADRAO = "https://vuxnyjkykftecqgznswg.supabase.co"
    const val CHAVE_PUBLICA_PADRAO = "sb_publishable_bjgZdjyi0tOtnXuO7CFNfQ_vG7my0VB"

    val supabaseUrl: String get() = valor("SUPABASE_URL").ifEmpty { URL_PADRAO }
    val chavePublica: String get() = valor("SUPABASE_CHAVE_PUBLICA").ifEmpty { CHAVE_PUBLICA_PADRAO }

    /** Onde o professor deve criar o arquivo (aparece na mensagem de erro quando falta configuração). */
    val arquivoDoUsuario: File by lazy { File(pastaDoUsuario(), ARQUIVO) }

    /** Sessão do login Google (tokens), ao lado da configuração e fora do repositório. */
    val arquivoSessao: File by lazy { File(pastaDoUsuario(), "sessao.json") }

    /** Arquivos procurados, na ordem. */
    val arquivos: List<File> by lazy {
        listOfNotNull(
            System.getenv("LUMINI_CONFIG")?.takeIf { it.isNotBlank() }?.let(::File),
            arquivoDoUsuario,
            File(ARQUIVO).absoluteFile,
        )
    }

    private val propriedades: Properties by lazy {
        Properties().apply {
            // Do último para o primeiro: o mais prioritário sobrescreve.
            for (arquivo in arquivos.reversed()) {
                if (arquivo.isFile) arquivo.inputStream().use { entrada -> load(entrada.reader(Charsets.UTF_8)) }
            }
        }
    }

    private fun valor(nome: String): String =
        System.getenv(nome)?.trim()?.takeIf { it.isNotEmpty() }
            ?: propriedades.getProperty(nome, "").trim()

    private fun pastaDoUsuario(): File {
        val casa = System.getProperty("user.home")
        val so = System.getProperty("os.name").lowercase()
        return when {
            so.contains("win") -> File(System.getenv("APPDATA") ?: casa, "Lumini")
            so.contains("mac") -> File(casa, "Library/Application Support/Lumini")
            else -> File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$casa/.config", "lumini")
        }
    }
}
