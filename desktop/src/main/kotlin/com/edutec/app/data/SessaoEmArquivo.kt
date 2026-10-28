package com.edutec.app.data

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Guarda a sessão do login (access e refresh token) num arquivo da pasta de configuração do usuário,
 * legível só por ele. Fechar e abrir o Lumini não pede login de novo; "Sair" apaga o arquivo.
 */
class SessaoEmArquivo(private val arquivo: File) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        arquivo.parentFile.mkdirs()
        val temporario = File(arquivo.parentFile, "${arquivo.name}.tmp")
        temporario.writeText(json.encodeToString(UserSession.serializer(), session))
        somenteDono(temporario)
        Files.move(temporario.toPath(), arquivo.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        if (!arquivo.isFile) return@withContext null
        runCatching { json.decodeFromString(UserSession.serializer(), arquivo.readText()) }
            .onFailure { arquivo.delete() } // corrompido ou de outra versão: pede login de novo
            .getOrNull()
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        arquivo.delete()
        Unit
    }

    private fun somenteDono(f: File) {
        runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------")) }
            .onFailure { // Windows: sem POSIX; o arquivo já fica no perfil do usuário (%APPDATA%)
                f.setReadable(false, false); f.setReadable(true, true)
                f.setWritable(false, false); f.setWritable(true, true)
            }
    }
}
