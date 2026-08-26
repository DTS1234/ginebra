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

## Otras dudas, por si sale la conversación

Estas siguen abiertas. La implementación ya toma una decisión en cada una, pero confirmarlas
nos ahorraría suposiciones. Están detalladas en `rules-diff.md` §3.3.

1. **Fer todo.** El libro dice que se pide al llegar a cinco basas. Si se pide y luego
   **no** se hace, ¿pasa algo? ¿Se pierde solo el punto del todo, o se paga algo más?
2. **Los cuatro reyes.** Si a uno le vienen los cuatro reyes cobra cuatro y se acaba la
   mano. Pero, ¿puede en vez de eso **ir a soles** y jugar la mano, cobrando los cuatro
   además de lo que haga? (El máximo de 13 que da el libro parece decir que sí.)
3. **Si el que va pierde teniendo el estuche o habiendo hecho primeras**, ¿paga más por
   ello? ¿O lo que sube el pago son las primeras del **contrario**?
4. **Cuando al que es mano le cae el rey y se acaba la mano**, ¿qué se paga exactamente?
   ¿Solo el 1 del rey caído, o algo más?
5. **Cambiar de palo hasta que salga el rey.** Ya está confirmado que es obligatorio. Lo
   que falta: el que sale, ¿tiene que cambiar de palo respecto al **palo anterior**, o
   respecto a **todos los que ya se han jugado**? (Implementado como lo primero — lo
   segundo se vuelve imposible pasadas cuatro basas.)
