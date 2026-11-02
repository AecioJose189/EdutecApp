import type { Questao } from '../../api/tipos'
import { TextoLatex } from '../../componentes/TextoLatex'
import { respondida, type RespostasLocais } from './armazenamento'

interface Props {
  questoes: Questao[]
  respostas: RespostasLocais
  irPara: (indice: number) => void
  aoEnviar: () => void
  bloqueada: boolean
}

export function Revisao({ questoes, respostas, irPara, aoEnviar, bloqueada }: Props) {
  const feitas = questoes.filter((q) => respondida(respostas[q.id])).length
  return (
    <section className="cartao">
      <h2>Revise antes de enviar</h2>
      <p className="corpo">
        Você respondeu <strong>{feitas}</strong> de <strong>{questoes.length}</strong> questões. Toque numa questão para
        mudar a resposta.
      </p>
      <ol className="revisao">
        {questoes.map((q, i) => {
          const ok = respondida(respostas[q.id])
          return (
            <li key={q.id}>
              <button type="button" className="revisao-item" onClick={() => irPara(i)} disabled={bloqueada}>
                <span className={`revisao-marca${ok ? ' ok' : ''}`} aria-hidden="true">
                  {ok ? '✓' : '–'}
                </span>
                <span className="revisao-texto">
                  <strong>Questão {i + 1}</strong>
                  <span className="secundario recorte">
                    <TextoLatex texto={q.enunciado} />
                  </span>
                </span>
                <span className={ok ? 'secundario' : 'em-branco'}>{ok ? 'Respondida' : 'Em branco'}</span>
              </button>
            </li>
          )
        })}
      </ol>
      <button className="botao largo" onClick={aoEnviar} disabled={bloqueada}>
        Enviar respostas
      </button>
    </section>
  )
}

export function ConfirmarEnvio({
  emBranco,
  aoConfirmar,
  aoCancelar,
}: {
  emBranco: number
  aoConfirmar: () => void
  aoCancelar: () => void
}) {
  return (
    <div className="fundo-dialogo" onClick={aoCancelar}>
      <div
        className="cartao dialogo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-confirmar"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="titulo-confirmar">Enviar respostas?</h2>
        <p className="corpo">Depois de enviar, não dá para mudar as respostas.</p>
        {emBranco > 0 && (
          <p className="alerta-branco">
            <span aria-hidden="true">! </span>
            {emBranco === 1 ? '1 questão está em branco.' : `${emBranco} questões estão em branco.`}
          </p>
        )}
        <div className="dialogo-botoes">
          <button className="botao secundario-btn" onClick={aoCancelar} autoFocus>
            Voltar
          </button>
          <button className="botao" onClick={aoConfirmar}>
            Enviar
          </button>
        </div>
      </div>
    </div>
  )
}
