import { describe, expect, it } from 'vitest'
import { turmaForaDaTurma } from './erros'
import { ErroApi } from './tipos'

describe('turmaForaDaTurma', () => {
  it('devolve a turma do nao_matriculado', () => {
    const e = new ErroApi('nao_matriculado', '', { turma: { id: 't1', nome: 'IA', codigo: 'Turma A' } })
    expect(turmaForaDaTurma(e)).toEqual({ id: 't1', nome: 'IA', codigo: 'Turma A' })
  })
  it('sem turma no corpo (servidor do MVP) devolve null', () => {
    expect(turmaForaDaTurma(new ErroApi('nao_matriculado', ''))).toBeNull()
  })
  it('outros erros devolvem null', () => {
    expect(turmaForaDaTurma(new ErroApi('qr_expirado', '', { turma: { id: 't1', nome: 'IA' } }))).toBeNull()
    expect(turmaForaDaTurma(new Error('x'))).toBeNull()
  })
})
