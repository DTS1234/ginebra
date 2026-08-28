# What a hand pays — the players' correction

The settlement in `spec.md` and in the engine was built from the book (`rules-source.md`
§5 and §6) plus a first round of answers from the players on **2026-08-26**. One of those
answers was wrong, and on **2026-08-27** the same source corrected it, unprompted:

> **Lo de cobrar no está bien.** Ahora te lo pongo como es.

This file is the corrected account. Where it disagrees with `rules-source.md`'s reading or
with the 2026-08-26 answers, **this wins** — it is the more recent testimony, and unlike
the earlier reading it reproduces every printed row of both of the book's tables.

---

## 1. What was said

Verbatim, in the order it arrived.

> Si el que va i el que ha puesto rey hacen 5 o más, cobran 2.
> Sino hacen más de 4 pagan 2.

> Si los 3 que no van hacen 5 cobran 1 (i los 2 que iban pagan 2).

> Si te cae el rey cuando vas, puedes elegir si quieres seguir (si tienes cartas buenas
> para hacer 5) o parar si no lo tienes bueno.

> En la primera opción, cobras 4 si haces 5 o pagas 4 si no haces más que 4.

> En la segunda, solo pagas 1.

> Soledad se cobran 5 si haces 5 basas, i si no, pagas 5 i todos los otros cobran 1.

> Además, si un jugador tiene en su mano el dingue (as de espadas i as de bastos), siempre
> cobra una.

> Si los que van tienen l'estuche entre los dos — as de espadas, la manilla de triunfo, i
> as de bastos — cobran 1 más si hacen 5 o pagan 1 más si no llegan a hacer 5.

> I si hacen primeras (hacer las primeras cuatro basas), los que van cobran 1 más si hacen
> 5 o pagan 1 más si se quedan en 4.

**Translation.**

- If the one who goes and the one who put the king make **5 or more**, they collect **2**
  each. If they do not make more than 4, they pay **2** each.
- If the **3 who are not going** make 5, they collect **1** each (and the 2 who went pay 2
  each).
- If **your own king falls while you are going**, you choose: carry on (if you have the
  cards to make 5) or stop.
  - Carrying on: **collect 4** if you make 5, **pay 4** if you make no more than 4.
  - Stopping: **you pay 1**, and that is all.
- **Soledad**: collect **5** for making 5 basas; otherwise **pay 5**, and **all four
  others collect 1** each.
- A player holding the **dengue** (ace of espadas + ace of bastos) in their hand **always**
  collects 1.
- If the going side holds the **estutxe between the two of them** — espadilla, manilla of
  trump, basto — they collect **1 more** for making 5, or **pay 1 more** if they fall
  short.
- If they make **primeres** (the first four basas), the going side collects **1 more** for
  making 5, or **pays 1 more** if they stop at 4.

---

## 2. What changed

**The estutxe is a stake, not a fee.** The 2026-08-26 answer had it collected *for going*
— by both of the going pair, win or lose. It is not: it is **signed by the result**, +1
each on a win and −1 each on a loss, exactly like primeres. What survives from the earlier
answer is that it belongs to the **side, not a player**: one partner's espadilla and basto
with the other's manilla is an estutxe just the same.

**Your own king falling is a decision, not an accident.** The engine decided between
"you put your own king" (±4) and "the king fell, hand over" (−1) by asking whether the
player had a legal alternative. They are not two situations — they are two answers to the
same question, and the player picks. There is a window to open here, like the one for
*fer todo*.

Everything else stands: base 2 helped, 4 for your own king, 5 alone; primeres ±1; the
dengue a flat +1 to whoever holds it, on either side, win or lose; four kings 4; todo +1
made and −1 called and missed; the opposing side collects 1 when the going side fails.

---

## 3. The model

One **base**, signed by the result, plus **increments**:

| Base — one only, per player of the going side | Win | Lose |
|---|---|---|
| Helped — a king was put on you | +2 | −2 |
| You carried on after your own king fell | +4 | −4 |
| You went alone (*a soles*) | +5 | −5 |

| Increment, to each player of the going side | Win | Lose |
|---|---|---|
| *Primeres* — the first four basas | +1 | −1 |
| *Estutxe* — espadilla, manilla, basto, held **between the side** | +1 | −1 |

Outside the stake, never negated by the result:

| | |
|---|---|
| *Dengue* — espadilla + basto **in one hand** | **+1**, any player, any outcome |
| Four kings dealt to one player | **+4** |
| *Todo* — all eight basas | **+1** made, **−1** called and missed |
| Stopping when your own king falls | **−1**, and nobody else pays or collects |
| Each opponent, when the going side fails | **+1** (2 if the going side was held under 4) |

Everything is against the **posso**, never player to player.

---

## 4. Checked against the book

Every row of `rules-source.md` §5 and §6, priced by the model above.

### §5 — what you collect

