// Acertou / errou / aguardando correção: sempre com símbolo e palavra, nunca só a cor.
// Errar não é alarme (Design System, "calma, não alarme"): contorno neutro, sem a cor de erro.
const marcas = {
  acertou: { simbolo: '✓', rotulo: 'Acertou' },
  errou: { simbolo: '✗', rotulo: 'Errou' },
  aguarda: { simbolo: '…', rotulo: 'Aguarda correção' },
} as const

export function tipoMarca(acertou: boolean | null): keyof typeof marcas {
  return acertou === null ? 'aguarda' : acertou ? 'acertou' : 'errou'
}

export function MarcaAcerto({ acertou, comRotulo = true }: { acertou: boolean | null; comRotulo?: boolean }) {
  const tipo = tipoMarca(acertou)
  const m = marcas[tipo]
  return (
    <span className={`marca-acerto ${tipo}`}>
      <span className="marca-acerto-simbolo" aria-hidden="true">
        {m.simbolo}
      </span>
      {comRotulo ? m.rotulo : <span className="so-leitor">{m.rotulo}</span>}
    </span>
  )
}
