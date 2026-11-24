# 04 — Pinball: pixel art, apelo visual e mais elementos

**Feedback:** "pixel art também, precisa de apelo visual; faltam mais elementos".

**Hoje** (`games/pinball/`) a mesa é azul e lisa: 3 bumpers, 2 kickers, 4 estrelas, 2 flippers, e a bola cai do alto (não há lançador).

## O que fazer
**Visual**
- Mesa temática em pixel art, por exemplo "Parque de diversões":
  - arte de fundo no feltro;
  - bumpers com luzes que piscam ao serem atingidos;
  - trilhos metálicos e placar estilo painel.
- Partículas simples (estrelinhas) nos acertos.
- Respeitar as configurações de movimento reduzido (`currentMotion`).

**Elementos novos** (em `PinballEngine.kt`, com testes):
- **Lançador** na direita: segurar e soltar um botão grande "Lançar". A força é automática no Fácil.
- **Raias superiores (rollovers):** 3 raias com luz; acender as 3 dobra os pontos da próxima jogada (multiplicador ×2, mostrado no placar).
- **Alvos que caem (drop targets):** uma fileira de 3; derrubar todos dá bônus e reergue a fileira.
- **Uma rampa** simples (uma volta pela lateral) que vale pontos ao ser completada. Pode ser um trilho com entrada e saída fixas: a bola entra, percorre e sai no flipper.
- **Spinner** (opcional): dá pontos por volta.
- **Mantido:** tudo continua calmo, com bola lenta no Fácil e o salva-bola.

**Desempenho:** 60 fps num celular simples. A física continua em `Table.step` com subpassos; nada de alocar objetos por quadro.

**Testes:** `PinballTest` ganha os casos do lançador, das raias, dos alvos e da rampa. O teste "os flippers mantêm a bola em jogo mais tempo" continua passando.

## Pronto quando
- Há capturas novas (en e pt, com a bola em jogo).
- O lint e a suíte passam.
