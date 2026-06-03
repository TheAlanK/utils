# UberWatch

App Android pessoal que monitora o preço exibido no app da Uber (via Accessibility Service)
e notifica quando o preço cai abaixo de um alvo fixo OU cai X% do pico observado — o que
disparar primeiro.

## Por que Accessibility Service
A Uber não tem API pública de preço. Este app lê o texto da tela do app oficial da Uber
enquanto ela está em foreground. O Android não permite ler apps em background, então a Uber
precisa estar visível na tela de estimativa de preço para o monitoramento funcionar.

## Como gerar o APK SEM instalar nada (GitHub Actions) — recomendado
Você não precisa de PC com Android Studio. O GitHub compila pra você na nuvem.

1. Crie uma conta no GitHub (se não tiver) e crie um repositório novo (pode ser **privado**).
2. Suba os arquivos deste projeto no repo:
   - No site do repo: botão **Add file > Upload files**, arraste TODO o conteúdo da pasta
     `UberWatch` (incluindo a pasta oculta `.github`). Commit.
   - Importante: mantenha a estrutura de pastas. Se o upload pelo site não pegar a pasta
     `.github`, crie o arquivo manualmente em `.github/workflows/build.yml` com o conteúdo
     que está no projeto.
3. Vá na aba **Actions** do repo. O build "Build APK" roda sozinho após o commit
   (ou clique em **Run workflow**).
4. Quando terminar (uns 2-4 min), abra o run concluído e baixe o artifact
   **UberWatch-debug-apk** — dentro tem o `.apk`.
5. Passe o `.apk` pro celular e instale (precisa permitir "instalar de fontes desconhecidas").

O APK debug já vem assinado automaticamente, então instala direto. Não precisa configurar chave.

## Alternativa: compilar no Android Studio
1. Abra a pasta `UberWatch` no Android Studio (Hedgehog ou mais novo).
2. Deixe o Gradle sincronizar.
3. Build > Build APK, ou Run direto no celular via USB.
   - Terminal: `./gradlew assembleDebug` gera o APK em `app/build/outputs/apk/debug/`.

## Como usar
1. Instale o APK no celular.
2. Abra o UberWatch:
   - Defina **Preço alvo** (ex: 25.00) e/ou **Queda do pico %** (ex: 15).
   - Em **Categorias para monitorar**, liste as opções que te interessam, separadas por
     vírgula (ex: `UberX, Comfort, Prioridade`). Pode ser uma ou várias. Se deixar **vazio**,
     ele monitora o **menor preço** que aparecer na tela.
   - Toque em **Salvar config**.
3. Toque em **Abrir Configurações de Acessibilidade** e ative o **UberWatch** na lista.
   (Vai pedir confirmação de que o app pode ler o conteúdo da tela — é necessário.)
4. Toque em **Abrir Uber e iniciar monitoramento**. Isso liga o monitoramento e já abre a
   Uber. Vá até a tela de estimativa de preço da corrida e **deixe a tela aberta**.
5. Mantenha o celular nessa tela. O app relê a tela **a cada 2s** (não depende mais de você
   trocar de foco) e, quando o preço cair conforme sua regra, chega a notificação por categoria.
6. Quando terminar, toque em **Parar monitoramento** para o app parar de reler a tela.

## Qual preço ele lê (várias categorias / desconto)
- A tela da Uber mostra várias categorias (UberX, Comfort, Prioridade, ...), cada uma com seu
  preço. O app **casa cada preço com a categoria pela linha** em que aparecem (mesma posição
  vertical na tela).
- Quando há promoção, a linha mostra **dois preços**: o com desconto e o **cheio riscado**
  (maior). O app considera o **menor da linha** = o preço **com desconto**, que é o que você paga.
- O nome da categoria casa mesmo truncado (ex: `Priorida...` casa com `Prioridade`) e ignora
  acentos/maiúsculas.

## Regras de disparo (por categoria)
- **Alvo fixo:** preço atual <= alvo.
- **Queda do pico:** preço atual <= pico * (1 - pct/100). O "pico" é o maior preço visto
  daquela categoria desde o último reset.
- Cada categoria tem seu próprio pico/alerta e gera notificação separada.
- Notifica só quando o preço é novo/menor que o último já alertado (evita spam).
- Use **Resetar histórico** ao começar a vigiar uma nova corrida.

## Limitações conhecidas (app pessoal, interface lixona mesmo)
- **Heurística de preço:** dentro da linha da categoria ele pega o **menor** valor em `R$`
  (assume que o maior é o preço cheio riscado). Se a Uber mostrar a linha de outro jeito, pode
  errar. Ajuste `MIN_PLAUSIBLE`/`MAX_PLAUSIBLE` ou a lógica em `PriceAccessibilityService.kt`.
- **Casamento por linha:** depende dos preços ficarem na mesma altura do nome da categoria.
  Se a Uber empilhar diferente, pode pegar a linha errada — ajuste a tolerância (`tol`).
- **Quebra com mudança de UI:** se a Uber redesenhar a tela, o parsing pode parar. É esperado.
- **Só lê em foreground:** se você sair da tela da Uber, ele para de ler até voltar.

## Diagnóstico (saber se está funcionando)
A tela principal tem um painel **Diagnóstico** que atualiza sozinho a cada 1s:
- **Serviço:** se o serviço de acessibilidade está conectado.
- **Leituras (ticks):** contador que sobe a cada ~2s. Se **não sobe**, o serviço não está
  rodando (acessibilidade desligada ou serviço morto pelo sistema).
- **Última leitura:** há quantos segundos foi o último ciclo.
- **Log:** as últimas linhas do que o app está fazendo (qual app está em foreground, quantos
  preços achou, qual categoria casou, alertas disparados).

Tudo isso também sai no **Logcat** com a tag `UberWatch` (útil via `adb logcat -s UberWatch`).

### "Os preços não atualizam"
O Android mantém um **cache da árvore de acessibilidade**: ao reler a tela sem um evento de
mudança, ele devolve o texto antigo — por isso parecia que só atualizava ao trocar o foco. O
app agora força `refresh()` nos nós com texto (em uma thread de background, porque `refresh()`
faz IPC). Se ainda assim travar, olhe no log se o `tick:` mostra o mesmo preço sempre.

## Ajustes que você provavelmente vai querer
Em `PriceAccessibilityService.kt`:
- `POLL_INTERVAL_MS` — frequência de releitura da tela (default 2s).
- A linha `rowPrices.minByOrNull { ... }` — troque a heurística se o preço certo não for o
  menor da linha.
- `tol` (tolerância vertical) — aumente se o preço e o nome da categoria não estiverem casando.
