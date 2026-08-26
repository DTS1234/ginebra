'use strict';

/*
 * The deck.
 *
 * The faces are drawn rather than fetched: no images to ship, no licence to honour, no
 * network. The four suits and the three figures are SVG symbols placed once in a hidden
 * sprite and instantiated with <use>, so a card face costs a handful of elements.
 *
 * They are drawn after a classic baraja española - the Fournier pattern everyone in the
 * village has on the table - rather than after a generic playing card. What makes the
 * difference is not one thing but five:
 *
 *   - the numbering: 1-7, 10, 11, 12, with no 8 and no 9;
 *   - full colour, over white, with everything outlined in ink;
 *   - the pip layouts, which are rows: a seven is 2-3-2, a six is 2-2-2;
 *   - figures that are people - a page, a rider, a crowned king - not letters;
 *   - the *pinta*: the break in the border frame that names the suit from a card only
 *     half out of the fan. Oros has an unbroken frame, Copas one break on each long
 *     side, Espadas two, Bastos three.
 *
 * Colours come from CSS classes on the symbol contents. That works through <use>
 * because the symbols themselves are in the document, and every instance is meant to
 * look the same anyway.
 */

const CARD_W = 100;
const CARD_H = 156;

/* A Spanish deck has no 8 or 9, and the corner index is the card's number. */
const RANK_NUMERAL = {
    AS: '1', DOS: '2', TRES: '3', CUATRO: '4', CINCO: '5',
    SEIS: '6', SIETE: '7', SOTA: '10', CABALLO: '11', REY: '12'
};

const FIGURE_OF = { SOTA: 'sota', CABALLO: 'caballo', REY: 'rey' };

const PINTA_BREAKS = { OROS: 0, COPAS: 1, ESPADAS: 2, BASTOS: 3 };

/** Where the breaks sit on the long edges, by how many there are. */
const PINTA_AT = { 0: [], 1: [78], 2: [56, 100], 3: [46, 78, 110] };

/** Pips per row for the number cards, and the y of each row. */
const PIP_ROWS = {
    DOS:    [[50], [50]],
    TRES:   [[50], [50], [50]],
    CUATRO: [[32, 68], [32, 68]],
    CINCO:  [[32, 68], [50], [32, 68]],
    SEIS:   [[32, 68], [32, 68], [32, 68]],
    SIETE:  [[32, 68], [26, 50, 74], [32, 68]]
};
const ROW_Y = { 2: [54, 102], 3: [44, 78, 112] };

/** An instance of a sprite symbol, centred on (cx, cy) and drawn `size` across. */
function usePip(id, cx, cy, size) {
    return '<use href="#' + id + '"'
        + ' x="' + (cx - size / 2) + '" y="' + (cy - size / 2) + '"'
        + ' width="' + size + '" height="' + size + '"/>';
}

/** The border frame, broken as many times as the suit's pinta calls for. */
function pinta(suit) {
    let gaps = '';
    for (const y of PINTA_AT[PINTA_BREAKS[suit]]) {
        gaps += '<rect class="pinta-gap" x="1" y="' + (y - 7) + '" width="10" height="14"/>'
            + '<rect class="pinta-gap" x="89" y="' + (y - 7) + '" width="10" height="14"/>';
    }
    return '<rect class="pinta" x="5" y="5" width="90" height="146" rx="6"/>' + gaps;
}

/** The whole face of one card as SVG markup. */
function cardFace(card) {
    const suitId = 'suit-' + card.suit.toLowerCase();
    let pips;

    if (FIGURE_OF[card.rank]) {
        // The figure fills the card the way it does on a real one, with the suit beside it.
        pips = usePip('fig-' + FIGURE_OF[card.rank], 50, 94, 96)
            + usePip(suitId, 50, 34, 30);
    } else if (card.rank === 'AS') {
        pips = usePip(suitId, 50, 80, 60);
    } else {
        const rows = PIP_ROWS[card.rank];
        const ys = ROW_Y[rows.length];
        const size = rows.length === 2 ? 35 : (card.rank === 'SIETE' ? 26 : 30);
        pips = rows
            .map((row, i) => row.map((x) => usePip(suitId, x, ys[i], size)).join(''))
            .join('');
    }

    // The second index is the first one turned about the middle of the card, which is how
    // a real one carries it: readable whichever way up you are holding the fan.
    const index = '<text class="card-index" x="11" y="23">' + RANK_NUMERAL[card.rank] + '</text>';

    return '<svg class="card-face" viewBox="0 0 ' + CARD_W + ' ' + CARD_H + '" aria-hidden="true">'
        + pinta(card.suit)
        + pips
        + index
        + '<g transform="rotate(180 50 78)">' + index + '</g>'
        + '</svg>';
}

