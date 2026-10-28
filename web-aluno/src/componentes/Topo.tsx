import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** Cabeçalho das telas: botão voltar (opcional), título e ação à direita. */
export function Topo({ titulo, voltarPara, direita }: { titulo: ReactNode; voltarPara?: string; direita?: ReactNode }) {
  return (
    <header className="topo">
      {voltarPara && (
        <Link to={voltarPara} className="botao-icone" aria-label="Voltar">
          ‹
        </Link>
      )}
      <h1>{titulo}</h1>
      {direita}
    </header>
  )
}

export function Marca() {
  return (
    <span className="marca">
      <span className="marca-icone" aria-hidden="true" />
      Lumini
    </span>
  )
}
