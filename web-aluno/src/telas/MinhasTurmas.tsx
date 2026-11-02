import { Link } from 'react-router-dom'
import { chamarAlunoApi } from '../api/alunoApi'
import { AvisoErro } from '../componentes/AvisoErro'
import { Carregando } from '../componentes/Carregando'
import { Marca } from '../componentes/Topo'
import { useChamada } from '../componentes/useChamada'
import { useSessao } from '../sessao/SessaoProvider'

export function MinhasTurmas() {
  const { usuario, sair } = useSessao()
  const { estado, repetir } = useChamada(() => chamarAlunoApi('minhas_turmas', {}), [])

  return (
    <main className="pagina">
      <header className="topo">
        <Marca />
        <span style={{ flex: 1 }} />
        <button className="botao-texto" onClick={sair}>
          Sair
        </button>
      </header>

      <div>
        <h1>Minhas turmas</h1>
        {usuario && <p className="secundario">Olá, {usuario.nome.split(' ')[0]}!</p>}
      </div>

      {estado.tipo === 'carregando' && <Carregando texto="Buscando suas turmas…" />}
      {estado.tipo === 'erro' && <AvisoErro erro={estado.erro} aoRepetir={repetir} />}
      {estado.tipo === 'ok' &&
        (estado.dados.turmas.length === 0 ? (
          <div className="vazio">
            <p>
              <strong>Você ainda não está em nenhuma turma.</strong>
            </p>
            <p>Peça ao professor o link de convite da turma.</p>
          </div>
        ) : (
          <ul className="lista">
            {estado.dados.turmas.map((t) => (
              <li key={t.id}>
                <Link to={`/turmas/${t.id}/grafo`} state={{ turma: t }} className="cartao cartao-turma">
                  <span className="etiqueta">{t.codigo}</span>
                  <h2>{t.nome}</h2>
                  {t.professor_nome && <p className="secundario">Prof. {t.professor_nome}</p>}
                  <span className="cartao-acao">Ver meu grafo ›</span>
                </Link>
              </li>
            ))}
          </ul>
        ))}

      <div className="cartao dica">
        <p>
          <strong>Na aula:</strong> quando o professor projetar o QR code, aponte a câmera do celular para ele.
        </p>
      </div>
    </main>
  )
}
