# INAT EAD Backend

API da plataforma de educação a distância do INAT Paranaguá, instituição do terceiro setor que atua com formação profissional, inclusão social e desenvolvimento de jovens.

## Identidade do projeto

- Artefato Maven: `dev.lourencogabriel:inat-ead-backend`
- Aplicação Spring: `inat-ead-backend`
- Pacote Java base: `dev.lourencogabriel.inatead`
- Classe de inicialização: `InatEadApplication`
- Banco de dados padrão do ambiente Docker: `inat_ead`

## Escopo atual

O backend contém a autenticação JWT existente e a API do modelo educacional on-premises:

- identidade, pessoas, endereços, usuários, papéis e responsáveis;
- organizações do tipo empregador ou escola e seus vínculos;
- contratos de aprendizagem, documentos e arquivos armazenados no filesystem;
- turmas, matrículas, aulas `ONSITE` ou `ONLINE` e atividades;
- chamada esperada por aula (`lesson_participants`) e frequência efetiva (`attendance_records`);
- remanejo pontual de um aprendiz para uma aula de outra turma, sem alterar sua matrícula de origem;
- entregas de atividades, anexos, notificações e estado por destinatário/canal.

As tabelas usam nomes em inglês, chaves ULID nos agregados principais e instantes em UTC.
O bean `Clock` e os campos de instante nas respostas da API, inclusive `timestamp` e `expiresAt`, usam UTC. A API serializa esses campos em ISO 8601 com `Z`; a apresentação em São Paulo é feita na interface. Regras baseadas em datas civis usam `America/Sao_Paulo` explicitamente.

## Autorização

O Spring Method Security aplica RBAC e autorização por objeto. Existem somente quatro papéis de sistema:

- `ADMIN`: administração completa e toda a operação pedagógica, cadastral, documental, contratual e de comunicação;
- `INSTRUCTOR`: somente aulas atribuídas, seus participantes, chamadas, frequências, atividades e correções;
- `LEARNER`: próprio percurso, aulas, entregas, arquivos contextuais, contrato e notificações;
- `EMPLOYER_MANAGER`: leitura da empresa empregadora com vínculo ativo, de seus contratos e dos aprendizes relacionados.

O catálogo é fechado: a API rejeita a criação ou concessão de qualquer outro código. Não existem os papéis genéricos `USER`, de equipe pedagógica ou de gestor escolar. Uma conta sem concessão explícita não recebe autoridade implícita e fica sem acesso aos módulos de domínio; ela ainda pode consultar a própria conta e notificações quando a autorização for por identidade.

| Fluxo/módulo | `ADMIN` | `INSTRUCTOR` | `LEARNER` | `EMPLOYER_MANAGER` |
| --- | --- | --- | --- | --- |
| Pessoas, responsáveis e documentos | Gerência completa | Dados mínimos de aprendizes de suas aulas | Próprio contexto quando exposto pela rota | Dados mínimos de aprendizes contratados pela empresa |
| Aprendizes | Gerência completa | Leitura dos participantes de suas aulas | Próprio registro | Leitura dos vinculados por contrato à empresa |
| Empresas, escolas e vínculos | Gerência completa | Sem acesso direto | Sem acesso direto | Leitura somente da empresa com vínculo ativo |
| Contratos | Gerência completa | Sem acesso | Leitura dos próprios | Leitura dos relacionados à empresa |
| Turmas e matrículas | Gerência completa | Contexto obtido pelas aulas atribuídas | Contexto obtido pelas aulas em que participa | Sem acesso direto |
| Aulas, participantes e frequência | Gerência completa | Gerência das aulas atribuídas | Leitura da própria participação/frequência | Sem acesso |
| Atividades e entregas | Gerência completa | Criação/correção nas aulas atribuídas | Próprias entregas | Sem acesso |
| Comunicações | Criação e acompanhamento global | Caixa própria | Caixa própria | Caixa própria |
| Usuários, catálogo e concessões | Gerência completa | Somente a própria conta/papéis | Somente a própria conta/papéis | Somente a própria conta/papéis |

Em um banco novo, o primeiro papel `ADMIN` deve ser atribuído por um procedimento de bootstrap operacional no banco. A API não promove automaticamente o primeiro usuário e não oferece uma rota pública de elevação de privilégio.

## Organização da API

Os endpoints ficam sob `/api` e seguem os módulos `people`, `users`, `roles`, `learners`, `organizations`, `contracts`, `document-types`, `person-documents`, `cohorts`, `cohort-enrollments`, `lessons`, `lesson-participants`, `attendance-records`, `activities`, `activity-submissions`, `notifications` e `notification-recipients`.

Para paginação, as listagens administrativas aceitam `page` e `size` (máximo 100). Operações específicas incluem geração da chamada esperada, remanejo entre aulas do mesmo dia, correção de frequência, avaliação de entregas, caixa pessoal de notificações e transições de entrega.

### Conflitos de horário das aulas

Duas aulas se sobrepõem quando uma começa antes do término da outra e termina depois do início da outra. Aulas adjacentes são permitidas: uma pode começar exatamente no instante em que a anterior termina.

