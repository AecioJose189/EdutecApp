import type { EstadoConceito } from '../api/tipos'

// Cores: classes .estado-<estado> em estilos/global.css (tokens do tema).
export const estados: Record<EstadoConceito, { simbolo: string; rotulo: string }> = {
  consolidado: { simbolo: '✓', rotulo: 'Consolidado' },
  em_desenvolvimento: { simbolo: '~', rotulo: 'Em desenvolvimento' },
  revisar: { simbolo: '!', rotulo: 'Revisar' },
  sem_dados: { simbolo: '?', rotulo: 'Sem dados' },
}

/** Estado de aprendizagem sempre com símbolo além da cor. */
export function SeloEstado({ estado, comRotulo = true }: { estado: EstadoConceito; comRotulo?: boolean }) {
  const e = estados[estado]
  return (
    <span className="selo">
      <span className={`selo-simbolo estado-${estado}`} aria-hidden="true">
        {e.simbolo}
      </span>
      {comRotulo ? e.rotulo : <span className="so-leitor">{e.rotulo}</span>}
    </span>
  )
}
