import { describe, expect, it } from 'vitest'
import { partesLatex } from './TextoLatex'

describe('partesLatex', () => {
  it('separa texto e fórmulas', () => {
    expect(partesLatex('Custo $O(b^m)$ no pior caso')).toEqual([
      { latex: false, conteudo: 'Custo ' },
      { latex: true, conteudo: 'O(b^m)' },
      { latex: false, conteudo: ' no pior caso' },
    ])
  })

  it('cifrão escapado é texto', () => {
    expect(partesLatex('Custa R\\$ 5')).toEqual([{ latex: false, conteudo: 'Custa R$ 5' }])
  })

  it('cifrão sem par vira texto', () => {
    expect(partesLatex('só $x')).toEqual([
      { latex: false, conteudo: 'só ' },
      { latex: false, conteudo: '$x' },
    ])
  })
})