- Um instrutor não pode possuir duas aulas não canceladas sobrepostas;
- uma turma não pode possuir duas aulas não canceladas sobrepostas;
- um aprendiz não pode possuir duas participações não canceladas em aulas sobrepostas, mesmo que pertençam a turmas diferentes;
- a regra do aprendiz é aplicada na inclusão manual, geração da chamada, remanejo e reativação de participantes;
- ao alterar a turma ou a data de uma aula, as matrículas de origem de todos os participantes ativos já vinculados são revalidadas;
- aulas e participações canceladas são desconsideradas.

As datas civis da aula, da matrícula e do remanejo são calculadas em `America/Sao_Paulo`, independentemente do relógio UTC. Uma aula aberta só pode pertencer a uma turma `ACTIVE`.

Cada aula pode informar `externalLessonUrl`, independentemente da modalidade. O campo é opcional, aceita no máximo 2.048 caracteres e representa o conteúdo externo reproduzível da aula. `meetingUrl` também é opcional, inclusive em aulas `ONLINE`, e aceita no máximo 512 caracteres. Quando preenchidos, ambos os campos precisam conter uma URL com esquema `http://` ou `https://` e host válido.

### Ciclo acadêmico baseado no horário

Um agendador executa a cada minuto por padrão e reconcilia os estados persistidos com o relógio da aplicação:

- aulas `SCHEDULED` passam para `IN_PROGRESS` quando `startsAt <= agora < endsAt`;
- aulas `SCHEDULED` ou `IN_PROGRESS` passam diretamente para `COMPLETED` quando `endsAt <= agora`, inclusive após uma indisponibilidade da aplicação;
- atividades `PUBLISHED` passam para `CLOSED` quando `dueAt <= agora`;
- aulas e atividades `CANCELLED`, atividades `DRAFT` e estados terminais não são alterados;
- `DRAFT` nunca é publicado automaticamente: a publicação continua sendo uma decisão explícita.

Pela API, uma aula só pode ser criada como `SCHEDULED`. As transições aceitas são `SCHEDULED -> IN_PROGRESS`, `SCHEDULED -> CANCELLED`, `IN_PROGRESS -> COMPLETED` e `IN_PROGRESS -> CANCELLED`; `COMPLETED` e `CANCELLED` são terminais. Uma transição manual para `IN_PROGRESS` exige que o horário atual esteja dentro do período da aula, e `COMPLETED` exige que o período já tenha terminado.

A exclusão física é reservada a aulas `SCHEDULED` sem participantes nem atividades. Participantes de aulas `COMPLETED` ou `CANCELLED` também não podem ser excluídos. Participações `TRANSFERRED` só podem ser criadas pelo remanejo administrativo, preservando o cancelamento da origem e a criação do destino na mesma transação.

Antes de fechar as atividades, o mesmo ciclo processa a janela de lembrete de três horas. O intervalo e o tamanho do lote são configuráveis. O processamento de cada atividade usa bloqueio pessimista e grava `dueReminderProcessedAt`, garantindo no máximo um lembrete por prazo mesmo após reinício ou com mais de uma instância. Se `dueAt` mudar, ou a atividade for publicada novamente, essa marca é limpa para o novo prazo.

### Cadastro de usuários

`POST /api/auth/register` cria somente a conta de acesso. O e-mail é normalizado para minúsculas e precisa coincidir com o `contactEmail` de uma pessoa já cadastrada; essa pessoa é vinculada obrigatoriamente ao novo usuário em uma relação um-para-um. O e-mail de contato das pessoas é único quando informado. No registro, a conta recebe as roles correspondentes aos `personTypes` da pessoa: `ADMIN`, `INSTRUCTOR`, `LEARNER` e `EMPLOYER_MANAGER`. `GUARDIAN` não possui role de acesso.

- se não existir uma pessoa com o e-mail fornecido, a API responde `422 Unprocessable Entity`;
- se a pessoa existir, mas estiver `INACTIVE` ou `SUSPENDED`, o cadastro também responde `422 Unprocessable Entity`;
- se a pessoa já estiver vinculada a outra conta, responde `409 Conflict`;
- se o e-mail de login já estiver em uso, responde `409 Conflict`.

Na criação administrativa de usuários, `personId` também é obrigatório e precisa identificar uma pessoa `ACTIVE`. Depois da criação, esse vínculo é imutável: `personId` não faz parte do contrato de atualização e a coluna correspondente não pode ser atualizada pelo ORM. O cadastro de usuário nunca cria uma pessoa implicitamente.

A existência da pessoa elimina a ideia de uma “conta em análise”. Os tipos definidos no cadastro da pessoa determinam as roles iniciais no registro público; alterações posteriores dos tipos não sincronizam as roles da conta. A gestão administrativa de roles continua disponível. `INVITED` não é um papel nem uma análise cadastral; é apenas o estado transitório até a confirmação do e-mail.

A concessão de qualquer role exige que a pessoa vinculada à conta possua o `personType` de mesmo código. A role `LEARNER` também exige um perfil `Learner` com status `ACTIVE`; tipos incompatíveis e perfis ausentes, inativos ou suspensos resultam em `422 Unprocessable Entity`, inclusive durante o registro. As demais roles não dependem de um perfil `Learner`. Classificar uma pessoa não altera automaticamente as roles de uma conta existente.

