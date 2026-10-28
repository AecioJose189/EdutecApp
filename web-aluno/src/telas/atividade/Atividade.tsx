import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { chamarAlunoApi } from '../../api/alunoApi'
import { codigoErro } from '../../api/erros'
import type { Resposta, ResultadoEnvio, Tentativa } from '../../api/tipos'
import { AvisoErro } from '../../componentes/AvisoErro'
import { Carregando } from '../../componentes/Carregando'
import { Marca } from '../../componentes/Topo'
import { useChamada } from '../../componentes/useChamada'
import {
  guardarTentativa,
  respondida,
  respostasSalvas,
  resultadoSalvo,
  salvarResultado,
  salvarRespostas,
  tentativaDoToken,
  type RespostaLocal,
  type RespostasLocais,
} from './armazenamento'
import { Cronometro, useSegundosRestantes } from './Cronometro'
import { Questao } from './Questao'
import { JaEnviada, Resultado } from './Resultado'
import { ConfirmarEnvio, Revisao } from './Revisao'

/** Envia sozinho quando faltam estes segundos, para chegar antes do prazo no servidor. */
const ENVIO_AUTOMATICO_S = 3

// O servidor aceita o token velho de quem já tem tentativa (recarregar funciona e traz ja_enviada
// atualizado). A cópia guardada só entra quando a rede da sala falha.
async function carregarTentativa(token: string): Promise<Tentativa> {
  try {
    const t = await chamarAlunoApi('entrar_sessao', { token })
    guardarTentativa(token, t)
    return t
  } catch (e) {
    const salva = codigoErro(e) === 'sem_conexao' ? tentativaDoToken(token) : null
    if (salva) return salva
    throw e
  }
}

export function Atividade() {
  const { token = '' } = useParams()
  const { estado, repetir } = useChamada(() => carregarTentativa(token), [token])

  if (estado.tipo === 'ok') return <Respondendo key={estado.dados.tentativa_id} tentativa={estado.dados} />
  return (
    <main className="pagina">
      <header className="topo">
        <Marca />
      </header>
      {estado.tipo === 'carregando' && <Carregando texto="Abrindo a atividade…" />}
      {estado.tipo === 'erro' && (
        <AvisoErro erro={estado.erro} aoRepetir={repetir}>
          <Link to="/" className="botao secundario-btn">
            Minhas turmas
          </Link>
        </AvisoErro>
      )}
    </main>
  )
}

type Fase =
  | { tipo: 'respondendo' }
  | { tipo: 'confirmando' }
  | { tipo: 'enviando'; automatico: boolean }
  | { tipo: 'enviada'; resultado: ResultadoEnvio | null; automatico: boolean }
  | { tipo: 'erro'; erro: unknown }

function paraEnvio(respostas: RespostasLocais): Resposta[] {
  return Object.entries(respostas)
    .filter(([, r]) => respondida(r))
    .map(([questao_id, r]) =>
      'alternativa_id' in r ? { questao_id, alternativa_id: r.alternativa_id } : { questao_id, texto: r.texto.trim() },
    )
}

