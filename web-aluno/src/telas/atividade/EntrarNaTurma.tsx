import { Link } from 'react-router-dom'
import type { Turma } from '../../api/tipos'

/** QR lido por quem ainda não é aluno da turma: em vez do erro, oferece entrar e já começar. */
export function EntrarNaTurma({ turma, aoEntrar }: { turma: Turma; aoEntrar: () => void }) {
  return (
    <>
      <section className="cartao entrar-turma" aria-labelledby="titulo-entrar-turma">
        {turma.codigo && <span className="etiqueta">{turma.codigo}</span>}
        <h1 id="titulo-entrar-turma">Você ainda não está na turma {turma.nome}.</h1>
        <p className="corpo">
          Ao entrar, você passa a ser aluno desta turma e a atividade abre em seguida. Suas respostas atualizam o seu
          mapa do conhecimento.
        </p>
        <button className="botao largo" onClick={aoEntrar} autoFocus>
          Entrar na turma e começar
        </button>
      </section>
      <Link to="/" className="botao largo secundario-btn">
        Agora não
      </Link>
    </>
  )
}