O cadastro não emite mais JWT imediatamente. Ele cria a conta como `INVITED`, envia um código de seis dígitos e retorna um desafio com `challengeId`, finalidade, e-mail mascarado, expiração e número máximo de tentativas. O código confirma a posse do e-mail e somente `POST /api/auth/challenge/verify` emite os tokens e ativa a conta.

Depois da validação inicial, todo `POST /api/auth/login` bem-sucedido na etapa da senha retorna `202 Accepted` e envia um novo código com finalidade `MFA_LOGIN`. Esse código também precisa ser confirmado em `/api/auth/challenge/verify`. Se uma conta antiga ainda não tiver `emailVerifiedAt`, seu próximo login usa a finalidade `EMAIL_VERIFICATION` antes de liberar tokens.

```json
{
  "challengeId": "01...",
  "code": "123456"
}
```

Cada novo desafio invalida o anterior. Por padrão, o código expira em dez minutos, permite cinco tentativas e fica persistido somente como HMAC-SHA-256 com nonce aleatório. Contas não verificadas também não podem criar ou renovar tokens diretamente.

A recuperação de senha reutiliza a mesma infraestrutura de desafio, mas com a finalidade isolada `PASSWORD_RESET`. `POST /api/auth/forgot-password` recebe o e-mail e retorna o desafio enviado; somente contas `ACTIVE` ou `INVITED` podem solicitar a recuperação. `POST /api/auth/reset-password` recebe `challengeId`, código e `newPassword`, altera a credencial e invalida as sessões existentes sem emitir access token, refresh token ou autenticar o usuário. Um código de recuperação não pode ser usado em `/api/auth/challenge/verify`.

Falhas consecutivas de senha ou de código MFA usam o mesmo contador. Ao atingir cinco falhas, a conta passa temporariamente para `LOCKED` por 15 minutos; um agendador a desbloqueia e a tentativa de login também verifica bloqueios vencidos. Um `LOCKED` definido por administrador não expira. O contador só é zerado depois da confirmação correta do desafio.

Alterações administrativas de estado ou credenciais revogam todos os refresh tokens e incrementam a versão de sessão. Cada access token é validado contra o estado `ACTIVE` e essa versão no banco, portanto deixa de funcionar imediatamente após bloqueio, desativação ou invalidação da sessão. Uma conta sem `emailVerifiedAt` não pode ser criada ou alterada administrativamente para `ACTIVE`: ela precisa permanecer `INVITED` até concluir o desafio de confirmação. Trocar o e-mail também remove a verificação e exige salvar a conta como `INVITED`. Ao selecionar `INVITED`, a verificação anterior do e-mail é removida e o próximo login refaz o fluxo de confirmação. E-mails de notificação ainda pendentes são cancelados quando a conta deixa de estar ativa e o dispatcher confere novamente o estado antes do envio.

### Criação de notificações

`POST /api/notifications` exige uma audiência (`USER`, `COHORT` ou `ALL`) e ao menos um canal (`IN_APP`, `EMAIL` ou `PUSH`). A notificação e todos os destinatários resolvidos são persistidos na mesma transação; destinatários não podem ser criados ou excluídos isoladamente em `/api/notification-recipients`.

- `USER`: exige `audience.targetId` com o ID de uma conta ativa;
- `COHORT`: exige o ID da turma e seleciona os aprendizes com matrícula vigente, perfil ativo e conta ativa;
- `ALL`: não aceita `targetId` e seleciona todas as contas ativas.

A audiência é materializada como um retrato no momento da criação. Assim, alterações posteriores na turma não modificam os destinatários de uma notificação já criada.

```json
{
  "notificationType": "ACTIVITY_DUE",
  "title": "Atividade pendente",
  "message": "Há uma atividade aguardando sua entrega.",
  "priority": "NORMAL",
  "audience": {
    "type": "COHORT",
    "targetId": "01K4B3ZQ2M8T7V6W5X4Y3Z2A1B"
  },
  "channels": ["IN_APP", "EMAIL"]
}
```

O `PUT /api/notifications/{id}` altera somente o conteúdo e o agendamento enquanto nenhum destinatário tiver sido processado. Audiência e canais são imutáveis após a criação.

Destinatários do canal `EMAIL` são processados em segundo plano. O dispatcher seleciona notificações disponíveis, bloqueia cada destinatário durante o envio e usa o SMTP da Hostinger. Quando o servidor SMTP aceita a mensagem, o destinatário passa para `SENT`; erros ficam em `FAILED` com motivo seguro e podem retornar a `PENDING` pelo fluxo administrativo para nova tentativa. Como não há webhook de entrega da caixa postal, a aplicação não marca automaticamente e-mails como `DELIVERED`.

### Notificações automáticas de atividades

