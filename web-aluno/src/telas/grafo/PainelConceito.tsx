import type { ConceitoAluno } from '../../api/tipos'
import { SeloEstado } from '../../componentes/SeloEstado'

export function formatarDominio(d: number | null): string {
  return d === null ? '—' : `${Math.round(d * 100)}%`
}

/** Abaixo disto o domínio estimado ainda muda muito a cada resposta: o conceito leva o selo "poucos dados". */
export const MINIMO_RESPOSTAS = 5

/** "baseado em N respostas" e, com menos de 5, o selo "poucos dados". */
export function BaseRespostas({ n }: { n: number }) {
  if (n === 0) return <span className="secundario pequeno">Ainda sem respostas</span>
  return (
    <span className="base-respostas pequeno">
      <span className="secundario">baseado em {n === 1 ? '1 resposta' : `${n} respostas`}</span>
      {n < MINIMO_RESPOSTAS && (
        <span className="selo-poucos-dados">
          poucos dados<span className="so-leitor">: a estimativa ainda pode mudar bastante</span>
        </span>
      )}
    </span>
  )
}

interface Props {
  conceito: ConceitoAluno
  todos: ConceitoAluno[]
  aoSelecionar: (nome: string) => void
  aoFechar: () => void
}

export function PainelConceito({ conceito, todos, aoSelecionar, aoFechar }: Props) {
  const porNome = new Map(todos.map((c) => [c.nome, c]))
  const pres = conceito.prerequisitos.map((n) => porNome.get(n)).filter((c): c is ConceitoAluno => !!c)
  const dependentes = todos.filter((c) => c.prerequisitos.includes(conceito.nome))

  return (
    <section className="cartao painel-conceito" aria-live="polite">
      <div className="painel-cabecalho">
        <h2>{conceito.nome}</h2>
        <button className="botao-icone" onClick={aoFechar} aria-label="Fechar detalhes">
          ×
        </button>
      </div>
      <SeloEstado estado={conceito.estado} />

      {conceito.dominio === null ? (
        <p className="corpo">Ainda não há respostas suficientes para estimar o seu domínio deste conceito.</p>
      ) : (
        <div>
          <p className="dominio-numero">
            {formatarDominio(conceito.dominio)} <span className="secundario">de domínio estimado</span>
          </p>
          <div className="barra" aria-hidden="true">
            <div
              className={`barra-cheia estado-${conceito.estado}`}
              style={{ width: `${Math.round(conceito.dominio * 100)}%` }}
            />
          </div>
          <p className="secundario pequeno">Chance estimada de você saber o conceito; não é nota.</p>
        </div>
      )}
      <BaseRespostas n={conceito.n_respostas} />

      {pres.length > 0 && <ListaLigados titulo="Depende de" conceitos={pres} aoSelecionar={aoSelecionar} />}
      {dependentes.length > 0 && (
        <ListaLigados titulo="É base para" conceitos={dependentes} aoSelecionar={aoSelecionar} />
      )}
    </section>
  )
}

function ListaLigados({
  titulo,
  conceitos,
  aoSelecionar,
}: {
  titulo: string
  conceitos: ConceitoAluno[]
  aoSelecionar: (nome: string) => void
}) {
  return (
    <div>
      <h3 className="subtitulo">{titulo}</h3>
      <ul className="ligados">
        {conceitos.map((c) => (
          <li key={c.nome}>
            <button className="ligado" onClick={() => aoSelecionar(c.nome)}>
              <SeloEstado estado={c.estado} comRotulo={false} />
              <span className="ligado-texto">
                {c.nome}
                <BaseRespostas n={c.n_respostas} />
              </span>
              <span className="ligado-dominio">{formatarDominio(c.dominio)}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
