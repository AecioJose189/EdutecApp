import { useCallback, useEffect, useState } from 'react'

export type EstadoChamada<T> = { tipo: 'carregando' } | { tipo: 'ok'; dados: T } | { tipo: 'erro'; erro: unknown }

/** Roda `chamar` ao montar (e quando `deps` mudam) e expõe o estado + `repetir`. */
export function useChamada<T>(chamar: () => Promise<T>, deps: unknown[]) {
  const [estado, setEstado] = useState<EstadoChamada<T>>({ tipo: 'carregando' })
  const [vez, setVez] = useState(0)

  useEffect(() => {
    let ativo = true
    setEstado({ tipo: 'carregando' })
    chamar().then(
      (dados) => ativo && setEstado({ tipo: 'ok', dados }),
      (erro) => ativo && setEstado({ tipo: 'erro', erro }),
    )
    return () => {
      ativo = false
    }
  }, [...deps, vez])

  const repetir = useCallback(() => setVez((v) => v + 1), [])
  return { estado, repetir }
}