- Ao criar uma atividade como `PUBLISHED`, ou sempre que uma atividade mudar de outro estado para `PUBLISHED`, todos os alunos ativos da turma vinculada à aula recebem notificações `IN_APP` e `EMAIL`. Se `availableAt` estiver no futuro, ambos os canais aguardam esse instante;
- ao entrar na janela de três horas antes de `dueAt`, é criada uma notificação `ACTIVITY_DUE_SOON`, nos canais `IN_APP` e `EMAIL`, somente para alunos ativos da turma que não possuam entrega em `SUBMITTED`, `LATE` ou `GRADED`; alunos sem entrega ou com entrega `DRAFT`/`RETURNED` recebem o lembrete;
- o lembrete expira em `dueAt`, não é duplicado e não cria uma notificação vazia quando todos já entregaram;
- atividades em `DRAFT`, `CLOSED` ou `CANCELLED` não notificam alunos;
- quando uma entrega muda para `SUBMITTED` ou `LATE`, o usuário ativo com papel de professor vinculado à aula recebe notificações `IN_APP` e `EMAIL`;
- se a turma não possuir alunos ativos, ou o professor não possuir uma conta ativa, o evento principal é mantido sem criar uma notificação sem destinatário.

### Restrições atuais de entrega de atividades

- Existe no máximo uma entrega por combinação de atividade e aluno;
- atividades de aulas `SCHEDULED` não aceitam criação, edição ou exclusão de entregas, nem inclusão ou remoção de anexos; quanto ao status da aula, essas operações são liberadas em `IN_PROGRESS` e `COMPLETED`, sujeitas às demais regras da entrega;
- a atividade precisa estar em `PUBLISHED`; depois do fechamento automático, uma atividade `CLOSED` continua aceitando somente o envio efetivo, convertido em `LATE`, para preservar entregas atrasadas;
- o aluno precisa constar como participante da aula e não pode estar com participação `CANCELLED`; aprendiz, pessoa e turma precisam continuar ativos e, quando a participação veio de uma matrícula, matrícula e contrato também precisam estar `ACTIVE`;
- o aluno só pode salvar os estados `DRAFT` ou `SUBMITTED`; um envio posterior ao prazo é convertido automaticamente em `LATE`;
- somente entregas `DRAFT` ou `RETURNED` podem ser editadas ou excluídas; entregas e anexos de aluno também ficam bloqueados quando a aula está cancelada;
- uma entrega `SUBMITTED` ou `LATE` só pode ser avaliada ou devolvida, e uma nota não pode exceder `maxScore`;
- atualmente não há bloqueio para salvar antes de `availableAt`, não há recusa depois de `dueAt` e texto/anexo não são obrigatórios;
- anexos devem ser não vazios, têm limite padrão de 25 MB, nome de até 255 caracteres e precisam passar pela política de extensão, MIME declarado e conteúdo real. Ainda não há limite de quantidade de anexos.

### Frequência automática em aulas online

O `AcademicLifecycleScheduler` verifica a presença automática a cada 60 segundos, por padrão. A entrega em `SUBMITTED` ou `LATE` apenas registra o envio e notifica o instrutor:

- a regra é executada somente quando `lessons.delivery_mode` é `ONLINE` e a aula está em `IN_PROGRESS` ou `COMPLETED`;
- é necessário existir ao menos uma atividade `PUBLISHED` ou `CLOSED` na aula; atividades `DRAFT` e `CANCELLED` não entram no conjunto obrigatório;
- cada atividade obrigatória precisa possuir uma entrega do aprendiz em `GRADED`, com `gradedAt` preenchido e nota **estritamente maior que 6**; notas iguais a 6, entregas ainda não corrigidas e atividades sem `maxScore` não geram presença automática;
- quando todas estiverem corrigidas e aprovadas, é criado um único `attendance_record` com estado `PRESENT`, `checkInAt` igual a `lesson.startsAt` e `checkOutAt` igual a `lesson.endsAt`;
- quando a empresa do contrato informa `attendanceClosingDay`, só contam entregas feitas até o fim desse dia no ciclo da aula: aulas no dia do fechamento ou antes usam o mês atual, e aulas posteriores usam o mês seguinte. Dias inexistentes no mês são ajustados para o último dia;
- sem `attendanceClosingDay`, não há corte por data de entrega. O limite afeta somente a presença automática e não impede nem altera submissões posteriores;
- o instrutor da aula fica registrado em `recordedByUser`; o participante é bloqueado durante a verificação para evitar duplicidade;
- após o fim da aula, o scheduler registra `ABSENT` para participantes esperados sem frequência. Se todas as atividades forem corrigidas e aprovadas depois, essa falta automática é convertida em `PRESENT`; lançamentos manuais de frequência são preservados.

O lançamento ou a correção manual de frequência é bloqueado enquanto a aula estiver `SCHEDULED`; aulas `IN_PROGRESS` e `COMPLETED` aceitam o registro.

O domínio de modalidade aceita exclusivamente `ONSITE` ou `ONLINE` nas requisições de aula.

### Armazenamento de arquivos

O binário é gravado no filesystem e a tabela `files` mantém somente seus metadados. `StoredFile` é uma implementação interna: não existe um CRUD `/api/files`, não é possível fornecer `storageKey`, tamanho ou checksum manualmente, e a chave física nunca é exposta ao cliente. Os endpoints dos recursos criam o metadado e o vínculo na mesma transação do upload:

