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

## Dudas que siguen abiertas

Quedan dos. La implementación ya toma una decisión en cada una, así que se puede jugar sin
resolverlas — pero confirmarlas nos quitaría suposiciones.

---

### Q1 — «Fer todo»: si se canta y no se hace, ¿pasa algo?

**Lo que no sabemos.** El libro dice que el todo *«ho has de demanar quan tens cinc bases»*
— que hay que cantarlo al llegar a cinco basas — y que vale 1 más. Lo que no dice es qué
pasa si lo cantas y luego no lo consigues.

#### Versión corta

> Una duda de la ginebra, para la versión online.
>
> Cuando el que va llega a cinco basas y **canta «todo»**, y luego resulta que no las hace
> todas porque los contrarios le ganan una:
>
> **¿pasa algo por haberlo cantado, o simplemente ha ganado la mano y se queda sin el punto
> del todo?**

#### Versión larga, con ejemplo

> Una duda sobre el todo, para aclarar una regla.
>
> Pongamos esta mano. A va, y B le ha puesto el rey. A y B ganan las basas 1, 2, 3, 4 y 5,
> las cinco seguidas. Con eso ya tienen ganada la mano: cobrarían 2 cada uno.
>
> Pero como las llevan **todas**, A canta «todo» y se sigue jugando para intentar las ocho.
>
> En la basa 6 los contrarios se llevan una. Ya no puede haber todo.
>
> **¿Qué pasa entonces?**
>
> 1. **¿Ganan igual y cobran sus 2**, y lo único que pierden es el punto del todo?
> 2. **¿O se paga algo por haberlo cantado y no hacerlo?** ¿Cuánto? ¿Y quién lo cobra?
> 3. **¿O incluso se pierde la mano** aunque tuvieran las cinco basas?
>
> Y una pregunta añadida, porque nos hace falta para el programa:
>
> **¿Es obligatorio cantarlo?** Es decir, si el que va llega a cinco basas llevándolas todas
> y **no dice nada**, ¿se acaba la mano ahí y cobra sus 2? ¿O se sigue jugando de todas
> formas y si las hace todas cobra el punto igualmente?

*Por qué lo preguntamos:* ahora mismo el programa **no pregunta nada**. Si el que va lleva
todas las basas, sigue jugando solo; en cuanto pierde una, se para y cobra lo que le toque.
Lo hicimos así porque, si cantarlo no cuesta nada, cantarlo siempre sale a cuenta y la
pregunta sobra. Si resulta que **sí** cuesta algo, entonces hay que preguntárselo de verdad
al jugador cuando llegue a cinco.

---

### Q3 — Perder llevando el estuche, o habiendo hecho primeras: ¿quién sube el pago?

**Lo que no sabemos.** En la tabla de lo que **se paga** vienen estas dos líneas: *«Si
perden i primeres, 3 cadegú»* y *«Si perden i tenen l'estutxe, 3 cadegú»* — cuando lo normal
por perder son 2 cada uno. No queda claro **de quién** son esas primeras.

#### Versión corta

> Una duda de la ginebra, para la versión online.
>
> En la lista de lo que se paga pone que si pierden y hay primeras se pagan **3** en vez de
> 2. **¿Las primeras de quién — las del que va, o las de los contrarios?**
>
> O sea: el que va, **¿paga más por haber hecho él las primeras y perder igualmente, o paga
> más porque se las han hecho a él?**

#### Versión larga, con ejemplo

> Una duda sobre lo que se paga al perder.
>
> Somos cinco: **A** va y **B** le ha puesto el rey. **C, D y E** son los contrarios. La
> mano acaba con A y B perdiendo — se quedan sin las cinco basas. Lo normal sería que
> pagaran 2 cada uno.
>
> **Caso 1.** A y B ganaron **las cuatro primeras basas** — hicieron primeras — pero luego
> se hundieron y perdieron las cuatro últimas.
> **¿Pagan 3 cada uno en vez de 2, por haber hecho ellos las primeras aunque hayan perdido?**
>
> **Caso 2.** Las cuatro primeras las hicieron **C, D y E**, y A y B pierden.
> **¿Pagan A y B los 3 por las primeras de los contrarios?**
>
> Y lo mismo con el estuche:
>
> **Caso 3.** **A** lleva espadilla, manilla y basto — el estuche — y aun así pierden.
> **¿Paga A 3 en vez de 2 por llevar el estuche y no haber sacado la mano?** ¿Y B, que no lo
> lleva, paga 2 o 3?

*Por qué lo preguntamos:* ahora mismo el programa hace el **caso 1** — lo que sube el pago
es lo que hizo o llevaba **el bando que va**, no el de los contrarios. Nos pareció la única
lectura con la que *«si perden i **tenen** l'estutxe»* tiene sentido, porque «tenen» son los
que pierden. Pero es una interpretación nuestra.
