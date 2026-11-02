import { ErroApi } from './tipos'

export interface ErroExplicado {
  titulo: string
  mensagem: string
  /** O que o aluno deve fazer agora. */
  dica: string
  /** Faz sentido oferecer "Tentar de novo". */
  repetir: boolean
}

const textos: Record<string, Omit<ErroExplicado, 'mensagem'>> = {
  nao_autenticado: { titulo: 'Você saiu da conta', dica: 'Entre com o Google de novo.', repetir: false },
  convite_invalido: {
    titulo: 'Convite não encontrado',
    dica: 'Confira o link com o professor. Ele pode ter gerado um novo convite.',
    repetir: false,
  },
  qr_expirado: { titulo: 'O código mudou', dica: 'Aponte a câmera para o QR de novo.', repetir: false },
  sessao_encerrada: {
    titulo: 'Atividade encerrada',
    dica: 'O professor já fechou esta atividade. Fale com ele se ainda precisar responder.',
    repetir: false,
  },
  nao_matriculado: {
    titulo: 'Você ainda não está nesta turma',
    dica: 'Peça ao professor o link de convite da turma, abra-o e depois leia o QR de novo.',
    repetir: false,
  },
  tentativa_ja_enviada: { titulo: 'Respostas já enviadas', dica: 'Não é preciso enviar de novo.', repetir: false },
  tempo_esgotado: {
    titulo: 'O tempo acabou',
    dica: 'O prazo desta atividade terminou antes do envio. Fale com o professor.',
    repetir: false,
  },
  sem_conexao: { titulo: 'Sem conexão', dica: 'Confira a internet do celular e tente de novo.', repetir: true },
  erro_interno: { titulo: 'Algo deu errado', dica: 'Tente de novo em alguns segundos.', repetir: true },
}

/** Mensagem do servidor em linguagem simples + o que fazer. */
export function explicarErro(e: unknown): ErroExplicado {
  const codigo = e instanceof ErroApi ? e.codigo : 'erro_interno'
  const base = textos[codigo] ?? textos.erro_interno
  const mensagem = e instanceof ErroApi && e.message ? e.message : ''
  return { ...base, mensagem }
}

export function codigoErro(e: unknown): string | null {
  return e instanceof ErroApi ? e.codigo : null
}
