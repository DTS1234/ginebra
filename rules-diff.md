# Source rules vs. `spec.md` — differences and proposed fixes

Compares `spec.md` §2 (Functional Requirements — Game Rules) against `rules-source.md`,
the translated primary source (Juan Monjo Soliveres, *«Es joc de ginebra»*).

Every finding cites the source passage it rests on. Findings marked **[uncertain]** rest on
a passage the source leaves ambiguous or that was partly obscured in the photographs —
they are written up so the decision can be made, not so it can be implemented blind.

Nothing in the code has been changed. Section 3 lists the code work this implies, for a
follow-up.

---

## 1. What the source confirms

These were right and should be left alone.

| Spec | Confirmed by |
|---|---|
| Exactly 5 players | §1 *"Sa partida la componen cinc jugadors."* |
| 40-card Spanish deck, 8 cards each | §1 *"una baralla de quaranta cartes espanyola […] li tocaran huit cartes"* |
| The espadilla holder goes first **on the first hand only** | §4.1 *"A sa primera mà farà trumfos qui tinga l'espadilla."* |
| The one who goes names the trump suit | §4.2 *"Es qui és mà fa trumfos des pal que més en té o que li convé."* |
| The going side needs **5 basas** | §4.1 *"Guanya sa partida es qui va i fa 5 bases."* |
| Partnership is formed by playing a **king** | §4.3 *"posar el rei"*; glossary *"Posar el rei. Voler aidar an es qui va."* |
| A player with no king cannot form the partnership | §4.3 *"Un jugador que no té rei no el pot posar."* |
| All four trump-order tables in spec §2.4, including the manilla switch (7 for copes/oros, 2 for espases/bastos) | §2 trump lists — they match the spec tables row for row |
| Follow suit if you can | §4.4 *"tots es altres han de tirar des mateix pal"* |
| Trumping when void is **optional** | §4.4 *"Si no en tens, pots «fallar», si vols."* |
| No obligation to overtake | Nothing in the source requires it; *fallar* is framed as a choice throughout |
| Any player may go alone, not only the *mà* | §4.1 *"o qui vaja a soles, encara que no siga mà"* |
| In a soledad the lone player names trumps but the *mà* still leads (spec §2.5) | Reconciles §4.1 with the glossary's *"Ser mà […] És es primer en jugar."* **[uncertain]** — see D-19 |

---

## 2. Differences

### D-1 — The settlement model is a pot, not player-to-player transfers · **critical**

**Source.** §3: the ***posso*** is an equal ante from every player, placed in the middle of
the table. All scoring language is *cobrar* (collect **from** it) and *pagar* (pay **into**
it). The stake is whatever the table agrees; if the posso runs out it is topped up in equal
parts; if it grows large and the session drags on it is **divided equally among the
players**.

**Spec.** §2.2 gives each player 20 coins and §2.6 moves coins directly between winners and
losers — winners `+2`, losers `-2`; soledad `±3 with each of the four others, 12 in total`.

**Why it matters.** The source's tables are **not zero-sum and cannot be made so.** A going
pair that loses pays 2 each (4 into the pot) while the three opponents collect 1 each (3
out of it). The pot absorbs the difference — that is what it is for. Any implementation
built on direct transfers will either fail to reproduce the published numbers or silently
invent different ones.

**Consequence.** `Game.INITIAL_COINS = 20`, `WIN_COINS`, `LOSE_COINS`, `DRAW_COINS` and
`isAnyPlayerBankrupt` all encode the wrong model.

---

### D-2 — There is no draw · **high**

**Source.** The outcome is binary: the going side makes 5 basas or it does not. §5 prices
the failure directly — *"Si es que van fan menys de cinc bases, 1"* (each opponent collects
1) — and §6 has the going pair paying. No passage anywhere describes a tie.

**Spec.** §2.6 has *"Draw: each player receives 1 coin (unclear when draw occurs — TBD)"*.
`design.md` §5.1 makes it concrete: *"Round ends when either team reaches 5 basas (WIN) or
all 8 basas played with 4-4 score (DRAW)"*, and `Round.checkForRoundEnd` returns
`RoundResult.Draw` at eight basas.

**Correct rule.** A 4–4 finish is the going side **failing to make five**. They pay; the
opponents collect. The `DRAW` outcome should not exist.