function Respondendo({ tentativa }: { tentativa: Tentativa }) {
  const { atividade, tentativa_id: id } = tentativa
  const questoes = atividade.questoes
  const turmaId = atividade.turma.id

  const [respostas, setRespostas] = useState<RespostasLocais>(() => respostasSalvas(id))
  const [indice, setIndice] = useState(0) // questoes.length = tela de revisão
  const [fase, setFase] = useState<Fase>(() => {
    const r = resultadoSalvo(id)
    if (r) return { tipo: 'enviada', resultado: r, automatico: false }
    if (tentativa.ja_enviada) return { tipo: 'enviada', resultado: null, automatico: false }
    return { tipo: 'respondendo' }
  })

  const terminou = fase.tipo === 'enviada'
  const restante = useSegundosRestantes(terminou ? null : tentativa.prazo_em)

  function responder(questaoId: string, r: RespostaLocal) {
    setRespostas((atual) => {
      const novas = { ...atual, [questaoId]: r }
      salvarRespostas(id, novas)
      return novas
    })
  }

  function irPara(i: number) {
    setIndice(i)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const enviar = useCallback(
    async (automatico: boolean) => {
      setFase({ tipo: 'enviando', automatico })
      try {
        const resultado = await chamarAlunoApi('enviar_respostas', {
          tentativa_id: id,
          respostas: paraEnvio(respostas),
        })
        salvarResultado(id, resultado)
        salvarRespostas(id, null)
        setFase({ tipo: 'enviada', resultado, automatico })
      } catch (erro) {
        if (codigoErro(erro) === 'tentativa_ja_enviada') {
          salvarRespostas(id, null)
          setFase({ tipo: 'enviada', resultado: null, automatico: false })
        } else {
          setFase({ tipo: 'erro', erro })
        }
      }
      window.scrollTo({ top: 0 })
    },
    [id, respostas],
  )

  // Tempo acabando: envia o que já foi respondido, uma vez só.
  const enviouSozinho = useRef(false)
  useEffect(() => {
    if (restante === null || restante > ENVIO_AUTOMATICO_S || enviouSozinho.current) return
    if (fase.tipo === 'respondendo' || fase.tipo === 'confirmando') {
      enviouSozinho.current = true
      void enviar(true)
    }
  }, [restante, fase.tipo, enviar])

  const emBranco = questoes.filter((q) => !respondida(respostas[q.id])).length
  const bloqueada = fase.tipo !== 'respondendo' && fase.tipo !== 'confirmando'
  const naRevisao = indice >= questoes.length
  const esgotado = fase.tipo === 'erro' && codigoErro(fase.erro) === 'tempo_esgotado'

  return (
    <main className="pagina atividade">
      <div className="atividade-topo">
        <div>
          <p className="secundario">
            {atividade.turma.nome} · {atividade.turma.codigo}
          </p>
          <h1>{atividade.titulo}</h1>
        </div>
        {restante !== null && !terminou && <Cronometro restante={restante} />}
      </div>

      {fase.tipo === 'enviada' ? (
        fase.resultado ? (
          <Resultado resultado={fase.resultado} turmaId={turmaId} automatico={fase.automatico} />
        ) : (
          <JaEnviada turmaId={turmaId} />
        )
      ) : (
        <>
          {fase.tipo === 'erro' && (
            <AvisoErro erro={fase.erro} aoRepetir={() => void enviar(false)}>
              <Link to="/" className="botao secundario-btn">
                Minhas turmas
              </Link>
            </AvisoErro>
          )}
          {fase.tipo === 'enviando' && (
            <Carregando
              texto={fase.automatico ? 'O tempo acabou. Enviando suas respostas…' : 'Enviando suas respostas…'}
            />
          )}

          {fase.tipo !== 'enviando' && !esgotado && (
            <>
              <nav className="progresso" aria-label="Questões">
                {questoes.map((q, i) => (
                  <button
                    key={q.id}
                    type="button"
                    className={`ponto${i === indice ? ' atual' : ''}${respondida(respostas[q.id]) ? ' feita' : ''}`}
                    aria-label={`Questão ${i + 1}${respondida(respostas[q.id]) ? ', respondida' : ', em branco'}`}
                    aria-current={i === indice ? 'step' : undefined}
                    onClick={() => irPara(i)}
                  >
                    {i + 1}
                  </button>
                ))}
                <button
                  type="button"
                  className={`ponto ponto-revisao${naRevisao ? ' atual' : ''}`}
                  aria-current={naRevisao ? 'step' : undefined}
                  onClick={() => irPara(questoes.length)}
                >
                  Enviar
                </button>
              </nav>

              {naRevisao ? (
                <Revisao
                  questoes={questoes}
                  respostas={respostas}
                  irPara={irPara}
                  bloqueada={bloqueada}
                  aoEnviar={() => setFase({ tipo: 'confirmando' })}
                />
              ) : (
                <>
                  <p className="secundario">
                    Questão {indice + 1} de {questoes.length}
                  </p>
                  <Questao
                    questao={questoes[indice]}
                    numero={indice + 1}
                    resposta={respostas[questoes[indice].id]}
                    aoResponder={(r) => responder(questoes[indice].id, r)}
                    bloqueada={bloqueada}
                  />
                  <div className="navegacao">
                    <button className="botao secundario-btn" onClick={() => irPara(indice - 1)} disabled={indice === 0}>
                      ‹ Anterior
                    </button>
                    <button className="botao" onClick={() => irPara(indice + 1)}>
                      {indice === questoes.length - 1 ? 'Revisar e enviar' : 'Próxima ›'}
                    </button>
                  </div>
                </>
              )}
            </>
          )}
        </>
      )}

      {fase.tipo === 'confirmando' && (
        <ConfirmarEnvio
          emBranco={emBranco}
          aoCancelar={() => setFase({ tipo: 'respondendo' })}
          aoConfirmar={() => void enviar(false)}
        />
      )}
    </main>
  )
}
