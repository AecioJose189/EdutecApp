import { Link, useParams } from 'react-router-dom'
import { chamarAlunoApi } from '../api/alunoApi'
import { AvisoErro } from '../componentes/AvisoErro'
import { Carregando } from '../componentes/Carregando'
import { Marca } from '../componentes/Topo'
import { useChamada } from '../componentes/useChamada'

// Uma chamada por código por carregamento da página: sem isso, a segunda execução do efeito (StrictMode)
// responderia "já era membro" e a tela mentiria. Falhas saem do mapa para "Tentar de novo" funcionar.
const aceites = new Map<string, ReturnType<typeof aceitar>>()
function aceitar(codigo: string) {
  return chamarAlunoApi('aceitar_convite', { codigo })
}
function aceitarUmaVez(codigo: string) {
  let p = aceites.get(codigo)
  if (!p) {
    p = aceitar(codigo)
    p.catch(() => aceites.delete(codigo))
    aceites.set(codigo, p)
  }
  return p
}

export function Convite() {
  const { codigo = '' } = useParams()
  const { estado, repetir } = useChamada(() => aceitarUmaVez(codigo.toUpperCase()), [codigo])

  return (
    <main className="pagina">
      <header className="topo">
        <Marca />
      </header>

      {estado.tipo === 'carregando' && <Carregando texto="Entrando na turma…" />}
      {estado.tipo === 'erro' && (
        <AvisoErro erro={estado.erro} aoRepetir={repetir}>
          <Link to="/" className="botao secundario-btn">
            Ir para minhas turmas
          </Link>
        </AvisoErro>
      )}
      {estado.tipo === 'ok' && (
        <>
          <div className="cartao aviso-ok" role="status">
            <span className="selo-simbolo estado-consolidado grande" aria-hidden="true">
              ✓
            </span>
            <h1>{estado.dados.ja_era_membro ? 'Você já participava desta turma' : 'Você entrou na turma!'}</h1>
          </div>
          <div className="cartao">
            <span className="etiqueta">{estado.dados.turma.codigo}</span>
            <h2>{estado.dados.turma.nome}</h2>
            {estado.dados.turma.professor_nome && (
              <p className="secundario">Prof. {estado.dados.turma.professor_nome}</p>
            )}
            <p className="corpo">
              Na aula, aponte a câmera para o QR code que o professor projetar para responder às atividades.
            </p>
          </div>
          <Link to="/" className="botao largo">
            Ir para minhas turmas
          </Link>
        </>
      )}
    </main>
  )
}
