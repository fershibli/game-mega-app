# 05 — Estacionamento em pixel art

**Feedback:** "pixel art deixaria mais bonito".

**Pré-requisito:** o PR #66 já estar na `stag`, além do item 01.

## O que fazer
- **Carros vistos de cima, em pixel art:** sedã, hatch, picape e van para 2 casas; caminhão, ônibus e caminhão de mudança para 3 casas. Cada um tem cores variadas, faróis e para-brisa. O carro vermelho é um sedã de destaque e mantém a seta.
- **Pátio em pixel art:** asfalto com textura, vagas pintadas, guarita e cancela na saída da direita, plantinhas e cones na moldura.
- **Animações calmas:** o carro desliza ao soltar, e no fim o vermelho sai pela cancela, que levanta.
- **O que não muda:** `ParkingEngine.kt`, o arraste e o `ParkingUiTest` (a geometria `LOT_MARGIN` pode mudar; atualize o teste se mudar).

## Pronto quando
- Há capturas novas (Médio en e Fácil pt).
- O lint e a suíte passam.
