import { Link } from 'react-router-dom'
import type { ResultadoEnvio } from '../../api/tipos'

function Proximos({ turmaId }: { turmaId: string }) {
  return (
    <>
      <Link to={`/turmas/${turmaId}/grafo`} className="botao largo">
        Ver meu grafo
      </Link>
      <Link to="/" className="botao largo secundario-btn">
        Minhas turmas
      </Link>
    </>
  )
}

export function Resultado({
  resultado,
  turmaId,
  automatico,
}: {
  resultado: ResultadoEnvio
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
          Suas respostas atualizam o seu domínio estimado de cada conceito. Veja como ficou no seu grafo.
        </p>
      </section>
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
        <p className="corpo">Suas respostas foram registradas. Não é preciso enviar de novo.</p>
      </section>
      <Proximos turmaId={turmaId} />
    </>
  )
}
