package com.edutec.app.ui

/** Dado carregado do servidor para uma parte da tela. */
sealed interface Carga<out T> {
    data object Carregando : Carga<Nothing>
    data class Erro(val mensagem: String) : Carga<Nothing>
    data class Pronto<T>(val valor: T) : Carga<T>
}
