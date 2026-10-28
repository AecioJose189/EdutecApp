import type { ReactNode } from 'react'
import { explicarErro } from '../api/erros'

/** Erro em linguagem simples: título, mensagem do servidor e o que fazer. */
export function AvisoErro({
  erro,
  aoRepetir,
  children,
}: {
  erro: unknown
  aoRepetir?: () => void
  children?: ReactNode
}) {
  const e = explicarErro(erro)
  return (
    <div className="cartao aviso-erro" role="alert">
      <h2>
        <span aria-hidden="true">! </span>
        {e.titulo}
      </h2>
      {e.mensagem && <p>{e.mensagem}</p>}
      <p>
        <strong>{e.dica}</strong>
      </p>
      {e.repetir && aoRepetir && (
        <button className="botao" onClick={aoRepetir}>
          Tentar de novo
        </button>
      )}
      {children}
    </div>
  )
}
