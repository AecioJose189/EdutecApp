import { describe, expect, it } from 'vitest'
import { formatarEnvio, letra, textoAcertos } from './formatar'

describe('formatarEnvio', () => {
  it('mostra data e hora no fuso pedido', () => {
    const texto = formatarEnvio('2026-11-04T21:40:00.000Z', 'America/Sao_Paulo')
    expect(texto).toMatch(/2026/)
    expect(texto).toMatch(/18:40/)
  })
  it('data inválida vira texto vazio', () => {
    expect(formatarEnvio('ontem')).toBe('')
  })
})

describe('textoAcertos', () => {
  it('singular, plural e sem múltipla escolha', () => {
    expect(textoAcertos(1, 1)).toBe('1 de 1 acerto')
    expect(textoAcertos(3, 4)).toBe('3 de 4 acertos')
    expect(textoAcertos(0, 0)).toBeNull()
  })
})

describe('letra', () => {
  it('vem da posição, não do id', () => {
    expect([0, 1, 2].map(letra)).toEqual(['A', 'B', 'C'])
  })
})
