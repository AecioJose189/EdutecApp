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

/** Logo horizontal do Lumini (versão branca no tema escuro, como pede o Design System). */
export function Marca() {
  return (
    <span className="marca">
      <picture>
        <source srcSet="/lumini-horizontal-branco.svg" media="(prefers-color-scheme: dark)" />
        <img src="/lumini-horizontal-primaria.svg" alt="Lumini" width={128} height={47} />
      </picture>
    </span>
  )
}
