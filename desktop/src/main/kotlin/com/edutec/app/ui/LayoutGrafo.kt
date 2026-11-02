package com.edutec.app.ui

/** Lugar de um nó no layout em camadas: coluna [camada] (da esquerda) e linha [ordem] dentro dela. */
data class PosicaoNo(val camada: Int, val ordem: Int, val tamanhoCamada: Int, val totalCamadas: Int) {
    /** Centro do nó em frações da largura e da altura da área do grafo (0–1). */
    val x get() = (camada + 0.5f) / totalCamadas
    val y get() = (ordem + 0.5f) / tamanhoCamada
}

/**
 * Layout automático em camadas (estilo Sugiyama simplificado) para o grafo de pré-requisitos:
 * - camada = profundidade = maior caminho de pré-requisitos até o conceito (sem pré-requisito = 0);
 *   as camadas vão da esquerda (base) para a direita;
 * - ordem dentro da camada pela heurística do baricentro: cada nó vai para a média das posições dos
 *   vizinhos na camada ao lado, em algumas varreduras para a direita e para a esquerda, o que costuma
 *   reduzir os cruzamentos de setas. Empates mantêm a ordem anterior (resultado estável).
 *
 * [prerequisitos]: conceito → seus pré-requisitos, na ordem em que os conceitos devem aparecer de início.
 * Pré-requisitos que não são conceitos do mapa são ignorados; ciclos (não deveriam existir, RN03) não
 * travam o cálculo.
 */
fun layoutEmCamadas(prerequisitos: Map<String, List<String>>, varreduras: Int = 4): Map<String, PosicaoNo> {
    if (prerequisitos.isEmpty()) return emptyMap()
    val nomes = prerequisitos.keys.toList()
    val pres = nomes.associateWith { n -> prerequisitos.getValue(n).filter { it in prerequisitos && it != n }.distinct() }
    val dependentes = nomes.associateWith { n -> nomes.filter { n in pres.getValue(it) } }

    // Profundidade por memória; "visitando" corta um eventual ciclo.
    val profundidade = HashMap<String, Int>()
    val visitando = HashSet<String>()
    fun prof(n: String): Int = profundidade[n] ?: run {
        if (!visitando.add(n)) return 0
        val p = pres.getValue(n).maxOfOrNull { prof(it) + 1 } ?: 0
        visitando.remove(n)
        profundidade[n] = p
        p
    }
    nomes.forEach { prof(it) }

    val totalCamadas = profundidade.values.max() + 1
    val camadas = List(totalCamadas) { c -> nomes.filter { profundidade[it] == c }.toMutableList() }

    fun posicao(n: String): Double {
        val camada = camadas[profundidade.getValue(n)]
        return (camada.indexOf(n) + 0.5) / camada.size
    }

    fun reordenar(camada: MutableList<String>, vizinhos: (String) -> List<String>) {
        val atual = camada.withIndex().associate { (i, n) -> n to (i + 0.5) / camada.size }
        val chave = camada.associateWith { n ->
            vizinhos(n).takeIf { it.isNotEmpty() }?.map(::posicao)?.average() ?: atual.getValue(n)
        }
        val nova = camada.sortedWith(compareBy<String> { chave.getValue(it) }.thenBy { atual.getValue(it) })
        camada.clear()
        camada.addAll(nova)
    }

    repeat(varreduras) {
        for (c in 1 until totalCamadas) reordenar(camadas[c]) { pres.getValue(it) }
        for (c in totalCamadas - 2 downTo 0) reordenar(camadas[c]) { dependentes.getValue(it) }
    }
    // Termina com uma varredura para a direita: a base fica como as últimas varreduras deixaram.
    for (c in 1 until totalCamadas) reordenar(camadas[c]) { pres.getValue(it) }

    return buildMap {
        camadas.forEachIndexed { c, camada ->
            camada.forEachIndexed { i, n -> put(n, PosicaoNo(c, i, camada.size, totalCamadas)) }
        }
    }
}

/** Quantos pares de arestas (pré-requisito → conceito) se cruzam entre camadas vizinhas. */
fun cruzamentos(prerequisitos: Map<String, List<String>>, layout: Map<String, PosicaoNo>): Int {
    val arestas = prerequisitos.flatMap { (n, ps) -> ps.filter { it in layout }.map { it to n } }
        .filter { (p, n) -> layout.getValue(n).camada - layout.getValue(p).camada == 1 }
    var total = 0
    for (i in arestas.indices) for (j in i + 1 until arestas.size) {
        val (a1, b1) = arestas[i]
        val (a2, b2) = arestas[j]
        if (layout.getValue(a1).camada != layout.getValue(a2).camada) continue
        val dy1 = layout.getValue(a1).y - layout.getValue(a2).y
        val dy2 = layout.getValue(b1).y - layout.getValue(b2).y
        if (dy1 * dy2 < 0) total++
    }
    return total
}