| Recurso | Upload | Download |
| --- | --- | --- |
| Documento de pessoa | `POST /api/person-documents` | `GET /api/person-documents/{id}/content` |
| Documento de contrato | `POST /api/contract-documents` | `GET /api/contract-documents/{id}/content` |
| Material de atividade | `POST /api/activities/{activityId}/files` | `GET /api/activities/{activityId}/files/{fileId}/content` |
| Anexo de entrega | `POST /api/activity-submissions/{submissionId}/files` | `GET /api/activity-submissions/{submissionId}/files/{fileId}/content` |

Uploads que também possuem dados estruturados usam `multipart/form-data`: a parte `metadata` contém JSON com `Content-Type: application/json`, e a parte `file` contém o binário. Por exemplo:

```bash
curl -X POST http://localhost:8080/api/person-documents \
  -H "Authorization: Bearer <token>" \
  -F 'metadata={"personId":"01...","documentTypeId":1,"documentNumber":"123","issuedOn":"2026-09-04","expiresOn":null};type=application/json' \
  -F 'file=@documento.pdf;type=application/pdf'
```

O upload de uma entrega não possui metadados adicionais e envia apenas a parte `file`. Nas respostas, `file` informa ID, nome original, MIME type, tamanho, SHA-256, autor e data. O download sempre usa `Content-Disposition: attachment` e respeita a autorização do recurso pai.

Durante a gravação, a API transmite o conteúdo para uma área temporária, calcula SHA-256 e só instala o arquivo depois de todas as inspeções. A chave física é endereçada pelo checksum (`sha256/aa/bb/<checksum>`): arquivos com bytes idênticos compartilham um único binário, mas mantêm seus próprios metadados e vínculos. A reutilização também confere tamanho e checksum do conteúdo já existente. Um rollback remove um binário novo sem referência; na exclusão, o binário compartilhado só é apagado depois do commit e quando o último metadado deixa de referenciá-lo. No Docker Compose, o volume nomeado `file_storage` preserva os dados em `/var/lib/inat/files`.

As políticas padrão são:

- documentos pessoais ou contratuais: `pdf`, `png`, `jpg`, `jpeg` e `webp`;
- materiais de atividade e anexos de entrega: os formatos anteriores, além de `gif`, `txt`, `csv`, `rtf`, `docx`, `xlsx`, `pptx`, `odt`, `ods`, `odp`, `mp3`, `mp4`, `webm` e `wav`;
- extensões executáveis, como `.exe`, são recusadas em todos os contextos;
- a extensão, o MIME declarado e a assinatura/estrutura real precisam ser compatíveis. Arquivos Office e OpenDocument são abertos como contêineres, com bloqueio de macros, entradas executáveis, caminhos inválidos e expansão excessiva;
- quando o ClamAV é habilitado, o arquivo também é enviado ao `clamd` pelo protocolo `INSTREAM`. Malware é recusado com `422`; se o antivírus estiver indisponível, o upload falha fechado com `503`.

A inspeção estrutural é sempre ativa. O ClamAV é opcional por padrão para não tornar o ambiente local dependente de outro processo, mas deve ser habilitado em produção quando houver um `clamd` acessível.

### Regras de documentos

Cada tipo possui um `scope`: `PERSON`, `CONTRACT` ou `BOTH`. Um tipo não pode ser alterado para um escopo incompatível enquanto já estiver em uso.

As listagens administrativas aplicam os filtros no banco, antes da paginação (`page` começa em 0; `size` vai de 1 a 100):

- `GET /api/person-documents?personId=...` retorna somente os documentos daquela pessoa;
- `GET /api/person-documents?verificationStatus=EXPIRED` retorna somente expirados, ordenados pela validade mais antiga e pelo identificador; `personId` e `verificationStatus` podem ser combinados;
- `GET /api/contract-documents?contractId=...` retorna somente os documentos daquele contrato, sem documentos pessoais do aprendiz;
- `GET /api/learners?personId=...` localiza o perfil do aprendiz para os links da lista de expirados, sem carregar todos os aprendizes.

Sem filtros, os endpoints mantêm a listagem administrativa existente. Listagens documentais que não filtram por `EXPIRED` são ordenadas por criação decrescente e identificador. Os filtros não ampliam permissões: essas listagens continuam restritas a `ADMIN`.

Documentos pessoais seguem estas regras:

- somente uma pessoa com status `ACTIVE` pode receber novos documentos ou ter seus metadados alterados;
- existe no máximo um documento simultâneo por pessoa e tipo;
- quando informado, o número é único dentro do tipo de documento, sem distinção entre maiúsculas e minúsculas;
- `issuedOn` não aceita uma data futura; `expiresOn` não aceita uma data já passada nem anterior à emissão;
- o upload administrativo nasce como `VERIFIED`, com o administrador e o instante da verificação registrados;
- a pessoa e o tipo são imutáveis depois da criação;
- quando a validade passa, o agendador muda `VERIFIED` para `EXPIRED`. Uma correção com validade novamente válida registra a renovação e retorna a `VERIFIED`;
- cada mudança de status gera uma entrada individual com estado anterior, novo estado, motivo, instante e usuário responsável quando houver. O histórico acompanha a resposta do documento.