The spec's own open question — *"Draw condition: When/how does a draw occur in a round?"* —
is answered: it does not.

---

### D-3 — The opposing side wins at **4** basas, not 5 · **high**

**Source.** §4.1 — only the going side has a target of 5. The opponents' job is to prevent
it: §4.7 *"Es contraris procuraran que això no ocórrega."*

**Spec.** §2.1 *"Be part of the team that wins 5 basas first"*; §2.6 *"Round ends
immediately when one team wins 5 basas"*.

**Correct rule.** Eight basas, two sides. Once the opponents hold **4**, the going side can
reach at most 4 and has already failed — the hand is decided there. Requiring 5 from the
opponents produces a phantom 4–4 state that the source has no name for, which is exactly
where the spurious draw in D-2 comes from.

---

### D-4 — The hand does not automatically stop at five basas · **medium**

**Source.** §4.8 *"«Fer todo» ho has de demanar quan tens cinc bases."* — todo is **called
at five**, and play continues for all eight.

**Spec.** §2.6 *"Round ends immediately when one team wins 5 basas. Remaining cards/basas
are not played."*

**Correct rule.** At five basas the going side chooses: bank the win, or call *todo* and
play on for the extra point. Reaching five is the decision point, not automatically the
end.

**[uncertain]** The source does not say what happens if *todo* is called and then missed —
whether the win stands minus the point, or something worse. Needs confirmation.

---

### D-5 — When a trump is led, everyone must trump · **high**

**Source.** §4.5, four sentences, reducible to one rule: **when a trump is led you must
play a trump if you hold one, except that you may withhold a special card that outranks
the card led.**

| Card led | Must play a trump | May withhold |
|---|---|---|
| Espadilla | everyone | nothing |
| Manilla | everyone | espadilla |
| Basto | everyone | espadilla, manilla |
| Any other trump | everyone | espadilla, manilla, basto |

**Spec.** §2.3 has no trump-lead obligation at all. It describes only following the led
suit, with *fallar* and *refallar* as free choices.

**Consequence.** `MoveValidator` gets one case right by accident and one wrong. It excludes
special cards from the "do you hold the led suit" check, which correctly lets you withhold
the basto when a plain trump is led — but it applies the same exclusion when the
**espadilla** is led, where the source allows no exemption and the basto must be played.
It also never treats the manilla as withholdable, because `Card.isSpecial()` covers only
the espadilla and the basto.

---

### D-6 — The rank of a plain suit's ace is unverified · **info**

**Source.** §2 gives the order **within the trump suit only**. There the ace sits *above*
the king: `… Basto, Rovell (as), Rei, Cavall, Sota …`.

**Spec.** §2.4 puts the ace *below* the sota in non-trump suits: *"King > Horse > Sota >
Ace (if present)"*, and the four tables agree.

**Status.** The source neither confirms nor contradicts this. The asymmetry — ace above the
king in trump, below the sota outside it — is unusual enough to be worth checking with a
player before it is treated as settled. **No change proposed**; flagged so it is not
mistaken for verified.

---

### D-7 — "Espadilla and basto can be played at any time" is not supported · **medium**

**Spec.** §2.3: *"They can be played at any time (even when player has the led suit)."*

**Source.** Says no such thing. What it says is the opposite in shape: the espadilla and
basto **are trumps** (§2), and playing a trump when a plain suit is led is *fallar*, which
§4.4 permits only *"si no en tens"* — when you are void. The discretion the source grants
these cards is the discretion to be **withheld** when a trump is led (§4.5), not to be
played out of suit.

**Proposed reading.** The espadilla and basto follow ordinary trump rules for *playing*:
you may not renege on a plain-suit lead to play one. Their privilege is exemption from the
trump-lead obligation.

**[uncertain]** This is inference from what the source permits, not a passage that settles
it. It changes legality of moves, so it needs confirmation before it reaches
`MoveValidator`.

---

### D-8 — It is the ***dengue***, and it is always collected · **medium**

**Source.** §2 *"Es diu «dengue» […] quan té l'espadilla i el basto"*; §6 closes with
***"El dengue sempre es cobra."*** — the dengue is always collected, win or lose.

