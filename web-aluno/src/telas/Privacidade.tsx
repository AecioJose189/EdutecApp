import { Link } from 'react-router-dom'
import { Marca } from '../componentes/Topo'

// Pública (o Google exige o link na tela de consentimento do OAuth). Mudou o que o sistema guarda? Atualize aqui.
export function Privacidade() {
  return (
    <main className="pagina privacidade">
      <header className="topo">
        <Marca />
      </header>
      <h1>Política de privacidade</h1>
      <p className="secundario">Última atualização: 27 de outubro de 2026</p>

      <p>
        O Lumini é um projeto acadêmico de apoio ao ensino. O professor publica atividades, os alunos respondem pelo
        celular e o sistema estima o domínio de cada conceito. Esta página explica quais dados usamos e por quê.
      </p>

      <h2>O que guardamos</h2>
      <ul>
        <li>
          <strong>Da sua conta Google:</strong> nome, e-mail e foto, só para identificar você no login. Não temos acesso
          à sua senha nem a outros dados da conta.
        </li>
        <li>
          <strong>Das turmas:</strong> de quais turmas você participa.
        </li>
        <li>
          <strong>Das atividades:</strong> suas respostas, o horário de envio e o domínio estimado de cada conceito.
        </li>
      </ul>

      <h2>Quem vê</h2>
      <p>
        Só o professor da turma vê suas respostas e seu domínio estimado. Os outros alunos não veem nada seu. O domínio
        estimado é um apoio ao estudo, não é nota.
      </p>

      <h2>O que não fazemos</h2>
      <ul>
        <li>Não vendemos nem compartilhamos seus dados com terceiros para publicidade.</li>
        <li>
          Não enviamos seu nome, e-mail ou respostas para a inteligência artificial: ela recebe apenas o material da
          aula, para gerar as questões.
        </li>
        <li>Não usamos rastreadores de anúncios.</li>
      </ul>

      <h2>Onde ficam os dados</h2>
      <p>
        Os dados ficam no Supabase (banco de dados e login) e o site é servido pela Vercel. Enquanto você responde, as
        respostas ficam também no seu aparelho, para não se perderem se a página recarregar, e são apagadas de lá depois
        do envio.
      </p>

      <h2>Seus direitos</h2>
      <p>
        Você pode pedir para ver, corrigir ou apagar seus dados, conforme a LGPD. Fale com o professor da sua turma, que
        encaminha o pedido à equipe do projeto.
      </p>

      <Link to="/" className="botao largo">
        Voltar ao início
      </Link>
    </main>
  )
}