Documentos contratuais só podem ser criados ou alterados enquanto o contrato estiver em `DRAFT`, `ACTIVE` ou `SUSPENDED`; contratos `ENDED` e `CANCELLED` ficam bloqueados para novas versões ou alterações. O contrato e o tipo do documento são imutáveis depois da criação. A regra já existente de uma única versão atual por contrato e tipo permanece válida.

O ciclo de validade documental executa a cada minuto por padrão, processa lotes configuráveis e usa bloqueio pessimista antes de cada transição. A validade informada representa o último dia válido; por isso, um documento com `expiresOn` igual à data atual só passa a `EXPIRED` no dia seguinte.

Em instalações que já possuam a restrição antiga `uk_files_storage_key`, ela precisa ser removida pela migração do banco antes de usar a deduplicação, pois múltiplos registros de metadados podem apontar para o mesmo binário. Em uma base nova, o Hibernate cria diretamente o índice não exclusivo atual.

### Ciclo de contratos, turmas e matrículas

- contratos são criados exclusivamente como `DRAFT`; nenhum documento é exigido na criação ou na ativação;
- aprendiz, empresa, escola e data inicial do contrato ficam imutáveis. Uma data final existente só pode ser mantida ou estendida; removê-la representa prazo indeterminado. Reduzi-la para a data atual é permitido exclusivamente ao cancelar e nunca é aceita uma nova data anterior ao dia atual;
- as transições permitidas são `DRAFT → ACTIVE/CANCELLED`, `ACTIVE → SUSPENDED/ENDED/CANCELLED` e `SUSPENDED → ACTIVE/ENDED/CANCELLED`. Suspensão e cancelamento exigem motivo, e estados terminais não podem ser reabertos;
- ativar ou manter contrato operacional exige aprendiz, pessoa, empresa e eventual escola ativos. Esses cadastros não podem ser inativados enquanto participarem de um contrato `ACTIVE` ou `SUSPENDED`;
- o cancelamento do contrato cancela matrículas pendentes/ativas e participantes de aulas abertas originados por elas. O encerramento conclui matrículas ativas e cancela as que ainda estavam pendentes;
- turmas são criadas como `PLANNED`, podem ter capacidade opcional e só ficam `ACTIVE` durante o próprio período. Matrículas `PENDING` e `ACTIVE` reservam vaga;
- cancelar uma turma cancela suas matrículas abertas, aulas `SCHEDULED`/`IN_PROGRESS`, atividades `DRAFT`/`PUBLISHED` e os lembretes ainda vigentes, na mesma transação;
- uma turma só pode ser concluída depois de seu prazo e sem aulas abertas. Turmas e matrículas concluídas ou canceladas são somente leitura; exclusão física fica restrita a rascunhos/planejamentos ainda sem dependências;
- contratos, turmas e matrículas mantêm histórico individual de cada mudança de estado, incluindo origem automática ou administrativa, motivo, instante e usuário quando houver;
- o agendador acadêmico também encerra contratos e conclui turmas/matrículas cujo prazo já passou. Rascunhos, planejamentos e matrículas pendentes continuam dependendo de decisão administrativa.

### Onboarding de aprendizes

`POST /api/learner-onboardings` cria a pessoa, incluindo seu endereço, e o perfil de aprendiz, além de vincular os responsáveis em uma única transação. Se o CPF já existir, a pessoa é reutilizada sem ter seus dados sobrescritos; a data de nascimento informada deve coincidir com o cadastro existente. Aprendizes menores de 18 anos devem receber ao menos um vínculo marcado como responsável legal.

`POST /api/people/{personId}/learner` cria o perfil para uma pessoa já cadastrada. O corpo contém `learner` (escolaridade e situação) e `guardians` (lista de responsáveis), como no onboarding, mas não contém `person`. A pessoa deve existir e já possuir o tipo `LEARNER`; o endpoint não altera seus dados nem seus tipos. A mesma regra de responsável legal para menores e a transação única se aplicam. A resposta inclui `person`, `learner`, `guardians` e `personCreated: false`. Ambas as rotas são exclusivas de `ADMIN`.

`learners.registration_number` é um `BIGINT AUTO_INCREMENT` único, independente do ULID `learners.id`. O banco gera a matrícula na criação, inclusive para os registros importados; o campo é retornado pela API e não faz parte dos corpos de criação ou edição.

O cadastro de pessoa enviado no onboarding deve incluir `LEARNER` em `personTypes`. Se o CPF já existir, a pessoa cadastrada também precisa ter esse tipo; o onboarding não altera sua classificação implicitamente. As pessoas usadas como responsáveis precisam existir previamente, ter o tipo `GUARDIAN` e ao menos 18 anos. As atualizações de pessoa, aprendiz e vínculo de responsável não podem tornar essas regras inválidas, e o último responsável legal de um aprendiz menor não pode ser removido ou desmarcado. Os endpoints individuais de aprendizes permanecem disponíveis somente para consulta, atualização e exclusão administrativa; `POST /api/learners` não existe.

