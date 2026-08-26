# Preguntas para los jugadores de Tàrbena

Open questions about *es joc de ginebra* that the written rules (Juan Monjo Soliveres,
*«Es joc de ginebra»* — transcribed in `rules-source.md`) do not settle, written out so
they can be put to someone who actually plays.

Each has a **short version** to ask at the table and a **long version** with worked
examples, in case the short one gets a "depende".

---

## D-6 — ¿Dónde va el as en un palo que **no** es triunfo? · ✅ RESPONDIDA (2026-08-26)

> **La respuesta.** El as **cambia de sitio** según si su palo es triunfo o no.
>
> | | Orden, de mayor a menor |
> |---|---|
> | Oros **sin** ser triunfo | Rey, Caballo, Sota, **Rovell**, 2, 3, 4, 5, 6, 7 |
> | Oros **de** triunfo | *(Espadilla y Basto por encima)* 7 **manilla**, **Rovell**, Rey, Caballo, Sota, 2, 3, 4, 5, 6 |
>
> Igual para copas con la *carabassa*. Espadas y bastos no tienen as propio — son la
> espadilla y el basto — y sus cartas bajas van del 7 al 2.
>
> Coincide con lo que ya hacía el motor, así que no hubo que cambiar nada; queda fijado en
> `SpecCardOrderTest.shouldRankEveryColumnAsThePlayersStatedIt`.

**What we needed.** The book gives the order *within the trump suit*, where the ace beats
the king, but never the order inside a suit that is not trump. It only matters for **oros
and copas**, since the aces of espadas and bastos are the espadilla and the basto.

*The question as it was asked is kept below, both because the answer is easier to check
against the question that produced it, and as a template for the ones still open.*

### Versión corta

> Estamos escribiendo las reglas de la ginebra para hacer una versión online, y hay una
> cosa que el libro no dice.
>
> Cuando un palo **no** es triunfo — por ejemplo, los triunfos son bastos y alguien sale de
> oros — ¿qué orden llevan las cartas de oros?
>
> **¿El rovell (el as de oros) va por encima del rey, o va más abajo, entre la sota y el 2?**

### Versión larga, con ejemplos

> Una pregunta sobre la ginebra, para aclarar una regla.
>
> En el libro viene el orden de los triunfos de cada palo. Por ejemplo, cuando los triunfos
> son oros: espadilla, manilla (el 7), basto, **rovell (el as)**, rey, caballo, sota, el 2,
> el 3, el 4, el 5 y el 6. Ahí se ve claro que el as va **por encima del rey**.
>
> Lo que no viene es qué pasa con ese mismo as cuando **oros no son triunfo**.
>
> Pongamos que los triunfos son **bastos**. Sale uno de **oros** y los demás tienen que
> tirar de oros:
>
> 1. Uno echa el **rovell (as de oros)** y otro echa el **rey de oros**. **¿Cuál se lleva la
>    basa?**
> 2. Uno echa el **rovell** y otro echa la **sota de oros**. **¿Cuál gana?**
> 3. Uno echa el **rovell** y otro echa el **2 de oros**. **¿Cuál gana?**
>
> Y lo mismo con la **carabassa (el as de copas)** cuando las copas no son triunfo.
>
> Dicho de otra manera: el as de un palo que no es triunfo, **¿es de las cartas altas
> (por encima del rey), o es de las bajas (por debajo de la sota)?** ¿O es que no es lo
> mismo cuando el palo es triunfo que cuando no lo es?

### La mateixa pregunta, en valencià

Si es más natural preguntarlo en la lengua del pueblo:

