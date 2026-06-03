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
   - Toque em **Salvar config**.
3. Toque em **Abrir Configurações de Acessibilidade** e ative o **UberWatch** na lista.
   (Vai pedir confirmação de que o app pode ler o conteúdo da tela — é necessário.)
4. Abra o app da Uber, vá até a tela de estimativa de preço da corrida, e **deixe a tela aberta**.
5. Mantenha o celular ligado nessa tela. Quando o preço cair conforme sua regra, chega a notificação.

## Regras de disparo
- **Alvo fixo:** preço atual <= alvo.
- **Queda do pico:** preço atual <= pico * (1 - pct/100). O "pico" é o maior preço visto desde o último reset.
- Notifica só quando o preço é novo/menor que o último já alertado (evita spam).
- Use **Resetar histórico** quando começar a vigiar uma nova corrida.

## Limitações conhecidas (app pessoal, interface lixona mesmo)
- **Heurística de preço:** o app pega o MAIOR valor plausível em `R$` na tela como sendo o
  preço da corrida. Se a Uber mostrar outro valor maior (ex: total de viagem antiga, gorjeta),
  pode pegar errado. Ajuste `MIN_PLAUSIBLE`/`MAX_PLAUSIBLE` ou a heurística em
  `PriceAccessibilityService.kt` se notar leitura errada.
- **Quebra com mudança de UI:** se a Uber redesenhar a tela, o parsing pode parar. É esperado.
- **Só lê em foreground:** se você sair da tela da Uber, ele para de ler até voltar.
- **Múltiplas categorias (UberX, Comfort, etc):** ele lê todos os preços e pega o maior. Se
  quiser monitorar uma categoria específica, vai precisar refinar `collectPrices` para casar
  o preço com o texto da categoria.

## Ajustes que você provavelmente vai querer
Em `PriceAccessibilityService.kt`:
- `MIN_INTERVAL_MS` — frequência de leitura (default 1.5s).
- A linha `prices...maxOrNull()` — troque a heurística se o preço certo não for o maior.