| Book | Model | |
|---|---|---|
| Si tens el dengue — 1 | dengue 1 | ✓ |
| Si tens l'estutxe i vas — 2 | dengue 1 + estutxe 1 | ✓ |
| Si et posen el rei i guanyes — 2 each | base 2 | ✓ |
| Per guanyar i fer primeres — 3 each | 2 + 1 | ✓ |
| Per guanyar i tindre l'estutxe — 3 each (4 with the dengue) | 2 + 1; the holder +1 | ✓ |
| Per guanyar, estutxe i primeres — 4 each (5 with the dengue) | 2 + 1 + 1; the holder +1 | ✓ |
| Per «fer todo» — 1 | todo 1 | ✓ |
| Per tindre es quatre reis — 4 | 4 | ✓ |
| Si et poses el rei i guanyes — 4 | base 4 | ✓ |
| …i primeres — 5 | 4 + 1 | ✓ |
| Si vas a soles i guanyes — 5 | base 5 | ✓ |
| …i primeres — 6 | 5 + 1 | ✓ |
| …primeres i dengue — 7 | 5 + 1 + 1 | ✓ |
| …i l'estutxe — 8 | 5 + 1 + 1 + 1 | ✓ |
| …i todo — 9 | 5 + 1 + 1 + 1 + 1 | ✓ |
| Si vas a soles, dengue, estutxe i todo — 9 | 5 + primeres 1 + 1 + 1 + 1 (todo implies primeres) | ✓ |
| Si tens es 4 reis, primeres, estutxe i todo — 13 | 5 + 4 + 1 + (dengue 1 + estutxe 1) + 1 | ✓ |
| Si es que van fan menys de cinc bases — 1 | opponents' award | ✓ |
| Si es que va a soles paga — 1 | same | ✓ |

### §6 — what you pay

| Book | Model | |
|---|---|---|
| Si et cau el rei — 1 | stopping | ✓ |
| Si t'aiden i perds — 2 each | −2 | ✓ |
| Si perden i primeres — 3 each | −2 − 1 | ✓ |
| **Si perden i tenen l'estutxe — 3 each** | **−2 − 1** | ✓ **now** |
| **Si perden primeres i estutxe — 4 each** | **−2 − 1 − 1** | ✓ **now** |
| Si et poses el rei perds — 4 | −4 | ✓ |
| …i primeres — 5 | −5 | ✓ |
| Si vas a soles i perds — 5 | −5 | ✓ |
| El dengue sempre es cobra | flat, never negated | ✓ |

The two rows in bold are the ones that would not come out before. Under the old reading a
losing side holding the estutxe netted −1 where the book says 3; the correction is exactly
what closes the gap, which is good evidence it is right. **Both tables now reproduce in
full, with nothing fudged.**

That also settles the one question left open in `rules-questions.md` — no need to ask it.

---

## 5. Answered (2026-08-27)

**A round runs until one side has five basas** — either side, not just the going side.
Only *fer todo*, called by the going side at five with a clean sweep, carries it past
that. Eight basas can therefore finish **4-4**, with neither side reaching five.

That replaces what the engine did before, which was to end the hand the moment the
opposing side had four, on the reasoning that five was then out of the going side's reach.
It is out of reach, but the hand is played on anyway.

**At 4-4 the going side still pays.** *"Sino hacen más de 4 pagan 2"* — four is not more
than four. Nobody made their five, but the stake is on making five, so the going side
loses it. The opponents collect their 1 each, as they do whenever the going side falls
short.

**The choice after your own king falls is offered only when the king was forced** — when
the one who goes had no other legal card. Putting your own king down when you had an
alternative is a decision already made: you carry on, at ±4.

**A king forced out of anyone else costs nothing.** They become the helper against their
will and play the hand for ±2 like any helper. The 1 in the book's *"si et cau el rei"* is
the price of *stopping*, and only the one who goes can stop.

---

## 6. Where this and the book disagree

One sub-clause of the book does not survive the correction.

> En cobres 1 si es qui van fan menys de 4 bases.
> You collect 1 [more] if the going side makes fewer than 4 basas.

The book pays each opponent **2** rather than 1 when the going side is held under four.
But with the hand running to five, an opposing side that reaches five has necessarily held
the going side to three or fewer — so that bonus would apply every single time the
opponents win, and the flat 1 would never be paid at all. The correction is explicit for
exactly that case: *"Si los 3 que no van hacen 5 cobran 1"*.

So the engine pays each opponent **a flat 1 whenever the going side fails to make five**,
and the doubling is gone. The book's headline row — *"Si es que van fan menys de cinc
bases, 1"* — still reads exactly as written, and so does the soledad sentence: *"si no,
pagas 5 i todos los otros cobran 1"*.

This is the one place where the two sources cannot both be right. If the players say the
doubling is still played, it is a two-line change.