A escola é vinculada pelo contrato. Ela é obrigatória quando o aprendiz tinha menos de 18 anos na data inicial do contrato e ainda não concluiu o ensino médio. Atualizações de pessoa, aprendiz, contrato ou tipo da organização preservam essa regra. Endereços são componentes obrigatórios de pessoas e organizações e não possuem endpoint próprio.

### Tipos de pessoa

`PersonType` possui os códigos fixos `ADMIN`, `INSTRUCTOR`, `LEARNER`, `EMPLOYER_MANAGER` e `GUARDIAN`. A relação com `Person` é muitos-para-muitos, persistida nas tabelas `person_types` e `person_type_assignments`, com unicidade por pessoa e tipo. Os tipos classificam responsabilidades; as roles do usuário continuam controlando o acesso ao sistema.

Os endpoints existentes `POST /api/people`, `PUT /api/people/{id}` e suas respostas aceitam/exibem `personTypes`, por exemplo `"personTypes": ["INSTRUCTOR", "GUARDIAN"]`. A gestão desses dados é exclusiva de `ADMIN`. Uma lista explícita substitui os tipos atuais; `[]` remove os tipos não utilizados. Omitir o campo ou enviar `null` preserva os tipos em uma atualização e cria uma pessoa sem classificação em um novo cadastro. Códigos desconhecidos e elementos nulos são rejeitados. `GET /api/people?personType=GUARDIAN&page=0&size=20` aplica o filtro antes da paginação.

- Instrutores de aulas exigem `INSTRUCTOR`, além da conta ativa com role `INSTRUCTOR` já exigida pelo sistema.
- Perfis de aprendiz exigem `LEARNER`; vínculos de responsável exigem `GUARDIAN` e maioridade.
- Funções organizacionais cujo `membershipRole` corresponda a um dos códigos de tipo exigem esse tipo. Funções de negócio livres continuam disponíveis.
- A concessão de qualquer role exige o tipo correspondente na pessoa. `GUARDIAN` não é uma role de acesso.
- Um tipo não pode ser removido enquanto houver um perfil de aprendiz, vínculo de responsável, atribuição de instrutor em aula, função organizacional correspondente ou role que dependa dele. Essa verificação também considera vínculos históricos ainda cadastrados. As atribuições e a edição de tipos bloqueiam a pessoa durante a transação.

Na primeira inicialização do catálogo, os registros anteriores recebem os tipos deduzidos de suas roles, perfis de aprendiz, vínculos de responsável, aulas e funções organizacionais correspondentes. A atualização é transacional e executada somente quando o catálogo ainda está vazio; reinícios seguintes preservam as escolhas administrativas. O schema e os dados iniciais são versionados pelo Flyway; o Hibernate apenas valida sua compatibilidade com as entidades.

## Tecnologias

- Java 21
- Spring Boot
- Spring Security e OAuth 2.0 Resource Server
- Spring Mail
- FreeMarker para templates de e-mail
- Spring Data JPA
- MySQL
- Maven
- JUnit, Mockito, H2 e Testcontainers

