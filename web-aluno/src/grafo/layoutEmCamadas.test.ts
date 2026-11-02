// Mesmos casos de desktop/src/test/kotlin/com/edutec/app/ui/LayoutGrafoTest.kt.
import { describe, expect, it } from 'vitest'
import { cruzamentos, fracaoX, fracaoY, layoutEmCamadas } from './layoutEmCamadas'

const mapa = (o: Record<string, string[]>) => new Map(Object.entries(o))

describe('layoutEmCamadas', () => {
  it('camada é o maior caminho de pré-requisitos', () => {
    // D depende de A (direto) e de C (A → B → C): fica na camada 3, não na 1.
    const layout = layoutEmCamadas(mapa({ A: [], B: ['A'], C: ['B'], D: ['A', 'C'] }))
    expect(['A', 'B', 'C', 'D'].map((n) => layout.get(n)!.camada)).toEqual([0, 1, 2, 3])
    expect(layout.get('A')!.totalCamadas).toBe(4)
  })

  it('camadas vão da esquerda para a direita', () => {
    const layout = layoutEmCamadas(mapa({ Base: [], Topo: ['Base'] }))
    expect(fracaoX(layout.get('Base')!)).toBeLessThan(fracaoX(layout.get('Topo')!))
    expect(fracaoY(layout.get('Base')!)).toBe(0.5)
  })

  it('baricentro desfaz cruzamentos', () => {
    // Na ordem de entrada, X2 depende de A1 e X1 de A2: as setas se cruzam. O layout troca a ordem.
    const grafo = mapa({ A1: [], A2: [], X1: ['A2'], X2: ['A1'] })
    expect(cruzamentos(grafo, layoutEmCamadas(grafo))).toBe(0)
  })

  it('grafo do teste.pdf sem cruzamentos', () => {
    const grafo = mapa({
      'Jogos de Soma Zero': [],
      'Algoritmo Minimax': ['Jogos de Soma Zero'],
      'Poda Alfa-Beta': ['Algoritmo Minimax'],
      'Decisões em Tempo Real': ['Algoritmo Minimax'],
      Quiescência: ['Decisões em Tempo Real'],
    })
    const layout = layoutEmCamadas(grafo)
    expect(cruzamentos(grafo, layout)).toBe(0)
    expect(layout.get('Poda Alfa-Beta')!.tamanhoCamada).toBe(2)
    expect(layout.get('Quiescência')!.camada).toBe(3)
  })

  it('ciclo e pré-requisito desconhecido não travam', () => {
    const layout = layoutEmCamadas(mapa({ A: ['B', 'Fora'], B: ['A'] }))
    expect(new Set(layout.keys())).toEqual(new Set(['A', 'B']))
  })

  it('vazio dá vazio', () => {
    expect(layoutEmCamadas(new Map()).size).toBe(0)
  })
})