/** A one-line suit mark for running text, buttons and headings. */
function suitIcon(suit) {
    return '<svg class="suit-icon" viewBox="0 0 100 100" aria-hidden="true">'
        + '<use href="#suit-' + suit.toLowerCase() + '" x="0" y="0" width="100" height="100"/>'
        + '</svg>';
}

function suitTag(suit) {
    return suitIcon(suit) + ' ' + SUIT_LABEL[suit];
}

/** The eight petals of a coin's rosette, which are tedious to write out by hand. */
function rosette() {
    let petals = '';
    for (let i = 0; i < 8; i++) {
        petals += '<ellipse class="c-red" cx="50" cy="28" rx="6" ry="11"'
            + ' transform="rotate(' + i * 45 + ' 50 50)"/>';
    }
    return petals;
}

/* ------------------------------------------------------------------ the symbols --- */

/** Oros: a gold coin, ink rim, a red rosette struck in the middle. */
const OROS = `
<symbol id="suit-oros" viewBox="0 0 100 100">
  <circle class="c-gold ol" cx="50" cy="50" r="46"/>
  <circle class="c-none ol-thin" cx="50" cy="50" r="38"/>
  ${rosette()}
  <circle class="c-gold ol-thin" cx="50" cy="50" r="10"/>
</symbol>`;

/** Copas: a lidded gold chalice with a red band, on a spread foot. */
const COPAS = `
<symbol id="suit-copas" viewBox="0 0 100 100">
  <circle class="c-gold ol-thin" cx="50" cy="8" r="6"/>
  <rect class="c-gold ol" x="20" y="14" width="60" height="10" rx="4"/>
  <path class="c-gold ol" d="M24 24 H76 C76 47 65 60 50 60 C35 60 24 47 24 24 Z"/>
  <rect class="c-red" x="28" y="30" width="44" height="9"/>
  <rect class="c-gold ol-thin" x="45" y="59" width="10" height="15"/>
  <ellipse class="c-gold ol-thin" cx="50" cy="66" rx="10" ry="5"/>
  <path class="c-gold ol" d="M30 86 C30 78 40 74 50 74 C60 74 70 78 70 86 Z"/>
  <rect class="c-gold ol" x="24" y="85" width="52" height="9" rx="4"/>
</symbol>`;

/** Espadas: a straight steel blade, gold guard and pommel, red grip. */
const ESPADAS = `
<symbol id="suit-espadas" viewBox="0 0 100 100">
  <path class="c-steel ol" d="M50 3 C57 20 59 40 57 60 H43 C41 40 43 20 50 3 Z"/>
  <path class="c-steel-dk" d="M49 12 H51 V58 H49 Z"/>
  <path class="c-gold ol" d="M15 64 C32 55 68 55 85 64 C68 73 32 73 15 64 Z"/>
  <rect class="c-red ol" x="43" y="71" width="14" height="15" rx="3"/>
  <circle class="c-gold ol" cx="50" cy="91" r="8"/>
</symbol>`;

/** Bastos: a lopped branch, knots and all, with the cuts still green. */
const BASTOS = `
<symbol id="suit-bastos" viewBox="0 0 100 100">
  <g transform="rotate(-24 50 50)">
    <ellipse class="c-green ol-thin" cx="24" cy="30" rx="9" ry="6" transform="rotate(-25 24 30)"/>
    <ellipse class="c-green ol-thin" cx="78" cy="54" rx="9" ry="6" transform="rotate(20 78 54)"/>
    <path class="c-brown ol" d="M41 93 C41 97 59 97 59 93 L64 34 C66 16 59 6 50 6 C41 6 34 16 36 34 Z"/>
    <path class="c-brown ol" d="M37 44 L18 32 L36 29 Z"/>
    <path class="c-brown ol" d="M62 62 L82 55 L61 49 Z"/>
    <ellipse class="c-brown-dk" cx="46" cy="26" rx="5" ry="3.5"/>
    <ellipse class="c-brown-dk" cx="54" cy="52" rx="4.5" ry="3"/>
    <ellipse class="c-brown-dk" cx="46" cy="76" rx="4" ry="2.5"/>
  </g>
</symbol>`;

