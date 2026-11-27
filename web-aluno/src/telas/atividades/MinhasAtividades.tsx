import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { chamarAlunoApi } from '../../api/alunoApi'
import type { AtividadeFeita, Turma } from '../../api/tipos'
import { AvisoErro } from '../../componentes/AvisoErro'
import { Carregando } from '../../componentes/Carregando'
import { Topo } from '../../componentes/Topo'
import { useChamada } from '../../componentes/useChamada'
import { formatarEnvio, textoAcertos } from './formatar'

/** Selo do gabarito: liberado depois que o professor encerra a sessão em sala. */
export function SeloGabarito({ liberado }: { liberado: boolean }) {
  return liberado ? (
    <span className="selo-gabarito liberado">
      <span aria-hidden="true">✓</span> Gabarito liberado
    </span>
  ) : (
    <span className="selo-gabarito aguardando">Gabarito quando a sessão acabar</span>
  )
}

function CartaoAtividade({ a, mostrarTurma }: { a: AtividadeFeita; mostrarTurma: boolean }) {
  const acertos = textoAcertos(a.acertos, a.total_multipla_escolha)
  return (
    <Link to={`/atividades/${a.tentativa_id}`} className="cartao cartao-atividade">
      <span className="rotulo">
        Atividade {a.atividade.numero}
        {mostrarTurma && ` · ${a.turma.nome}`}
      </span>
      <h2>{a.atividade.titulo}</h2>
      <p className="secundario pequeno">
        {a.turma.codigo} · enviada em {formatarEnvio(a.enviada_em)}
      </p>
      <div className="linha-meta">
        {acertos && <span className="placar">{acertos}</span>}
        <SeloGabarito liberado={a.gabarito_liberado} />
      </div>
      <span className="cartao-acao">{a.gabarito_liberado ? 'Ver a correção ›' : 'Ver minhas respostas ›'}</span>
    </Link>
  )
}

/** Atividades já enviadas, da mais recente para a mais antiga (`?turma=<id>` filtra uma turma). */
export function MinhasAtividades() {
  const [busca] = useSearchParams()
  const turmaId = busca.get('turma') ?? undefined
  const turma = (useLocation().state as { turma?: Turma } | null)?.turma
  const { estado, repetir } = useChamada(
    () => chamarAlunoApi('minhas_atividades', turmaId ? { turma_id: turmaId } : {}),
    [turmaId],
  )

  return (
    <main className="pagina">
      <Topo titulo="Minhas atividades" voltarPara={turmaId ? `/turmas/${turmaId}/grafo` : '/'} />
      {turmaId && (
        <p className="secundario">
          {turma ? `${turma.nome} · ${turma.codigo}` : 'Uma turma'} ·{' '}
          <Link to="/atividades" className="link-toque">
            ver de todas as turmas
          </Link>
        </p>
      )}

      {estado.tipo === 'carregando' && <Carregando texto="Buscando suas atividades…" />}
      {estado.tipo === 'erro' && <AvisoErro erro={estado.erro} aoRepetir={repetir} />}
      {estado.tipo === 'ok' &&
        (estado.dados.atividades.length === 0 ? (
          <div className="vazio">
            <p>
              <strong>Nenhuma atividade enviada ainda.</strong>
            </p>
            <p>Quando você responder uma atividade pelo QR code da aula, ela aparece aqui.</p>
          </div>
        ) : (
          <ul className="lista">
            {estado.dados.atividades.map((a) => (
              <li key={a.tentativa_id}>
                <CartaoAtividade a={a} mostrarTurma={!turmaId} />
              </li>
            ))}
          </ul>
        ))}
    </main>
  )
}
