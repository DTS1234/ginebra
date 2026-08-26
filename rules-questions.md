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

## Q4 — Cuando al que es mano le cae el rey · ✅ RESPONDIDA (2026-08-26)

> **La respuesta.** **Paga 1 y se reparte otra vez. A los demás no se les cobra nada.**
> Nadie cobra tampoco: la mano sencillamente no ha llegado a jugarse.
>
> Es lo que ya hacía el motor; queda fijado en
> `SettlementCalculatorTest.shouldChargeTheMaAloneWhenTheirOwnKingEndsTheHand`.

---

## Q5 — Cambiar de palo hasta que salga el rey · ✅ RESPONDIDA (2026-08-26)

> **La respuesta.** Hay que salir de un palo **que no se haya jugado todavía en esa mano**,
> no simplemente distinto del anterior. Con oros y copas ya jugados: *"tienes que jugar
> espadas o bastos si tienes"*.
>
> De ahí salen los dos límites: **si no tienes** ninguno de los palos que quedan, puedes
> repetir; y cuando ya se han sacado los cuatro palos la obligación se agota y vuelves a
> salir libre. Y en cuanto sale o se pone rey, se acabó la obligación.
>
> Implementado en `MoveValidator.validateLead`, con la lista de palos ya jugados que lleva
> la mano.

---

## Q1 — «Fer todo»: si se canta y no se hace · ✅ RESPONDIDA (2026-08-26)

> **La respuesta.** *"You lose 1 if you don't make it and you earn one if you make."*
> Hacerlo vale **+1**; cantarlo y no hacerlo cuesta **−1**.
>
> O sea que es una apuesta de verdad, y por tanto **hay que preguntárselo al jugador**: el
> programa ya no puede decidir por él. Al llegar a cinco basas llevándolas todas, la partida
> se para y el que va elige «voy a por el todo» o «me quedo con la mano».

---

## Q3 — Primeras y estuche al perder · ✅ RESPONDIDA (2026-08-26)

> **Las primeras: las del que va.** Si el bando que va hace primeras y gana, cobra 1 más;
> si hace primeras y aun así pierde, paga 1 más. **Las primeras de los contrarios no les dan
> nada a ellos.**
>
> **El estuche: se cobra por ir, no por ganar.** Solo lo cobras **si vas** (*si vas*), y
> entonces se cobra **tanto si ganas como si pierdes** — y lo cobran **los dos** jugadores
> del bando que va, no solo el que lo lleva.
>
> **Y el estuche es del bando, no de un jugador.** Puede formarse **entre los dos**: uno
> lleva el dengue (espadilla + basto) y el otro la manilla. En ese caso el primero cobra el
> **dengue él solo**, y el estuche lo cobran **los dos**.
>
> El dengue, en cambio, es **de una sola mano**: nunca se junta entre compañeros.
>
> Con eso, la línea del libro *«Per guanyar i tindre l'estutxe, 3 cadegú (si n té el dengue,
> 4)»* sale exacta — y además se entiende **por qué** lleva ese paréntesis: el bando puede
> tener el estuche sin que **nadie** tenga el dengue (uno lleva espadilla y manilla, el otro
> el basto).

---

## La única duda que queda

### El estuche en la tabla de lo que **se paga**

**Lo que no cuadra.** En la tabla de pagos del libro hay dos líneas que parecen decir que el
estuche también sube lo que paga el que pierde:

- *«Si perden i tenen l'estutxe, 3 cadegú»* — cuando lo normal por perder son 2.
- *«Si perden primeres i estutxe, 4 cadegú»*

Pero si el estuche **se cobra por ir**, gane o pierda, entonces al que pierde llevándolo le
saldría **−2 de la mano +1 del estuche = −1**, no −3.

#### Versión corta

> Una última duda de la ginebra.
>
> Ya nos habéis dicho que **el estuche se cobra por ir**, se gane o se pierda, y que lo
> cobran los dos del bando que va.
>
> Pero en el libro, en la lista de lo que **se paga**, pone *«si pierden y tienen el
> estuche, 3 cada uno»*, cuando lo normal por perder son 2.
>
> **¿Es que el estuche sube también lo que se paga al perder — o esa línea del libro está
> mal / se entiende de otra manera?**

#### Versión larga, con ejemplo

> **A** va y **B** le ha puesto el rey. **C, D y E** son los contrarios. **A** lleva
> espadilla, manilla y basto — el estuche. La mano acaba con A y B perdiendo.
>
> Por perder les tocaría pagar **2 cada uno**. Y por el estuche cobran **1 cada uno**, según
> nos habéis dicho, porque se cobra por ir.
>
> **¿Cuánto acaban pagando A y B?**
>
> 1. **1 cada uno** (los 2 de perder menos el 1 del estuche).
> 2. **2 cada uno** (el estuche se cobra aparte y no se descuenta de lo que se paga).
> 3. **3 cada uno**, como pone el libro — es decir, el estuche sube el pago *y además* se
>    cobra, o directamente no se cobra cuando se pierde.
>
> Y lo mismo con la línea *«si pierden primeras y estuche, 4 cada uno»*.

*Lo que hace el programa ahora:* la **opción 1** — pagan 2 de la mano y cobran 1 del
estuche, así que les queda −1 a cada uno. Es lo que se sigue de lo que nos dijisteis. Lo
preguntamos porque **todas las demás líneas del libro salen exactas** con las reglas que nos
habéis dado, y esta es la única que no cuadra.
