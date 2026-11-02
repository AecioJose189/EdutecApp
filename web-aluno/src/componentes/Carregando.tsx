export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return (
    <div className="carregando" role="status" aria-live="polite">
      <div className="girando" aria-hidden="true" />
      <p>{texto}</p>
    </div>
  )
}
