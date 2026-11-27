import { Link } from 'react-router-dom'
import type { Questao, ResultadoEnvio } from '../../api/tipos'
import { MarcaAcerto } from '../../componentes/MarcaAcerto'
import { TextoLatex } from '../../componentes/TextoLatex'

function Proximos({ turmaId }: { turmaId: string }) {
  return (
    <>
      <Link to={`/turmas/${turmaId}/grafo`} className="botao largo">
        Ver meu mapa do conhecimento
      </Link>
      <Link to="/atividades" className="botao largo secundario-btn">
        Minhas atividades
      </Link>
      <Link to="/" className="botao largo secundario-btn">
        Minhas turmas
      </Link>
    </>
  )
}

/**
 * Logo após enviar: quais questões acertou e quais errou, SEM a alternativa correta nem o feedback
 * (colegas na sala ainda podem estar respondendo). O gabarito aparece na revisão, depois que o professor
 * encerrar a sessão.
 */
function QuestoesCorrigidas({
  corrigidas,
  questoes,
  tentativaId,
}: {
  corrigidas: NonNullable<ResultadoEnvio['questoes']>
  questoes: Questao[]
  tentativaId: string
}) {
  const erradas = corrigidas.filter((c) => c.acertou === false).length
  return (
    <section className="cartao">
      <h2>
        {erradas === 0
          ? 'Questão por questão'
          : erradas === 1
            ? 'Você errou 1 questão'
            : `Você errou ${erradas} questões`}
      </h2>
      <ol className="revisao">
        {corrigidas.map((c) => {
          const i = questoes.findIndex((q) => q.id === c.questao_id)
          const q = questoes[i]
          return (
            <li key={c.questao_id} className={`corrigida${c.acertou === false ? ' errada' : ''}`}>
              <span className="revisao-texto">
                <strong>Questão {i >= 0 ? i + 1 : c.questao_id}</strong>
                {q && (
                  <span className="secundario recorte">
                    <TextoLatex texto={q.enunciado} />
                  </span>
                )}
              </span>
              <MarcaAcerto acertou={c.acertou} />
            </li>
          )
        })}
      </ol>
      <p className="secundario pequeno">
        A resposta certa e a explicação de cada alternativa aparecem quando o professor encerrar a sessão em sala,
        porque colegas ainda podem estar respondendo.
      </p>
      <Link to={`/atividades/${tentativaId}`} className="cartao-acao link-toque">
        Ver minhas respostas ›
      </Link>
    </section>
  )
}

export function Resultado({
  resultado,
  questoes,
  tentativaId,
  turmaId,
  automatico,
}: {
  resultado: ResultadoEnvio
  questoes: Questao[]
  tentativaId: string
  turmaId: string
  automatico?: boolean
}) {
  const { acertos, total_multipla_escolha: total, abertas_aguardando_correcao: abertas } = resultado
  return (
    <>
      <section className="cartao resultado" role="status">
        <span className="selo-simbolo estado-consolidado grande" aria-hidden="true">
          ✓
        </span>
        <p className="secundario">
          {automatico ? 'O tempo acabou e suas respostas foram enviadas.' : 'Respostas enviadas!'}
        </p>
        {total > 0 && (
          <h1>
            Você acertou {acertos} de {total}
          </h1>
        )}
        {abertas > 0 && (
          <p className="corpo">
            {abertas === 1 ? '1 questão aberta aguarda correção.' : `${abertas} questões abertas aguardam correção.`}
          </p>
        )}
        <p className="secundario">
          Suas respostas atualizam o seu domínio estimado de cada conceito. Veja como ficou no seu mapa do conhecimento.
        </p>
      </section>
      {resultado.questoes && resultado.questoes.length > 0 && (
        <QuestoesCorrigidas corrigidas={resultado.questoes} questoes={questoes} tentativaId={tentativaId} />
      )}
      <Proximos turmaId={turmaId} />
    </>
  )
}

export function JaEnviada({ turmaId }: { turmaId: string }) {
  return (
    <>
      <section className="cartao resultado" role="status">
        <span className="selo-simbolo estado-consolidado grande" aria-hidden="true">
          ✓
        </span>
        <h1>Você já enviou esta atividade</h1>
        <p className="corpo">
          Suas respostas foram registradas. Não é preciso enviar de novo. Em “Minhas atividades” você vê quantas
          acertou.
        </p>
      </section>
      <Proximos turmaId={turmaId} />
    </>
  )
}