> Una pregunta sobre sa ginebra, per aclarir una norma.
>
> En es llibre ve l'ordre des trumfos de cada pal. Per exemple, quan es trumfos són oros:
> espadilla, manilla (el 7), basto, **rovell (l'as)**, rei, cavall, sota, el 2, el 3, el 4,
> el 5 i el 6. Aquí es veu clar que l'as va **per damunt des rei**.
>
> Lo que no ve és qué passa amb aqueix mateix as quan **oros no són trumfos**.
>
> Posem que es trumfos són **bastos**. Un tira **oros** i es altres han de tirar des mateix
> pal. Si un tira el **rovell** i un altre tira el **rei d'oros**, **qui fa sa basa?** I si
> un tira el rovell i un altre sa **sota d'oros**?
>
> És a dir: l'as d'un pal que no és trumfo, **va en ses altes o va en ses baixes?**

### La respuesta que dieron

La segunda opción, y además con el detalle de que en triunfo el as sube por encima del rey:
el as tiene **dos posiciones** según si su palo es triunfo o no. Es lo que ya hacía el
motor, así que no hubo que tocar código — solo fijarlo con un test.

---

## Q2 — Los cuatro reyes: ¿se acaba la mano, o puede jugarla? · ✅ RESPONDIDA (2026-08-26)

> **La respuesta.** *"If you wanna try to win, then you go."* El que recibe los cuatro reyes
> **elige**: se lleva los 4 y se acaba la mano, o va a soles y la juega — y en ese caso se
> queda los 4 **además** de lo que haga. Nadie más puede ir a soles contra ese reparto.
>
> Eso es lo que hace alcanzable el máximo de 13 que da el libro: 5 de ir a soles + 4 de los
> cuatro reyes + 1 de primeras + 2 del estuche + 1 de todo.
>
> Implementado: la ventana de soledad se abre reducida a ese jugador. Si pasa, cobra 4 y se
> acaba. Si declara, es una soledad normal con los 4 encima.

---

## Dudas que siguen abiertas

La implementación ya toma una decisión en cada una, así que se puede jugar sin resolverlas
— pero confirmarlas nos quitaría suposiciones. Cada una lleva un ejemplo concreto de mesa.

### 1. «Fer todo»: si se pide y no se hace, ¿pasa algo?

**Qué es «fer todo».** Hacer **las ocho basas**, todas. El libro solo dice: *«"Fer todo" ho
has de demanar quan tens cinc bases»* — hay que **cantarlo al llegar a cinco**. Vale 1
más.

**Ejemplo.** Tú vas (te han puesto el rey). Ganas las basas 1, 2, 3, 4 y 5 — las cinco
seguidas. Ya has ganado la mano: cobras tus 2. Pero como las llevas todas, cantas «todo» y
seguís jugando para intentar las ocho.

En la basa 6 un contrario se lleva una. Ya no hay todo.

> **¿Qué pasa entonces?**
> - (a) Nada: has ganado igual, cobras tus 2 y te quedas sin el punto del todo.
> - (b) Algo peor: por haberlo cantado y no hacerlo, pagas, o pierdes la mano.

*Lo que hace el programa ahora:* (a) — la victoria se mantiene y solo se pierde el punto.
De hecho, como cantarlo no cuesta nada, el programa **no pregunta**: si el que va lleva
todas las basas, sigue jugando solo; si pierde una, se para y cobra. Si la respuesta es
(b), habría que preguntárselo de verdad al jugador.

### 2. Perder llevando el estuche o habiendo hecho primeras: ¿quién sube el pago?

**El texto.** En la tabla de lo que **se paga**: *«Si perden i primeres, 3 cadegú»* y *«Si
perden i tenen l'estutxe, 3 cadegú»* — cuando lo normal por perder son 2.

**La duda.** *Primeras de quién.*

**Ejemplo.** A va, B le pone el rey. C, D y E son los contrarios. La mano acaba con A y B
perdiendo.

> - **Caso i:** A y B ganaron **las cuatro primeras basas** (hicieron primeras) y luego se
>   hundieron: perdieron las cuatro últimas. ¿Pagan 3 cada uno en vez de 2, por haber hecho
>   primeras aunque hayan perdido?
> - **Caso ii:** Fueron **C, D y E** los que hicieron las cuatro primeras. A y B pierden.
>   ¿Pagan 3 por las primeras **del contrario**?

Y lo mismo con el estuche: si **A** lleva espadilla + manilla + basto y aun así pierde,
¿paga 3 en vez de 2 por llevarlo?

*Lo que hace el programa ahora:* el caso **i** — lo que sube el pago es lo que hizo o
llevaba **el bando que va**, no el contrario. Es la única lectura con la que *«si perden i
**tenen** l'estutxe»* tiene sentido: «tenen» son los que pierden. (Detalle: al que pierde
con estuche le sale −3 de la mano pero +1 del dengue, porque el dengue siempre se cobra,
así que acaba en −2.)

### 3. Cuando al que es mano le cae el rey y se acaba la mano: ¿qué se paga?

**El texto.** *«Si es qui és mà li cau el rei s'acaba sa mà»*, y en la tabla de pagos *«Si
et cau el rei, 1»*.

**Ejemplo.** A es mano y ha hecho triunfos de oros. Sale alguien de **copas**. A tiene una
sola copa en la mano: **el rey de copas**. Como hay que servir el palo, A está obligado a
echarlo — no lo pone porque quiera, *le cae*. La mano se acaba ahí mismo.

> **¿Qué se liquida?**
> - (a) A paga 1 y ya está: nadie más cobra ni paga, se reparte de nuevo.
> - (b) A paga 1 **y** algo más — ¿los otros cuatro cobran algo del posso?
> - (c) Otra cosa.

*Lo que hace el programa ahora:* (a). A paga 1, nadie cobra nada (salvo quien lleve el
dengue, que se cobra siempre), y se reparte otra vez.

### 4. Cambiar de palo hasta que salga el rey: ¿respecto a qué?

**El texto.** *«Després has de tirar un altre pal fins que isca o posen rei»* — ya está
confirmado que es **obligatorio** cambiar de palo mientras no haya salido rey. Lo que falta
es respecto a qué se mide «un altre pal».

**Ejemplo.** Triunfos: bastos. Todavía no ha salido ningún rey.

> - **Basa 1:** el que sale echa **oros**.
> - **Basa 2:** el que sale ahora no puede repetir oros. Echa **copas**.
> - **Basa 3:** sigue sin salir rey. No puede repetir copas. **¿Puede volver a oros?**
>   - **Lectura A:** sí — solo está prohibido el palo de la basa **anterior**.
>   - **Lectura B:** no — oros ya se jugó, así que solo le quedan espadas o bastos.
>
> Con la lectura B, en la basa 5 ya se habrían jugado los cuatro palos y **no quedaría nada
> legal que echar**. Por eso creemos que es la A.

*Lo que hace el programa ahora:* la **lectura A**. Y si al que sale no le queda más que ese
palo, puede repetirlo — la obligación no puede dejar a nadie sin jugada.
