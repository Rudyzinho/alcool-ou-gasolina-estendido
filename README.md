# Álcool ou Gasolina Estendido 🚗⛽

![Banner do App](imagens/1.png)

**Álcool ou Gasolina Estendido** é um aplicativo Android desenvolvido para ajudar o usuário a decidir se deve abastecer seu veículo com álcool ou gasolina, baseado na relação de preços de cada combustível e em critérios personalizados de rentabilidade. O app também permite registrar e salvar locais de postos de combustível com preços históricos, usando mapa interativo.

---

## 📱 Tecnologias Utilizadas

* **Kotlin** – Linguagem principal do app, para desenvolvimento Android moderno.
* **Jetpack Compose** – Para criar a interface de usuário de forma declarativa.
* **Material Design** – Para o design moderno e responsivo dos componentes.
* **OSMDroid** – Biblioteca open-source para mapas offline/online no Android.
* **Google FusedLocationProvider** – Para localizar a posição atual do usuário.
* **SharedPreferences** – Para persistência local de dados (lista de postos, histórico e preferências de critérios).
* **Gson** – Para serialização/deserialização de objetos Kotlin para JSON.
* **UUID** – Para gerar identificadores únicos para cada estação/posto.

---

## ⚙️ Funcionalidades

1. **Cálculo de recomendação de combustível**

   * O app calcula automaticamente se o álcool ou gasolina é mais vantajoso usando a relação `(alcool / gasolina) * 100` e compara com um critério de referência.
   * O usuário pode escolher entre dois critérios: **70% (padrão)** ou **75% (checado)**.

2. **Cadastro de postos**

   * Nome do posto, preço do álcool e gasolina, localização via mapa e endereço completo.
   * Cada registro armazena a **percentual de referência usado no momento do cadastro**, permitindo comparações futuras consistentes.

3. **Histórico de postos**

   * Lista de postos cadastrados ordenada pela data de registro.
   * Cada item mostra:

     * Nome do posto
     * Preços de álcool e gasolina
     * Percentual usado na comparação
     * Resultado da recomendação
     * Data e endereço
   * É possível **editar** ou **remover** cada registro.

4. **Mapas interativos**

   * Seleção de localização em mapa via **OSMDroid**.
   * Marcador no ponto selecionado.
   * Botão para centralizar na posição atual do usuário.
   * Reverse geocoding para obter endereço completo do ponto selecionado.

5. **Design moderno**

   * Tema claro e escuro baseado na configuração do sistema.
   * Layouts responsivos usando Jetpack Compose.
   * Componentes com Material Design: `Card`, `Button`, `Switch`, `OutlinedTextField`, etc.

---

---

## 📸 Capturas e Demonstrações

### Tela Principal

![Tela Principal](imagens/telaprincipal.png)

### Seleção de Localização no Mapa

![Mapa](imagens/mapscreen.png)

### Histórico de Postos

![Histórico](imagens/comparacoes.png)

### Vídeo Demonstração

[![Assista ao vídeo]([https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Android_Studio_icon_%282023%29.svg/2048px-Android_Studio_icon_%282023%29.svg.png])](https://drive.google.com/file/d/1eImQk3Rzs72TdzvCpZW9NxX-8_UEuBv-/view?usp=sharing)

---

## 🚀 Como Rodar o Projeto

1. Clone o repositório:

```bash
git clone github.com/Rudyzinho/alcool-ou-gasolina-estendido.git
```

2. Abra o projeto no **Android Studio** (versão recomendada: Arctic Fox ou superior).

3. Instale as dependências do Gradle.

4. Conecte um dispositivo Android ou inicie um emulador.

5. Rode o app.

---

## 📌 Observações

* Para funcionamento completo do mapa, o app precisa de **permissão de localização**.
* Os dados são persistidos localmente no dispositivo via `SharedPreferences`.
* Cada posto salvo mantém o **percentual de referência usado** no momento da criação, garantindo consistência nas comparações históricas.



**Desenvolvedor:** Erik Oliveira
