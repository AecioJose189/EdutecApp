import { Link, useParams } from 'react-router-dom'
import { chamarAlunoApi } from '../../api/alunoApi'
import { codigoErro } from '../../api/erros'
import type {
  QuestaoRevisao,
  QuestaoRevisaoAberta,
  QuestaoRevisaoMultipla,
  RevisaoTentativa as TRevisao,
} from '../../api/tipos'
import { AvisoErro } from '../../componentes/AvisoErro'
import { Carregando } from '../../componentes/Carregando'
import { MarcaAcerto } from '../../componentes/MarcaAcerto'
import { TextoLatex } from '../../componentes/TextoLatex'
import { Topo } from '../../componentes/Topo'
import { useChamada } from '../../componentes/useChamada'
import { letra } from './formatar'
import { SeloGabarito } from './MinhasAtividades'

/**
 * Revisão de uma tentativa enviada (`revisao_tentativa`). Sem o gabarito liberado (sessão ainda aberta),
 * mostra só a resposta do aluno e se acertou; com ele, a alternativa correta, o feedback de cada
 * alternativa e a rubrica das abertas. Os campos do gabarito simplesmente não vêm quando não liberado.
 */
export function RevisaoTentativa() {
  const { tentativaId = '' } = useParams()
  const { estado, repetir } = useChamada(
    () => chamarAlunoApi('revisao_tentativa', { tentativa_id: tentativaId }),
    [tentativaId],
  )

  return (
    <main className="pagina">
      <Topo titulo="Minhas respostas" voltarPara="/atividades" />
      {estado.tipo === 'carregando' && <Carregando texto="Buscando suas respostas…" />}
      {estado.tipo === 'erro' && codigoErro(estado.erro) === 'nao_matriculado' && (
        // Aqui o código quer dizer "tentativa que não é sua, não enviada ou inexistente", não "fora da turma".
        <div className="cartao aviso-erro" role="alert">
          <h2>
            <span aria-hidden="true">! </span>Atividade não encontrada
          </h2>
          <p>Estas respostas não são da sua conta ou ainda não foram enviadas.</p>
          <Link to="/atividades" className="botao secundario-btn">
            Minhas atividades
          </Link>
        </div>
      )}
      {estado.tipo === 'erro' && codigoErro(estado.erro) !== 'nao_matriculado' && (
        <AvisoErro erro={estado.erro} aoRepetir={repetir}>
          <Link to="/atividades" className="botao secundario-btn">
            Minhas atividades
          </Link>
        </AvisoErro>
      )}
      {estado.tipo === 'ok' && <Conteudo revisao={estado.dados} />}
    </main>
  )
}

function Conteudo({ revisao }: { revisao: TRevisao }) {
  const { atividade, gabarito_liberado: liberado, acertos, total_multipla_escolha: total } = revisao
  return (
    <>
      <section className="cartao">
        <span className="rotulo">Atividade {atividade.numero}</span>
        <h1>{atividade.titulo}</h1>
        <div className="linha-meta">
          {total > 0 && (
            <span className="placar">
              Você acertou {acertos} de {total}
            </span>
          )}
          <SeloGabarito liberado={liberado} />
        </div>
      </section>

      <div className="cartao aviso-info">
        {liberado ? (
          <p>
            O professor encerrou a sessão: agora você vê a <strong>resposta certa</strong> e a explicação de cada
            alternativa.
          </p>
        ) : (
          <p>
            A resposta certa e as explicações aparecem quando o professor <strong>encerrar a sessão em sala</strong>,
            porque colegas ainda podem estar respondendo. Por enquanto, você vê a sua resposta e se acertou.
          </p>
        )}
      </div>

      <ol className="lista">
        {revisao.questoes.map((q, i) => (
          <li key={q.questao_id}>
            <CartaoQuestao questao={q} numero={i + 1} liberado={liberado} />
          </li>
        ))}
      </ol>

      <Link to="/atividades" className="botao largo secundario-btn">
        Minhas atividades
      </Link>
    </>
  )
}

function CartaoQuestao({ questao, numero, liberado }: { questao: QuestaoRevisao; numero: number; liberado: boolean }) {
  return (
    <section className="cartao questao" aria-labelledby={`q-${questao.questao_id}`}>
      <div className="questao-revisao-topo">
        <h2 id={`q-${questao.questao_id}`}>Questão {numero}</h2>
        <MarcaAcerto acertou={questao.acertou} />
      </div>
      {questao.conceitos.length > 0 && (
        <div className="chips">
          {questao.conceitos.map((c) => (
            <span key={c} className="chip">
              {c}
            </span>
          ))}
        </div>
      )}
      <p className="enunciado">
        <TextoLatex texto={questao.enunciado} />
      </p>
      {questao.tipo === 'multipla_escolha' ? (
        <Alternativas questao={questao} liberado={liberado} />
      ) : (
        <Aberta questao={questao} liberado={liberado} />
      )}
    </section>
  )
}

function Alternativas({ questao, liberado }: { questao: QuestaoRevisaoMultipla; liberado: boolean }) {
  const escolhida = 'alternativa_id' in questao.minha_resposta ? questao.minha_resposta.alternativa_id : null
  return (
    <>
      {escolhida === null && <p className="em-branco">Você deixou esta questão em branco.</p>}
      <ul className="alternativas lista-simples">
        {questao.alternativas.map((a, i) => {
          const minha = a.id === escolhida
          // Só existe com o gabarito liberado; sem ele, nada de "certa".
          const certa = liberado && a.correta === true
          return (
            <li
              key={a.id}
              className={`alternativa revisada${minha ? ' escolhida' : ''}${certa ? ' correta' : ''}`}
              aria-label={`Alternativa ${letra(i)}${minha ? ', sua resposta' : ''}${certa ? ', resposta certa' : ''}`}
            >
              <span className="alternativa-letra" aria-hidden="true">
                {letra(i)}
              </span>
              <span className="alternativa-corpo">
                <span>
                  <TextoLatex texto={a.texto} />
                </span>
                {(minha || certa) && (
                  <span className="alternativa-rotulos">
                    {minha && <span className="rotulo-alternativa">Sua resposta</span>}
                    {certa && (
                      <span className="rotulo-alternativa certa">
                        <span aria-hidden="true">✓ </span>Resposta certa
                      </span>
                    )}
                  </span>
                )}
                {liberado && a.feedback && (
                  <span className="feedback">
                    <TextoLatex texto={a.feedback} />
                  </span>
                )}
              </span>
            </li>
          )
        })}
      </ul>
    </>
  )
}

function Aberta({ questao, liberado }: { questao: QuestaoRevisaoAberta; liberado: boolean }) {
  const texto = 'texto' in questao.minha_resposta ? questao.minha_resposta.texto : ''
  return (
    <>
      <div className="campo-aberta">
        <span className="secundario">Sua resposta</span>
        {texto.trim() ? (
          <p className="resposta-aberta">{texto}</p>
        ) : (
          <p className="em-branco">Você deixou esta questão em branco.</p>
        )}
      </div>
      <p className="secundario pequeno">
        Questões abertas são corrigidas pelo professor e não entram no seu domínio estimado por enquanto.
      </p>
      {liberado && questao.rubrica && (
        <div className="rubrica">
          <p className="subtitulo">O que se esperava na resposta</p>
          <p className="corpo">
            <TextoLatex texto={questao.rubrica} />
          </p>
        </div>
      )}
    </>
  )
}
