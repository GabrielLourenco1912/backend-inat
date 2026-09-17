# Regra de aprendizagem do INAT

Esta política é específica do projeto e está centralizada em `project/ApprenticeshipPolicy.java`.

- O contrato continua armazenando `weeklyWorkloadMinutes` como inteiro. O domínio/API não é convertido para enum nem limitado a dois valores; o front oferece 20h (1200 minutos) e 30h (1800 minutos).
- A turma pode ter matrículas dos dois modelos. Não há restrição de carga horária na matrícula da turma.
- Uma aula online aceita somente participação com contrato **ativo de exatamente 1800 minutos**, vigente na data da aula (fuso da aplicação: `America/Sao_Paulo`). Aulas presenciais mantêm as regras anteriores.
- Ao criar uma aula online, sua lista regular é gerada na mesma transação. Matrículas de outras cargas são ignoradas, sem cancelar a matrícula nem impedir a criação da aula. A geração manual da lista aplica o mesmo filtro e continua idempotente.
- Inclusão, edição/reativação e remanejamento de participantes aplicam a mesma validação. Havendo matrícula de origem, vale o contrato dessa matrícula; reposições e extras sem origem exigem contrato elegível do próprio aprendiz.
- A mudança de presencial para online e a alteração da data/turma revalidam participantes não cancelados. Uma lista incompatível impede a alteração; ninguém é removido silenciosamente.
- Não é permitido alterar a carga de um contrato de forma que invalide suas participações esperadas em aulas online agendadas/em andamento. Primeiro é necessário cancelar/remover essas participações. Aulas encerradas e participações canceladas não bloqueiam a mudança.
- Ao atingir `lesson.endsAt`, o ciclo acadêmico registra falta para cada participante esperado de aula online que ainda não possui chamada. Uma entrega posterior de todas as atividades obrigatórias atualiza esse mesmo registro para presença, preservando sua identidade e impedindo duplicidade. Presenças lançadas antes do encerramento não são sobrescritas pelo scheduler.
- A empresa pode informar opcionalmente um dia mensal de fechamento de ponto. Para uma aula no dia do fechamento ou antes, todas as atividades precisam ter sido entregues até o fim desse dia no mesmo mês; para uma aula posterior, vale o fechamento do mês seguinte. O último dia do mês substitui configurações como dia 31 em meses menores. Sem configuração, a regra de entregas permanece ilimitada. Submissões posteriores continuam aceitas e armazenadas, mas não geram presença automática para aquele ciclo.

Não há migração destrutiva nem alteração automática de contratos, matrículas ou participações preexistentes.

## Verificação

`mvn -o test` executa a suíte completa. `ApprenticeshipPolicyIntegrationTest` testa consultas reais e o serviço de participantes em H2; os testes de `LessonService` e `ContractService` cobrem os pontos de integração da política, incluindo criação automática e troca de modalidade.
