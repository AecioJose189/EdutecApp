// Porta de desktop/src/main/kotlin/com/edutec/app/ui/LayoutGrafo.kt: mesma regra do app do professor.

/** Lugar de um nó no layout em camadas: coluna `camada` (da esquerda) e linha `ordem` dentro dela. */
export interface PosicaoNo {
  camada: number
  ordem: number
  tamanhoCamada: number
  totalCamadas: number
}

/** Centro do nó em frações da largura e da altura da área do grafo (0–1). */
export const fracaoX = (p: PosicaoNo) => (p.camada + 0.5) / p.totalCamadas
export const fracaoY = (p: PosicaoNo) => (p.ordem + 0.5) / p.tamanhoCamada

/**
 * Layout automático em camadas (estilo Sugiyama simplificado) para o grafo de pré-requisitos:
 * - camada = profundidade = maior caminho de pré-requisitos até o conceito (sem pré-requisito = 0);
 *   as camadas vão da esquerda (base) para a direita;
 * - ordem dentro da camada pela heurística do baricentro, em algumas varreduras para a direita e para
 *   a esquerda, o que costuma reduzir os cruzamentos de setas. Empates mantêm a ordem anterior.
 *
 * `prerequisitos`: conceito → seus pré-requisitos, na ordem em que os conceitos devem aparecer de início.
 * Pré-requisitos que não são conceitos do mapa são ignorados; ciclos (não deveriam existir) não travam.
 */
export function layoutEmCamadas(prerequisitos: Map<string, string[]>, varreduras = 4): Map<string, PosicaoNo> {
  if (prerequisitos.size === 0) return new Map()
  const nomes = [...prerequisitos.keys()]
  const pres = new Map(
    nomes.map((n) => [n, [...new Set(prerequisitos.get(n)!.filter((p) => prerequisitos.has(p) && p !== n))]]),
  )
  const dependentes = new Map(nomes.map((n) => [n, nomes.filter((d) => pres.get(d)!.includes(n))]))

  // Profundidade por memória; "visitando" corta um eventual ciclo.
  const profundidade = new Map<string, number>()
  const visitando = new Set<string>()
  function prof(n: string): number {
    const salvo = profundidade.get(n)
    if (salvo !== undefined) return salvo
    if (visitando.has(n)) return 0
    visitando.add(n)
    const p = pres.get(n)!.reduce((max, pre) => Math.max(max, prof(pre) + 1), 0)
    visitando.delete(n)
    profundidade.set(n, p)
    return p
  }
  nomes.forEach(prof)

  const totalCamadas = Math.max(...profundidade.values()) + 1
  const camadas = Array.from({ length: totalCamadas }, (_, c) => nomes.filter((n) => profundidade.get(n) === c))

  function posicao(n: string): number {
    const camada = camadas[profundidade.get(n)!]
    return (camada.indexOf(n) + 0.5) / camada.length
  }

  function reordenar(c: number, vizinhos: (n: string) => string[]) {
    const camada = camadas[c]
    const atual = new Map(camada.map((n, i) => [n, (i + 0.5) / camada.length]))
    const chave = new Map(
      camada.map((n) => {
        const v = vizinhos(n)
        return [n, v.length > 0 ? v.map(posicao).reduce((a, b) => a + b, 0) / v.length : atual.get(n)!]
      }),
    )
    // Array.prototype.sort é estável; desempate pela posição atual, como no Kotlin.
    camadas[c] = [...camada].sort((a, b) => chave.get(a)! - chave.get(b)! || atual.get(a)! - atual.get(b)!)
  }

  for (let v = 0; v < varreduras; v++) {
    for (let c = 1; c < totalCamadas; c++) reordenar(c, (n) => pres.get(n)!)
    for (let c = totalCamadas - 2; c >= 0; c--) reordenar(c, (n) => dependentes.get(n)!)
  }
  // Termina com uma varredura para a direita: a base fica como as últimas varreduras deixaram.
  for (let c = 1; c < totalCamadas; c++) reordenar(c, (n) => pres.get(n)!)

  const resultado = new Map<string, PosicaoNo>()
  camadas.forEach((camada, c) =>
    camada.forEach((n, i) => resultado.set(n, { camada: c, ordem: i, tamanhoCamada: camada.length, totalCamadas })),
  )
  return resultado
}

/** Quantos pares de arestas (pré-requisito → conceito) se cruzam entre camadas vizinhas. */
export function cruzamentos(prerequisitos: Map<string, string[]>, layout: Map<string, PosicaoNo>): number {
  const arestas: [string, string][] = []
  prerequisitos.forEach((ps, n) =>
    ps.forEach((p) => {
      if (layout.has(p) && layout.has(n) && layout.get(n)!.camada - layout.get(p)!.camada === 1) arestas.push([p, n])
    }),
  )
  let total = 0
  for (let i = 0; i < arestas.length; i++) {
    for (let j = i + 1; j < arestas.length; j++) {
      const [a1, b1] = arestas[i]
      const [a2, b2] = arestas[j]
      if (layout.get(a1)!.camada !== layout.get(a2)!.camada) continue
      const dy1 = fracaoY(layout.get(a1)!) - fracaoY(layout.get(a2)!)
      const dy2 = fracaoY(layout.get(b1)!) - fracaoY(layout.get(b2)!)
      if (dy1 * dy2 < 0) total++
    }
  }
  return total
}