## Configuração

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Sim | URL JDBC do MySQL |
| `SPRING_DATASOURCE_USERNAME` | Sim | Usuário do banco de dados |
| `SPRING_DATASOURCE_PASSWORD` | Sim | Senha do banco de dados |
| `SPRING_JPA_SHOW_SQL` | Não | Exibe o SQL do Hibernate; padrão: `false` |
| `API_SECURITY_JWT_SECRET` | Sim | Chave de assinatura JWT com pelo menos 32 caracteres |
| `API_SECURITY_JWT_ISSUER` | Sim | URL que identifica o emissor dos tokens |
| `CORS_ALLOWED_ORIGINS` | Não | Origens exatas permitidas, separadas por vírgula; padrão local: `http://localhost:3000` |
| `API_SECURITY_REFRESH_COOKIE_SECURE` | Não | Exige HTTPS no cookie de refresh; padrão: `true` |
| `JAVA_TOOL_OPTIONS` | Não | Opções adicionais aplicadas pela JVM no container |
| `EMAIL` | Sim | Endereço completo da caixa Hostinger, usado como usuário SMTP e remetente |
| `EMAIL_PASS` | Sim | Senha da caixa de e-mail Hostinger |
| `PORTAL_BASE_URL` | Não | Origem pública do portal usada para resolver links relativos dos e-mails; padrão local: `http://localhost:3000` |
| `SMTP_HOST` | Não | Servidor SMTP; padrão: `smtp.hostinger.com` |
| `SMTP_PORT` | Não | Porta SMTP; padrão: `465` |
| `SMTP_SSL_ENABLED` | Não | Habilita SSL implícito; padrão: `true` |
| `SMTP_STARTTLS_ENABLED` | Não | Habilita STARTTLS, normalmente com porta `587`; padrão: `false` |
| `SMTP_STARTTLS_REQUIRED` | Não | Exige STARTTLS quando ele estiver habilitado; padrão: `false` |
| `SMTP_CONNECTION_TIMEOUT` | Não | Timeout de conexão SMTP em milissegundos; padrão: `5000` |
| `SMTP_READ_TIMEOUT` | Não | Timeout de leitura SMTP em milissegundos; padrão: `5000` |
| `SMTP_WRITE_TIMEOUT` | Não | Timeout de escrita SMTP em milissegundos; padrão: `5000` |
| `AUTH_CHALLENGE_TTL` | Não | Validade dos códigos de autenticação; padrão: `10m` |
| `AUTH_CHALLENGE_MAX_ATTEMPTS` | Não | Tentativas permitidas por desafio; padrão: `5` |
| `AUTH_ACCOUNT_LOCK_MAX_FAILED_ATTEMPTS` | Não | Falhas consecutivas de senha/MFA antes do bloqueio; padrão: `5` |
| `AUTH_ACCOUNT_LOCK_DURATION` | Não | Duração do bloqueio automático; padrão: `15m` |
| `AUTH_ACCOUNT_LOCK_SCHEDULER_ENABLED` | Não | Habilita o desbloqueio automático agendado; padrão: `true` |
| `AUTH_ACCOUNT_UNLOCK_INTERVAL` | Não | Intervalo do desbloqueio automático em milissegundos; padrão: `60000` |
| `NOTIFICATION_EMAIL_ENABLED` | Não | Habilita o dispatcher de notificações por e-mail; padrão: `true` |
| `NOTIFICATION_EMAIL_DISPATCH_DELAY` | Não | Intervalo do dispatcher de e-mails em milissegundos; padrão: `5000` |
| `NOTIFICATION_EMAIL_BATCH_SIZE` | Não | Máximo de notificações processadas por ciclo; padrão: `50` |
| `ACADEMIC_LIFECYCLE_ENABLED` | Não | Habilita o agendador de estados acadêmicos e lembretes; padrão: `true` |
| `ACADEMIC_LIFECYCLE_INTERVAL` | Não | Intervalo em milissegundos entre execuções; padrão: `60000` |
| `ACADEMIC_REMINDER_BATCH_SIZE` | Não | Máximo de atividades verificadas por ciclo para o lembrete; padrão: `200` (limite: `1000`) |
| `ACADEMIC_ATTENDANCE_BATCH_SIZE` | Não | Máximo de aulas processadas por ciclo de fechamento de presença; padrão: `200` |
| `DOCUMENT_LIFECYCLE_ENABLED` | Não | Habilita a expiração automática de documentos pessoais; padrão: `true` |
| `DOCUMENT_LIFECYCLE_INTERVAL` | Não | Intervalo do ciclo documental em milissegundos; padrão: `60000` |
| `DOCUMENT_LIFECYCLE_BATCH_SIZE` | Não | Máximo de documentos expirados por ciclo; padrão: `200` (limite: `1000`) |
| `FILE_STORAGE_ROOT` | Não | Diretório do filesystem; padrão local: `./data/files` |
| `FILE_STORAGE_MAX_SIZE` | Não | Tamanho máximo de cada arquivo; padrão: `25MB` |
| `FILE_STORAGE_MAX_REQUEST_SIZE` | Não | Tamanho máximo da requisição multipart; padrão: `26MB` |
| `FILE_ALLOWED_DOCUMENT_EXTENSIONS` | Não | Extensões aceitas em documentos pessoais/contratuais; padrão: `pdf,png,jpg,jpeg,webp` |
| `FILE_ALLOWED_ACTIVITY_EXTENSIONS` | Não | Extensões aceitas em materiais de atividade; usa a lista ampliada descrita acima |
| `FILE_ALLOWED_SUBMISSION_EXTENSIONS` | Não | Extensões aceitas em anexos de entrega; usa a lista ampliada descrita acima |
| `FILE_ANTIVIRUS_ENABLED` | Não | Habilita a inspeção pelo ClamAV; padrão: `false` |
| `CLAMAV_HOST` | Não | Host acessível do processo `clamd`; padrão: `localhost` |
| `CLAMAV_PORT` | Não | Porta TCP do `clamd`; padrão: `3310` |
| `CLAMAV_CONNECT_TIMEOUT` | Não | Timeout para conectar ao ClamAV; padrão: `3s` |
| `CLAMAV_READ_TIMEOUT` | Não | Timeout para receber o resultado do ClamAV; padrão: `30s` |
| `SERVER_PORT` | Não | Porta HTTP; padrão: `8080` |

Os e-mails são enviados como `multipart/alternative`, com texto simples e HTML renderizado por templates FreeMarker. Links de notificações são limitados à mesma origem de `PORTAL_BASE_URL`.

O padrão da Hostinger usa `smtp.hostinger.com:465` com SSL. Para a alternativa STARTTLS, configure `SMTP_PORT=587`, `SMTP_SSL_ENABLED=false`, `SMTP_STARTTLS_ENABLED=true` e `SMTP_STARTTLS_REQUIRED=true`.

O [repositório de infraestrutura](https://github.com/GabrielLourenco1912/infra-inat) contém o `.env.example` usado pelo Docker Compose. Copie-o para `.env` e substitua os valores sensíveis antes de iniciar os serviços.

## Execução

Na raiz do repositório:

```bash
docker compose up --build
```

Para executar apenas os testes do backend:

```bash
./mvnw test
```
