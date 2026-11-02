import type { EstadoConceito } from '../api/tipos'

export const estados: Record<EstadoConceito, { simbolo: string; rotulo: string; cor: string }> = {
  consolidado: { simbolo: '✓', rotulo: 'Consolidado', cor: '#1F5BA8' },
  em_desenvolvimento: { simbolo: '~', rotulo: 'Em desenvolvimento', cor: '#F2C14E' },
  revisar: { simbolo: '!', rotulo: 'Revisar', cor: '#D0531F' },
  sem_dados: { simbolo: '?', rotulo: 'Sem dados', cor: '#9A968E' },
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
