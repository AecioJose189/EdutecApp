package com.edutec.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.edutec.app.data.Turma

/**
 * Onde o professor está no app (Minhas turmas → Turma → Nova atividade) e as turmas criadas.
 * Fica num ViewModel para sobreviver à recriação da Activity (girar a tela, dividir a janela).
 */
class NavegacaoViewModel : ViewModel() {
    /** Começa vazia: as turmas são criadas pelo professor (só em memória até termos o banco). */
    val turmas = mutableStateListOf<Turma>()

    var turmaAberta by mutableStateOf<Turma?>(null)
        private set

    /** null = Nova atividade fechada; caso contrário, os conceitos já marcados ao abrir. */
    var novaAtividade by mutableStateOf<Set<String>?>(null)
        private set

    fun abrirTurma(turma: Turma) {
        turmaAberta = turma
    }

    fun fecharTurma() {
        turmaAberta = null
    }

    fun abrirNovaAtividade(conceitosIniciais: Set<String>) {
        novaAtividade = conceitosIniciais
    }

    fun fecharNovaAtividade() {
        novaAtividade = null
    }
}
