import { useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { chamarAlunoApi } from '../../api/alunoApi'
import type { ConceitoAluno, EstadoConceito, Turma } from '../../api/tipos'
import { AvisoErro } from '../../componentes/AvisoErro'
import { Carregando } from '../../componentes/Carregando'
import { estados, SeloEstado } from '../../componentes/SeloEstado'
import { Topo } from '../../componentes/Topo'
import { useChamada } from '../../componentes/useChamada'
import { GrafoSvg } from './GrafoSvg'
import { formatarDominio, PainelConceito } from './PainelConceito'

const ORDEM_ESTADOS: EstadoConceito[] = ['consolidado', 'em_desenvolvimento', 'revisar', 'sem_dados']
/** Na lista, primeiro o que pede atenção. */
const PRIORIDADE: EstadoConceito[] = ['revisar', 'em_desenvolvimento', 'sem_dados', 'consolidado']

export function MeuGrafo() {
  const { turmaId = '' } = useParams()
  const turma = (useLocation().state as { turma?: Turma } | null)?.turma
  const { estado, repetir } = useChamada(() => chamarAlunoApi('meu_grafo', { turma_id: turmaId }), [turmaId])
  const [selecionado, setSelecionado] = useState<string | null>(null)

  function selecionar(nome: string) {
    setSelecionado(nome)
    // No celular o painel fica abaixo do grafo: rola até ele.
    requestAnimationFrame(() =>
      document.querySelector('.painel-conceito')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
    )
  }

  return (
    <main className="pagina grafo-pagina">
      <Topo titulo="Meu grafo" voltarPara="/" />
      {turma && (
        <p className="secundario">
          {turma.nome} · {turma.codigo}
        </p>
      )}

      {estado.tipo === 'carregando' && <Carregando texto="Montando o seu grafo…" />}
      {estado.tipo === 'erro' && <AvisoErro erro={estado.erro} aoRepetir={repetir} />}
      {estado.tipo === 'ok' &&
        (estado.dados.conceitos.length === 0 ? (
          <div className="vazio">
            <p>
              <strong>Ainda não há conceitos nesta turma.</strong>
            </p>
            <p>Eles aparecem quando o professor publicar a primeira atividade.</p>
          </div>
        ) : (
          <Conteudo
            conceitos={estado.dados.conceitos}
            selecionado={selecionado}
            aoSelecionar={selecionar}
            aoFechar={() => setSelecionado(null)}
          />
        ))}
    </main>
  )
}

function Conteudo({
  conceitos,
  selecionado,
  aoSelecionar,
  aoFechar,
}: {
  conceitos: ConceitoAluno[]
  selecionado: string | null
  aoSelecionar: (nome: string) => void
  aoFechar: () => void
}) {
  const atual = conceitos.find((c) => c.nome === selecionado) ?? null
  const contagem = ORDEM_ESTADOS.map((e) => [e, conceitos.filter((c) => c.estado === e).length] as const)

  return (
    <>
      <div className="legenda" aria-label="Legenda">
        {contagem.map(([e, n]) => (
          <span key={e} className="legenda-item">
            <SeloEstado estado={e} />
            <span className="secundario">{n}</span>
          </span>
        ))}
      </div>

      <div className="cartao grafo-cartao">
        <GrafoSvg conceitos={conceitos} selecionado={selecionado} aoSelecionar={aoSelecionar} />
        <p className="secundario pequeno grafo-dica">
          Arraste para ver o grafo todo. Toque num conceito para ver os detalhes. As setas vão do pré-requisito para o
          conceito que depende dele.
        </p>
      </div>

      {atual && <PainelConceito conceito={atual} todos={conceitos} aoSelecionar={aoSelecionar} aoFechar={aoFechar} />}

      <section className="cartao">
        <h2>Todos os conceitos</h2>
        <ul className="ligados">
          {[...conceitos]
            .sort((a, b) => PRIORIDADE.indexOf(a.estado) - PRIORIDADE.indexOf(b.estado) || a.nome.localeCompare(b.nome))
            .map((c) => (
              <li key={c.nome}>
                <button
                  className={`ligado${c.nome === selecionado ? ' ativo' : ''}`}
                  onClick={() => aoSelecionar(c.nome)}
                >
                  <SeloEstado estado={c.estado} comRotulo={false} />
                  <span>{c.nome}</span>
                  <span className="secundario" title={estados[c.estado].rotulo}>
                    {formatarDominio(c.dominio)}
                  </span>
                </button>
              </li>
            ))}
        </ul>
        <p className="secundario pequeno">
          A porcentagem é o domínio estimado: a chance estimada de você saber o conceito, calculada a partir das suas
          respostas. Não é nota.
        </p>
      </section>
    </>
  )
}
