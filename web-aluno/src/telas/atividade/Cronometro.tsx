import { useEffect, useState } from 'react'

function segundosAte(prazo: string | null): number | null {
  return prazo ? Math.max(0, Math.ceil((Date.parse(prazo) - Date.now()) / 1000)) : null
}

/** Segundos que faltam até `prazo` (ISO), atualizado a cada segundo; `null` sem prazo. */
export function useSegundosRestantes(prazo: string | null): number | null {
  const [restante, setRestante] = useState(() => segundosAte(prazo))
  useEffect(() => {
    setRestante(segundosAte(prazo))
    if (!prazo) return
    const id = setInterval(() => setRestante(segundosAte(prazo)), 1000)
    return () => clearInterval(id)
  }, [prazo])
  return restante
}

export function formatarTempo(segundos: number): string {
  const h = Math.floor(segundos / 3600)
  const m = Math.floor((segundos % 3600) / 60)
  const s = segundos % 60
  const mmss = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return h > 0 ? `${h}:${mmss}` : mmss
}

export function Cronometro({ restante }: { restante: number }) {
  const acabando = restante <= 60
  return (
    <div className={`cronometro${acabando ? ' acabando' : ''}`} role="timer" aria-live={acabando ? 'polite' : 'off'}>
      <span aria-hidden="true">⏱</span>
      <span>
        {acabando && <span aria-hidden="true">! </span>}
        Faltam <strong>{formatarTempo(restante)}</strong>
      </span>
    </div>
  )
}
