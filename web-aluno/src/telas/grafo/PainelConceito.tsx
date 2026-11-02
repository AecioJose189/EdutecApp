import type { ConceitoAluno } from '../../api/tipos'
import { SeloEstado } from '../../componentes/SeloEstado'

export function formatarDominio(d: number | null): string {
  return d === null ? '—' : `${Math.round(d * 100)}%`
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
  const respostas = conceito.n_respostas === 1 ? '1 resposta' : `${conceito.n_respostas} respostas`

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
      <p className="corpo">Com base em {respostas} suas.</p>

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
              <span>{c.nome}</span>
              <span className="secundario">{formatarDominio(c.dominio)}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