**Spec.** §2.6 calls it **"Duende"** and lists it under *"Special Combinations Bonus
(awarded at end of round to winning team/player)"*.

Two errors: the name, and the condition. The dengue is worth 1 to whoever holds it,
**regardless of the outcome** — it is the one item that never appears on the paying side.
Spec §2.6's *"Estuche"* should likewise be ***estutxe***.

---

### D-9 — Bonus values and stacking are wrong · **medium**

**Source.** §5.1 — the published table is one **base** plus **+1 increments**:

| Base (exclusive) | | Increment | |
|---|---|---|---|
| Helped (king put on you), each | 2 | Primeres | +1 |
| King put on yourself | 4 | Dengue | +1 |
| Going alone | 5 | Estutxe | +1 *on top of* the dengue |
| Dealt four kings | 4 | Todo (implies primeres) | +1 |

This reproduces all eighteen printed rows, including the author's own arithmetic for the
maximum: `5 (a soles) + 4 (four kings) + 1 (primeres) + 2 (estutxe, dengue included) + 1
(todo) = 13`.

**Spec.** §2.6: duende `+1`, estuche `+2`, to the winning side only.
`Game.calculateBonus` takes `max(estuche, duende)` across winners.

**Errors.** The estutxe is `+1` beyond the dengue, not a flat `+2` replacing it — a holder
scores both, for 2. They **add**, they are not alternatives, and `max()` is the wrong
combinator. And the dengue is not conditional on winning (D-8).

---

### D-10 — *Fer primeres* is missing entirely · **medium**

**Source.** §4.7, and the glossary: winning the **first four basas in a row**, worth +1 to
the going side, and something the opponents actively play to prevent.

**Spec.** No mention. This is a scoring event the engine must detect: the first four basas
all won by the same side.

---

### D-11 — *Fer todo* is missing entirely · **medium**

**Source.** §4.8 and the glossary: winning **all eight basas**, worth +1, and **it must be
called when the caller has five**.

**Spec.** No mention. Needs a declaration point, not just a detector — see D-4.

---

### D-12 — Four kings in one hand ends it immediately · **medium**

**Source.** §4.8: *"Si a un li venen es quatre reis, cobra quatre, i s'acaba sa mà. Si en es
mateix temps un altre jugador vullguera anar a soles, no podria. En aquest cas sols pot
anar a soles es qui té es quatre reis."* Also §4.4: *"Si a un jugador li venen es quatre
reis, s'acaba sa mà."*

**Spec.** No mention.

**Rule.** A player dealt all four kings collects **4**, the hand ends before play, and the
deal **pre-empts any soledad declaration** — only the four-king holder may go alone.
Ordering matters: this must be checked at deal time, ahead of the soledad window.

---

### D-13 — *Posar-se el rei* — putting the king on yourself — is missing · **medium**

**Source.** §4.7: *"Un jugador es pot posar ell mateixa el rei, si veu que pot fer ses 5
bases."* Priced in §5 (`4` to win, `5` with primeres) and §6 (`4` to lose, `5` with
primeres).

**Spec.** No mention.

**Rule.** A distinct way to play alone, entered **during** the hand by playing your own
king rather than declared before it, and settled at 4 rather than the 5 of *anar a soles*.
Ginebra has **two** solo modes, not one.

---

### D-14 — "The player who goes plays the first king, and can stop the game" conflates two rules · **medium**

**Spec.** §2.3: *"Special case: If the player who 'goes' plays the first King themselves,
they can choose to stop the game."*

**Source.** Two separate rules, neither of which is that:

1. **Voluntarily** — §4.7, that is *posar-se el rei*: they play on, alone, for 4 (D-13).
2. **Forced** — §4.3: *"Si es qui és mà li cau el rei s'acaba sa mà."* If the *mà*'s king
   is **forced out**, the hand ends. Not a choice, and the trigger is compulsion, not the
   act of playing a king.

The distinction the spec is missing is *choosing* to play your king versus *"et cau"* —
having it dragged out of you by the follow-suit obligation.

**Consequence.** `TeamResolver` returns `Optional.empty()` for this case and defers, so the
*next* king played by someone else forms the teams instead — an outcome neither source rule
allows.

---

### D-15 — *Caure el rei* — the forced king — is missing · **medium**

