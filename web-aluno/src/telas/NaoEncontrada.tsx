import { Link } from 'react-router-dom'
import { Marca } from '../componentes/Topo'

export function NaoEncontrada() {
  return (
    <main className="pagina">
      <header className="topo">
        <Marca />
      </header>
      <div className="vazio">
        <p>
          <strong>Página não encontrada.</strong>
        </p>
        <p>Confira o link ou volte para o início.</p>
      </div>
      <Link to="/" className="botao largo">
        Ir para minhas turmas
      </Link>
    </main>
  )
}