/** The king: crown, grey beard, red robe over a gold-trimmed collar. */
const REY = `
<symbol id="fig-rey" viewBox="0 0 100 100">
  <path class="c-red ol" d="M12 100 C14 84 26 76 38 73 L50 82 L62 73 C74 76 86 84 88 100 Z"/>
  <path class="c-gold ol-thin" d="M38 73 L50 82 L62 73 L57 70 L50 76 L43 70 Z"/>
  <path class="c-skin ol" d="M35 36 H65 V56 C65 68 59 74 50 74 C41 74 35 68 35 56 Z"/>
  <path class="c-white ol-thin" d="M36 55 C36 70 42 78 50 78 C58 78 64 70 64 55 C60 63 40 63 36 55 Z"/>
  <circle class="c-ink" cx="43" cy="48" r="2"/>
  <circle class="c-ink" cx="57" cy="48" r="2"/>
  <path class="c-gold ol" d="M26 34 L30 10 L40 23 L50 6 L60 23 L70 10 L74 34 Z"/>
  <rect class="c-gold ol" x="24" y="32" width="52" height="10" rx="3"/>
  <circle class="c-red" cx="38" cy="37" r="2.6"/>
  <circle class="c-red" cx="50" cy="37" r="2.6"/>
  <circle class="c-red" cx="62" cy="37" r="2.6"/>
</symbol>`;

/** The page: a red cap with a feather, blue tunic, gold sash. */
const SOTA = `
<symbol id="fig-sota" viewBox="0 0 100 100">
  <path class="c-blue ol" d="M16 100 C18 85 30 77 40 74 L50 82 L60 74 C70 77 82 85 84 100 Z"/>
  <path class="c-gold ol-thin" d="M40 74 L50 82 L60 74 L55 71 L50 76 L45 71 Z"/>
  <path class="c-gold" d="M30 100 L44 84 L52 88 L38 100 Z"/>
  <path class="c-brown ol-thin" d="M32 44 C32 62 38 68 38 68 L62 68 C62 68 68 62 68 44 Z"/>
  <path class="c-skin ol" d="M36 36 H64 V54 C64 66 58 72 50 72 C42 72 36 66 36 54 Z"/>
  <circle class="c-ink" cx="44" cy="48" r="2"/>
  <circle class="c-ink" cx="56" cy="48" r="2"/>
  <path class="feather" d="M66 18 C82 2 94 10 88 26"/>
  <path class="c-red ol" d="M28 38 C28 18 38 9 50 9 C62 9 72 18 72 38 Z"/>
  <rect class="c-red ol" x="24" y="36" width="52" height="9" rx="4"/>
</symbol>`;

/** The rider: a horse in profile with a page up, which is what a caballo is. */
const CABALLO = `
<symbol id="fig-caballo" viewBox="0 0 100 100">
  <path class="c-brown-dk ol-thin" d="M16 62 C6 68 5 82 12 92 L20 88 C15 80 16 72 22 68 Z"/>
  <path class="c-brown ol" d="M24 74 L20 98 H28 L34 78 Z"/>
  <path class="c-brown ol" d="M72 74 L76 98 H68 L62 78 Z"/>
  <ellipse class="c-brown ol" cx="46" cy="66" rx="30" ry="15"/>
  <path class="c-brown ol" d="M30 76 L26 98 H34 L40 78 Z"/>
  <path class="c-brown ol" d="M66 76 L70 98 H62 L58 78 Z"/>
  <path class="c-brown ol" d="M64 62 C68 50 74 40 80 32 L78 22 L86 28 C92 32 94 40 92 46
                                 C90 54 84 60 76 64 Z"/>
  <path class="c-brown-dk" d="M70 40 C74 34 78 30 82 26 L78 22 L74 32 Z"/>
  <circle class="c-ink" cx="84" cy="36" r="2.2"/>
  <path class="c-blue ol" d="M38 56 C36 44 42 36 50 36 C58 36 62 44 60 56 Z"/>
  <path class="c-blue ol" d="M56 44 C62 42 68 44 72 50 L68 54 C64 50 60 49 56 50 Z"/>
  <circle class="c-skin ol-thin" cx="50" cy="28" r="9"/>
  <path class="c-red ol-thin" d="M39 22 C39 14 44 10 50 10 C56 10 61 14 61 22 Z"/>
</symbol>`;

function spriteMarkup() {
    return OROS + COPAS + ESPADAS + BASTOS + REY + SOTA + CABALLO;
}

function installSprite() {
    document.body.insertAdjacentHTML(
        'afterbegin',
        '<svg class="sprite" aria-hidden="true" focusable="false">' + spriteMarkup() + '</svg>'
    );
}
