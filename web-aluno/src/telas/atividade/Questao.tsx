import type { Questao as TQuestao } from '../../api/tipos'
import { TextoLatex } from '../../componentes/TextoLatex'
import type { RespostaLocal } from './armazenamento'

interface Props {
  questao: TQuestao
  numero: number
  resposta: RespostaLocal | undefined
  aoResponder: (r: RespostaLocal) => void
  bloqueada: boolean
}

export function Questao({ questao, numero, resposta, aoResponder, bloqueada }: Props) {
  const escolhida = resposta && 'alternativa_id' in resposta ? resposta.alternativa_id : null
  const texto = resposta && 'texto' in resposta ? resposta.texto : ''
  const idEnunciado = `enunciado-${questao.id}`

  return (
    <section className="cartao questao">
      {questao.conceitos.length > 0 && (
        <div className="chips">
          {questao.conceitos.map((c) => (
            <span key={c} className="chip">
              {c}
            </span>
          ))}
        </div>
      )}
      <p id={idEnunciado} className="enunciado">
        <span className="so-leitor">Questão {numero}. </span>
        <TextoLatex texto={questao.enunciado} />
      </p>

      {questao.tipo === 'multipla_escolha' && questao.alternativas ? (
        <div role="radiogroup" aria-labelledby={idEnunciado} className="alternativas">
          {questao.alternativas.map((a, i) => {
            const marcada = escolhida === a.id
            return (
              <button
                key={a.id}
                type="button"
                role="radio"
                aria-checked={marcada}
                className={`alternativa${marcada ? ' marcada' : ''}`}
                disabled={bloqueada}
                onClick={() => aoResponder({ alternativa_id: a.id })}
              >
                <span className="alternativa-letra" aria-hidden="true">
                  {marcada ? '✓' : String.fromCharCode(65 + i)}
                </span>
                <span>
                  <TextoLatex texto={a.texto} />
                </span>
              </button>
            )
          })}
        </div>
      ) : (
        <label className="campo-aberta">
          <span className="secundario">Sua resposta</span>
          <textarea
            value={texto}
            rows={6}
            disabled={bloqueada}
            placeholder="Escreva aqui com suas palavras…"
            onChange={(e) => aoResponder({ texto: e.target.value })}
          />
        </label>
      )}
    </section>
  )
}