**Source.** §4.3 *"Si et cau el rei, te l'has de jugar forçosament."*; glossary *"Caure el
rei. Es diu quan t'has de jugar el rei forçós."*; §6 prices it at **1** paid.

**Spec.** No mention.

**Rule.** When following suit leaves you no choice but your king, you must play it, it
forms the partnership as if you had chosen to, and **you pay 1** for the accident. §4.3
notes this is common: *"Moltes voltes poses el rei sense voler perquè et cau."*

---

### D-16 — The *"es primer rei aida"* call is missing · **low**

**Source.** §4.3 and the glossary: the one who goes, holding a *rei pelat* (a bare king,
§7) that may fall, may call **"es primer rei aida"** to have a king put on them as early as
possible.

**Spec.** No mention. A declaration the going player can make, visible to the table.

---

### D-17 — The leader may be obliged to change suit until a king appears · **low, [uncertain]**

**Source.** §4.4: *"Després has de tirar un altre pal fins que isca o posen rei."*

**Reading.** Most plausibly: the leader must lead a **different suit each basa** until the
partnership is revealed — flushing out the king. It could also be read as a restatement of
the discard option when void.

**Spec.** No mention. Do not implement without confirmation; it constrains legal moves.

---

### D-18 — Soledad settles at 5, not 12 · **medium**

**Source.** §5 *"Si vas a soles i guanyes, 5"*; §6 *"Si vas a soles i perds, 5"*. Each
opponent collects **1** when the lone player fails, **2** if they were held under four
basas.

**Spec.** §2.6: *"Receives 3 coins from each of the 4 other players (12 coins total)"*.

Wrong figure, and wrong mechanism — see D-1. Note also that the source never uses the word
*"soledad"*; it is ***anar a soles***.

---

### D-19 — Soledad turn order: the source backs the spec · **info, [uncertain]**

**Source.** §4.1 says a lone player makes trumps *"encara que no siga mà"* — even if not
*mà*; the glossary says the *mà* *"és es primer en jugar"*. Together: the lone player names
trumps, the *mà* still leads.

**Spec.** §2.5 already says exactly this.

**Status.** The spec is **correct** and `PROGRESS.md`'s known gap #1 —
`Round.withSoledadDeclared` overwriting `playerWhoGoes` — is a genuine implementation bug,
now with source backing. The disabled `SoledadRoundRulesTest.TurnOrder` tests encode the
right behaviour.

---

### D-20 — Cards are dealt four at a time · **low**

**Source.** §1: *"Ses cartes es repartixen de quatre en quatre."*

**Spec.** §2.2 gives the count but not the manner. Immaterial to a shuffled server-side
deal; worth recording so a future dealing animation gets it right.

---

### D-21 — The ace of copes is the ***carabassa***, not the *rovell* · **low, documentation**

**Source.** §2 and glossary: **rovell** = ace of *oros*, **carabassa** = ace of *copes*.

**Spec.** §2.4's copes-trump table has *"As de Copes (Carabassa)"* — correct. The other
three tables label the same card *"As de Copes (Rovell)"* — wrong in all three.

---

### D-22 — Four of spec §2.8's open questions are answered · **info**

| Open question | Answer from the source |
|---|---|
| *What happens when a player runs out of coins?* | The question dissolves — there are no per-player balances. If the **posso** runs out and players want to continue, it is topped up in equal parts by agreement (§3). |
| *Is there an overall game winner, or just continuous rounds until players quit?* | No overall winner. Play continues by agreement; when it ends, or when the posso has grown too large, it is **divided equally** (§3). |
| *Draw condition: when/how does a draw occur?* | It does not — D-2. |
| *Initial pot contribution: how many coins does each player contribute?* | Whatever the table agrees, **equal for everyone**, and changeable by common agreement mid-session (§3). |

Still open, and **not** answered by the source: multiple simultaneous soledad declarations
(other than the four-kings pre-emption of D-12), disconnection handling, players joining or
leaving between hands, maximum session length, and the exact timing of the soledad
declaration relative to trump selection.

---

## 3. Proposed fixes

### 3.1 To `spec.md` — do these first

Ordered so that the model-level changes land before the details that depend on them.

