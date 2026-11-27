/** "4 de nov. de 2026, 18:40" (no fuso do aparelho; `fuso` só para os testes). */
export function formatarEnvio(iso: string, fuso?: string): string {
  const data = new Date(iso)
  if (Number.isNaN(data.getTime())) return ''
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short', timeZone: fuso }).format(data)
}

/** "3 de 4 acertos"; sem múltipla escolha (só abertas), não há placar. */
export function textoAcertos(acertos: number, total: number): string | null {
  if (total === 0) return null
  return `${acertos} de ${total} ${total === 1 ? 'acerto' : 'acertos'}`
}

/** Rótulo da alternativa pela POSIÇÃO na lista (A, B, C…), nunca pelo id: as alternativas podem vir embaralhadas. */
export const letra = (posicao: number) => String.fromCharCode(65 + posicao)