1. **§2.6 — replace the coin model with the posso** (D-1). An equal ante per player into a
   pot; settlements are collections from and payments into it, not transfers between
   players; top-up and equal division by agreement. Drop the 20-coin starting balance from
   §2.2.
2. **§2.6 — delete the draw** (D-2), and strike the corresponding open question in §2.8.
3. **§2.1 / §2.6 — restate the objective asymmetrically** (D-3): the going side needs 5
   basas; the opposing side wins by holding them to 4 or fewer, which is decided the moment
   the opponents take their 4th.
4. **§2.6 — five basas is a decision point, not the end** (D-4): bank the win, or call
   *todo* and play all eight.
5. **§2.3 — add the trump-lead obligation** (D-5) as the unified rule plus its four cases.
6. **§2.6 — replace the bonus section with the base-plus-increment table** (D-9), rename
   *Duende* → *dengue* and *Estuche* → *estutxe*, and record that the dengue is collected
   win or lose (D-8).
7. **§2.6 — correct soledad to 5/5** and rename it *anar a soles* (D-18).
8. **Add the missing rules**: *fer primeres* (D-10), *fer todo* (D-11), four kings (D-12),
   *posar-se el rei* (D-13), *caure el rei* and its 1-coin penalty (D-15), *es primer rei
   aida* (D-16).
9. **§2.3 — replace the "can choose to stop the game" clause** with the two rules it
   conflates (D-14).
10. **§2.4 — fix the three tables that call the ace of copes the *rovell*** (D-21); add a
    note that the non-trump ace position is unverified (D-6).
11. **§2.2 — record that the deal is four at a time** (D-20).
12. **§2.8 — close the four answered questions** (D-22).
13. **Flag, do not yet change**: D-7 (espadilla/basto out of suit) and D-17 (leader changing
    suit). Both alter move legality and both rest on inference.
14. **Cite the source.** Point spec §2 at `rules-source.md`, and add it to `CLAUDE.md`'s
    context list alongside `spec.md` and `design.md`.

### 3.2 To the code — for a follow-up, after the spec settles

Grouped by what has to move together. The disabled acceptance tests listed in
`PROGRESS.md`'s Known Gaps stay valid; these are additional.

| # | Change | Files |
|---|---|---|
| C-1 | Replace balances with a posso: an ante at game start, `cobrar`/`pagar` against the pot, no bankruptcy end condition | `Game` (`INITIAL_COINS`, `WIN_COINS`, `LOSE_COINS`, `DRAW_COINS`, `applyCoins`, `isAnyPlayerBankrupt`), `GameStatePayload`, `GameStateMapper` |
| C-2 | Delete `RoundResult.Draw`; end the round when the opponents reach 4 or the going side reaches 5 | `RoundResult`, `Round.checkForRoundEnd`, `Game.calculateCoinChanges` |
| C-3 | Settlement as base + increments, replacing `max(estuche, duende)`; dengue paid win or lose | `Game.calculateBonus`, `Game.calculateCoinChanges` |
| C-4 | Trump-lead obligation with rank-based exemption | `MoveValidator`, and the client's mirror of it in `static/app.js` |
| C-5 | Detect *primeres*; add a *todo* declaration at five basas and detect the result | `Round`, `Game`, a new client→server message, `GameWebSocketController` |
| C-6 | Four kings at deal time — collect 4, end the hand, pre-empt soledad | `Game.start` / `startNextRound`, `Round` |
| C-7 | *Posar-se el rei* as a distinct outcome; distinguish a chosen king from a forced one, end the hand when the *mà*'s king is forced, and charge 1 for *caure el rei* | `TeamResolver`, `Round`, `Game` |
| C-8 | *"Es primer rei aida"* declaration and its broadcast | `Round`, `GameWebSocketController`, `ServerMessage` |
| C-9 | Soledad at 5/5 against the posso, on top of the existing Known Gap #1 work | `Game.calculateCoinChanges`, `Round` |
| C-10 | Update the help panel's rules text and card-order display to match | `static/app.js`, `static/index.html` |
| C-11 | Extend `SpecCardOrderTest` with the source's four trump lists, so the tables are pinned to the source and not only to the spec | `game/domain/service/SpecCardOrderTest` |

**Deliberately not proposed:** D-6, D-7 and D-17 stay out of the code until someone
confirms them. D-17 in particular would reject moves that are legal today.
