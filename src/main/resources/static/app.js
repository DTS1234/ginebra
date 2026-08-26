'use strict';

/*
 * Minimal play client for Ginebra.
 *
 * No build step and no dependencies: the STOMP framing below is hand-rolled over a
 * native WebSocket, which is all the server's /ws/game endpoint needs (it is registered
 * without SockJS). Open one browser tab per player.
 */

// === STOMP over a raw WebSocket ===

const NULL = '\u0000';

class Stomp {

    constructor(url) {
        this.url = url;
        this.socket = null;
        this.subscriptionId = 0;
        this.handlers = new Map();
        this.onError = () => {};
    }

    /**
     * Opens the socket and sends CONNECT with the JWT in the "token" header,
     * which is what JwtChannelInterceptor reads. Resolves once CONNECTED comes back.
     */
    connect(token) {
        return new Promise((resolve, reject) => {
            this.socket = new WebSocket(this.url);
            this.socket.onopen = () => {
                this.#sendFrame('CONNECT', {
                    'accept-version': '1.2',
                    'heart-beat': '0,0',
                    token: token
                });
            };
            this.socket.onmessage = (event) => {
                for (const frame of this.#parse(event.data)) {
                    if (frame.command === 'CONNECTED') {
                        resolve();
                    } else if (frame.command === 'MESSAGE') {
                        this.#dispatch(frame);
                    } else if (frame.command === 'ERROR') {
                        this.onError(frame.headers.message || 'STOMP error');
                        reject(new Error(frame.headers.message || 'STOMP error'));
                    }
                }
            };
            this.socket.onclose = () => this.onError('connection closed');
            this.socket.onerror = () => reject(new Error('WebSocket failed'));
        });
    }

    /**
     * Subscribes to a destination. Returns the subscription id, so the same destination
     * can be subscribed again to make the server re-send its state.
     */
    subscribe(destination, handler) {
        const id = 'sub-' + (++this.subscriptionId);
        this.handlers.set(id, { destination, handler });
        this.#sendFrame('SUBSCRIBE', { id: id, destination: destination });
        return id;
    }

    unsubscribe(id) {
        this.handlers.delete(id);
        this.#sendFrame('UNSUBSCRIBE', { id: id });
    }

    send(destination, body) {
        this.#sendFrame(
            'SEND',
            { destination: destination, 'content-type': 'application/json' },
            JSON.stringify(body || {})
        );
    }

    close() {
        if (this.socket) {
            this.#sendFrame('DISCONNECT', {});
            this.socket.close();
        }
    }

    #dispatch(frame) {
        const entry = this.handlers.get(frame.headers.subscription);
        if (!entry) {
            return;
        }
        let payload = frame.body;
        try {
            payload = JSON.parse(frame.body);
        } catch (ignored) {
            // Leave the raw body in place; the caller decides what to do with it.
        }
        entry.handler(payload, frame.headers);
    }

    #sendFrame(command, headers, body) {
        let frame = command + '\n';
        for (const [key, value] of Object.entries(headers)) {
            frame += key + ':' + value + '\n';
        }
        frame += '\n' + (body || '') + NULL;
        this.socket.send(frame);
    }

    /** Splits a raw payload into frames and parses command, headers and body. */
    #parse(data) {
        return data.split(NULL)
            .filter((chunk) => chunk.trim().length > 0)
            .map((chunk) => {
                const split = chunk.indexOf('\n\n');
                const head = chunk.slice(0, split === -1 ? chunk.length : split);
                const body = split === -1 ? '' : chunk.slice(split + 2);
                const lines = head.split('\n').filter((line) => line.length > 0);
                const headers = {};
                for (const line of lines.slice(1)) {
                    const colon = line.indexOf(':');
                    headers[line.slice(0, colon)] = line.slice(colon + 1);
                }
                return { command: lines[0], headers: headers, body: body };
            });
    }
}

// === Cards ===

const SUIT_SYMBOL = { COPAS: '🍷', OROS: '🪙', ESPADAS: '⚔️', BASTOS: '🌳' };
const SUIT_LABEL = { COPAS: 'Copas', OROS: 'Oros', ESPADAS: 'Espadas', BASTOS: 'Bastos' };
const RANK_LABEL = {
    AS: 'A', DOS: '2', TRES: '3', CUATRO: '4', CINCO: '5',
    SEIS: '6', SIETE: '7', SOTA: 'S', CABALLO: 'C', REY: 'R'
};

function cardKey(card) {
    return card.suit + '-' + card.rank;
}

function cardName(card) {
    return RANK_LABEL[card.rank] + ' ' + SUIT_LABEL[card.suit];
}

/*
 * Card order, mirroring CardRankingService.
 *
 * The three specials (Espadilla, Manilla, Basto) always take the top three places of the
 * trump column. The low cards run 2 down to 7 in Copas and Oros and 7 down to 2 in
 * Espadas and Bastos, whether or not the suit is trump. A suit whose Ace is a special
 * card does not list that Ace.
 */
const TRUMP_TAIL_COPAS_OROS = ['AS', 'REY', 'CABALLO', 'SOTA', 'DOS', 'TRES', 'CUATRO', 'CINCO', 'SEIS'];
const TRUMP_TAIL_ESPADAS_BASTOS = ['REY', 'CABALLO', 'SOTA', 'SIETE', 'SEIS', 'CINCO', 'CUATRO', 'TRES'];
const NON_TRUMP_COPAS_OROS = ['REY', 'CABALLO', 'SOTA', 'AS', 'DOS', 'TRES', 'CUATRO', 'CINCO', 'SEIS', 'SIETE'];
const NON_TRUMP_ESPADAS_BASTOS = ['REY', 'CABALLO', 'SOTA', 'SIETE', 'SEIS', 'CINCO', 'CUATRO', 'TRES', 'DOS'];

/** The Manilla for a given trump: the 7 for Copas/Oros, the 2 for Espadas/Bastos. */
function manillaRank(trump) {
    return trump === 'COPAS' || trump === 'OROS' ? 'SIETE' : 'DOS';
}

/**
 * The ordered cards of one suit for a given trump, strongest first.
 * Entries carry a note for the three special cards so the help can label them.
 */
function suitOrder(suit, trump) {
    if (suit === trump) {
        const tail = trump === 'COPAS' || trump === 'OROS'
            ? TRUMP_TAIL_COPAS_OROS
            : TRUMP_TAIL_ESPADAS_BASTOS;
        return [
            { card: { suit: 'ESPADAS', rank: 'AS' }, note: 'Espadilla' },
            { card: { suit: trump, rank: manillaRank(trump) }, note: 'Manilla' },
            { card: { suit: 'BASTOS', rank: 'AS' }, note: 'Basto' },
            ...tail.map((rank) => ({ card: { suit: suit, rank: rank }, note: null }))
        ];
    }
    const order = suit === 'COPAS' || suit === 'OROS'
        ? NON_TRUMP_COPAS_OROS
        : NON_TRUMP_ESPADAS_BASTOS;
    return order.map((rank) => ({ card: { suit: suit, rank: rank }, note: null }));
}

/** Espadilla and Basto never count as their own suit when following. */
function isSpecial(card) {
    return card.rank === 'AS' && (card.suit === 'ESPADAS' || card.suit === 'BASTOS');
}


/** How the going side came about, for the sides panel. */
const MODE_LABEL = {
    HELPED: 'a king was put — two against three',
    SELF_KING: 'put their own king — one against four',
    SOLEDAD: 'went alone — one against four',
    FOUR_KINGS: 'four kings dealt — hand over',
    KING_FELL: 'the king fell — hand over'
};

// === Application state ===

const state = {
    me: null,           // { playerId, displayName, token }
    roomId: null,
    gameId: null,
    stomp: null,
    hand: [],           // cards still held, maintained locally between GAME_STATE pushes
    round: null,        // last round payload received
    currentTurn: null,
    trump: null,
    basa: [],           // { playerId, card } played so far in the current basa
    seats: new Map(),   // playerId -> seat label
    coins: {},
    posso: null         // what is in the middle of the table
};

const el = (id) => document.getElementById(id);

function log(message, kind) {
    const line = document.createElement('div');
    line.className = 'log-line' + (kind ? ' log-' + kind : '');
    line.textContent = message;
    el('log').prepend(line);
}

function seatOf(playerId) {
    if (playerId === state.me?.playerId) {
        return 'you';
    }
    return state.seats.get(playerId) || (playerId ? playerId.slice(0, 4) : '-');
}

// === REST calls ===

async function api(path, options) {
    const opts = options || {};
    const headers = { 'Content-Type': 'application/json' };
    if (state.me) {
        headers['Authorization'] = 'Bearer ' + state.me.token;
    }
    const response = await fetch(path, { method: opts.method || 'GET', headers, body: opts.body });
    if (!response.ok) {
        throw new Error(path + ' -> ' + response.status);
    }
    return response.status === 204 ? null : response.json();
}

async function createIdentity(displayName) {
    const body = JSON.stringify({ displayName: displayName });
    const response = await fetch('/api/auth/anonymous', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body
    });
    state.me = await response.json();
    sessionStorage.setItem('ginebra.me', JSON.stringify(state.me));
    el('whoami').textContent = state.me.displayName + ' (' + state.me.playerId.slice(0, 8) + ')';
    log('Playing as ' + state.me.displayName, 'good');
}

async function refreshRooms() {
    const response = await api('/api/rooms');
    const list = el('rooms');
    list.innerHTML = '';
    if (response.rooms.length === 0) {
        list.innerHTML = '<li class="empty">No open rooms. Create one.</li>';
        return;
    }
    for (const room of response.rooms) {
        const item = document.createElement('li');
        item.innerHTML = '<span>' + room.roomId.slice(0, 8) + ' &middot; ' + room.playerCount + '/5</span>';
        const button = document.createElement('button');
        button.textContent = 'Join';
        button.onclick = () => joinRoom(room.roomId);
        item.appendChild(button);
        list.appendChild(item);
    }
}

async function createRoom() {
    const response = await api('/api/rooms', { method: 'POST' });
    state.roomId = response.roomId;
    log('Created room ' + response.roomId.slice(0, 8) + ' - waiting for 4 more players', 'good');
    el('room-status').textContent = 'In room ' + response.roomId.slice(0, 8) + ' (1/5)';
    await refreshRooms();
    pollForGameStart(response.roomId);
}

async function joinRoom(roomId) {
    const response = await api('/api/rooms/' + roomId + '/join', { method: 'POST' });
    state.roomId = roomId;
    el('room-status').textContent =
        'In room ' + roomId.slice(0, 8) + ' (' + response.players.length + '/5)';
    log('Joined room ' + roomId.slice(0, 8) + ' - ' + response.players.length + '/5 players');

    if (response.gameId) {
        await enterGame(response.gameId, response.players);
    } else {
        await refreshRooms();
        pollForGameStart(roomId);
    }
}

/**
 * The lobby has no push channel, so a player who joined early polls the room until it
 * reports a gameId. Only the fifth player to join gets one from the join call itself.
 */
function pollForGameStart(roomId) {
    const timer = setInterval(async () => {
        if (state.gameId) {
            clearInterval(timer);
            return;
        }
        try {
            const room = await api('/api/rooms/' + roomId);
            el('room-status').textContent =
                'In room ' + roomId.slice(0, 8) + ' (' + room.players.length + '/5)';
            if (room.gameId) {
                clearInterval(timer);
                await enterGame(room.gameId, room.players);
            }
        } catch (error) {
            log('Room lookup failed: ' + error.message, 'bad');
        }
    }, 1000);
}

// === Game ===

async function enterGame(gameId, players) {
    if (state.gameId) {
        return;   // Two poll ticks can race; a second subscription would double every event.
    }
    state.gameId = gameId;
    (players || []).forEach((player, index) => {
        state.seats.set(player.playerId || player.id, player.displayName || 'P' + index);
    });

    el('lobby').classList.add('hidden');
    el('game').classList.remove('hidden');
    log('Game started: ' + gameId.slice(0, 8), 'good');

    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    state.stomp = new Stomp(protocol + '://' + location.host + '/ws/game');
    state.stomp.onError = (message) => log('Connection: ' + message, 'bad');

    await state.stomp.connect(state.me.token);

    state.stomp.subscribe('/user/queue/game-state', onGameState);
    state.stomp.subscribe('/user/queue/errors', onServerMessage);
    state.stomp.subscribe('/topic/game/' + gameId, onServerMessage);

    awaitInitialState(0);
}

/**
 * Makes sure the first GAME_STATE actually lands.
 *
 * The server pushes it when the game topic subscription is registered, but STOMP frames
 * arrive on a thread pool: the topic SUBSCRIBE can be processed before the private queue
 * SUBSCRIBE that the push is addressed to, and the simple broker drops a message with no
 * subscriber. It sends no RECEIPT to wait on either, so the only reliable move is to ask
 * again until the state is here.
 */
function awaitInitialState(attempt) {
    const MAX_ATTEMPTS = 12;
    if (state.round) {
        return;
    }
    if (attempt >= MAX_ATTEMPTS) {
        log('Could not load the game state - try reloading', 'bad');
        return;
    }
    setTimeout(() => {
        if (state.round) {
            return;
        }
        refreshState();
        awaitInitialState(attempt + 1);
    }, 300);
}

/**
 * Asks the server to re-send the personalised state. The server only pushes GAME_STATE
 * on SUBSCRIBE, so a fresh subscription is how a client picks up a new deal.
 */
function refreshState() {
    // The handler is deliberately empty. Subscribing is only a trigger: the state itself
    // arrives on the private queue, which is already subscribed. Handling broadcasts here
    // too would deliver a second copy of every event for as long as this subscription is
    // open, and a duplicate CARD_PLAYED arriving after BASA_WON would rewind the turn.
    const id = state.stomp.subscribe('/topic/game/' + state.gameId, () => {});
    setTimeout(() => state.stomp.unsubscribe(id), 500);
}

function onGameState(message) {
    if (message.type !== 'GAME_STATE') {
        return;
    }
    const payload = message.payload;
    state.coins = payload.coinBalances || {};
    state.posso = payload.posso ?? null;
    payload.players.forEach((player) => {
        if (!state.seats.has(player.id)) {
            state.seats.set(player.id, 'seat ' + player.seatPosition);
        }
    });

    const round = payload.currentRound;
    if (!round) {
        return;
    }
    state.round = round;
    state.hand = round.yourHand || [];
    state.trump = round.trumpSuit;
    state.currentTurn = round.currentTurn;
    state.basa = (round.currentBasa?.cardsPlayed || []).map((played) => ({
        playerId: played.playerId,
        card: played.card
    }));
    render();
}

function onServerMessage(message) {
    const payload = message.payload || {};

    switch (message.type) {
        case 'GAME_STATE':
            onGameState(message);
            return;

        case 'PLAYER_CONNECTED':
            log(seatOf(payload.playerId) + ' connected');
            break;

        case 'PLAYER_DISCONNECTED':
            log(seatOf(payload.playerId) + ' disconnected', 'bad');
            break;

        case 'SOLEDAD_PASSED':
            // Deliberately does not touch the round status: only SOLEDAD_WINDOW_CLOSED
            // and TRUMP_SELECTED move the phase forward, and a stray late pass event
            // must not drag it back.
            log(seatOf(payload.playerId) + ' passed Soledad (' +
                payload.remainingPlayers.length + ' still to answer)');
            break;

        case 'SOLEDAD_DECLARED':
            log(seatOf(payload.byPlayer) + ' declared SOLEDAD', 'good');
            state.round = Object.assign({}, state.round, { soledadPlayer: payload.byPlayer });
            break;

        case 'SOLEDAD_WINDOW_CLOSED':
            state.round = Object.assign({}, state.round, {
                status: 'WAITING_FOR_TRUMP',
                playerWhoGoes: payload.awaitingTrumpFrom
            });
            log('Soledad window closed - ' + seatOf(payload.awaitingTrumpFrom) + ' picks trump');
            break;

        case 'TRUMP_SELECTED':
            state.trump = payload.suit;
            state.currentTurn = payload.currentTurn;
            state.round = Object.assign({}, state.round, {
                status: 'IN_PROGRESS',
                trumpSuit: payload.suit
            });
            log(seatOf(payload.byPlayer) + ' chose ' + SUIT_LABEL[payload.suit] + ' as trump', 'good');
            break;

        case 'CARD_PLAYED':
            state.basa.push({ playerId: payload.playerId, card: payload.card });
            state.currentTurn = payload.nextTurn;
            if (payload.playerId === state.me.playerId) {
                state.hand = state.hand.filter((card) => cardKey(card) !== cardKey(payload.card));
            }
            log(seatOf(payload.playerId) + ' played ' + cardName(payload.card));
            break;

        case 'BASA_WON':
            log('Basa ' + payload.basaNumber + ' won by ' + seatOf(payload.winner), 'good');
            state.basa = [];
            state.currentTurn = payload.nextStarter;
            state.round = Object.assign({}, state.round, { basasWon: payload.basasWon });
            break;

        case 'SIDE_DECIDED':
            log(seatOf(payload.byPlayer) + ' played ' + cardName(payload.king)
                + (payload.forced ? ' (forced — pays 1)' : '')
                + ': ' + (MODE_LABEL[payload.mode] || payload.mode)
                + ' — [' + payload.goingSide.map(seatOf).join(', ') + '] vs ['
                + payload.opposingSide.map(seatOf).join(', ') + ']', 'good');
            state.round = Object.assign({}, state.round, {
                mode: payload.mode,
                goingSide: payload.goingSide,
                opposingSide: payload.opposingSide
            });
            break;

        case 'ROUND_ENDED':
            log('Round ' + payload.roundNumber + ' ended: '
                + payload.result.replace(/_/g, ' ').toLowerCase()
                + (payload.winners.length ? ' — ' + payload.winners.map(seatOf).join(', ') : '')
                + ' · posso ' + payload.posso, 'good');
            state.coins = payload.coinBalances;
            state.posso = payload.posso;
            state.basa = [];
            // The next round is dealt server-side; ask for the new hand.
            setTimeout(refreshState, 250);
            break;

        case 'GAME_ENDED':
            log('Game over: ' + payload.reason, 'good');
            state.currentTurn = null;
            break;

        case 'ERROR':
            log('Rejected: ' + payload.code + ' - ' + payload.message, 'bad');
            break;

        default:
            log('Unhandled message: ' + message.type);
    }
    render();
}

// === Actions ===

function passSoledad() {
    state.stomp.send('/app/game/' + state.gameId + '/soledad-pass', {});
}

function declareSoledad() {
    state.stomp.send('/app/game/' + state.gameId + '/declare-soledad', {});
}

function selectTrump(suit) {
    state.stomp.send('/app/game/' + state.gameId + '/select-trump', { suit: suit });
}

function playCard(card) {
    state.stomp.send('/app/game/' + state.gameId + '/play-card', { card: card });
}

// === Rendering ===

function render() {
    const round = state.round;
    if (!round) {
        return;
    }

    const myTurn = state.currentTurn === state.me.playerId;
    const iPickTrump = round.status === 'WAITING_FOR_TRUMP'
        && round.trumpChooser === state.me.playerId;

    el('phase').textContent = round.status.replace(/_/g, ' ').toLowerCase();
    el('trump').textContent = state.trump
        ? SUIT_SYMBOL[state.trump] + ' ' + SUIT_LABEL[state.trump]
        : 'not chosen';
    el('round-number').textContent = round.roundNumber;
    el('coins').textContent = state.coins[state.me.playerId] ?? '-';
    el('posso').textContent = state.posso ?? '-';
    el('turn').textContent = state.currentTurn ? seatOf(state.currentTurn) : '-';
    el('turn').className = myTurn ? 'highlight' : '';

    const soledadOpen = round.status === 'WAITING_FOR_SOLEDAD';
    el('soledad-actions').classList.toggle('hidden', !soledadOpen);
    if (soledadOpen) {
        renderSoledadWindow(round);
    }
    el('trump-actions').classList.toggle('hidden', !iPickTrump);

    renderBasa();
    renderHand(myTurn);
    renderScores(round);
    renderTeams(round);

    // Keep an open help panel showing the trump actually in play.
    if (state.trump && state.trump !== helpTrump && !el('help-panel').classList.contains('hidden')) {
        renderHelp(state.trump);
    }
}

function renderBasa() {
    const table = el('basa');
    table.innerHTML = '';
    for (const played of state.basa) {
        const slot = document.createElement('div');
        slot.className = 'played';
        slot.appendChild(cardElement(played.card, false));
        const who = document.createElement('span');
        who.className = 'played-by';
        who.textContent = seatOf(played.playerId);
        slot.appendChild(who);
        table.appendChild(slot);
    }
    if (state.basa.length === 0) {
        table.innerHTML = '<p class="empty">No cards on the table yet.</p>';
    }
}

/**
 * The Soledad window. A four-king deal narrows it to one player: only they may go alone,
 * and their pass takes the 4 and ends the hand.
 */
function renderSoledadWindow(round) {
    const holder = round.fourKingHolder;
    const mine = holder === state.me.playerId;

    if (!holder) {
        el('soledad-label').textContent = 'Soledad window open:';
        el('pass-soledad').disabled = false;
        el('declare-soledad').disabled = false;
        el('declare-soledad').textContent = 'Declare Soledad';
        return;
    }

    el('soledad-label').textContent = mine
        ? 'Four kings! Take the 4, or play it out alone and keep the 4 as well:'
        : seatOf(holder) + ' was dealt the four kings — only they may go alone';
    el('pass-soledad').disabled = !mine;
    el('declare-soledad').disabled = !mine;
    el('declare-soledad').textContent = mine ? 'Go alone (keep the 4)' : 'Declare Soledad';
    el('pass-soledad').textContent = mine ? 'Take the 4, end the hand' : 'Pass';
}

/** The card that opened the current basa, or null if this player leads. */
function ledCard() {
    return state.basa.length === 0 ? null : state.basa[0].card;
}

/** The Espadilla and the Basto are trumps whatever the trump suit is. */
function isTrumpCard(card) {
    return isSpecial(card) || card.suit === state.trump;
}

/** Position in the trump order, where 0, 1 and 2 are Espadilla, Manilla and Basto. */
function trumpIndex(card) {
    const key = cardKey(card);
    return suitOrder(state.trump, state.trump).findIndex((entry) => cardKey(entry.card) === key);
}

/** The suit a card would lead: a special card leads trump, like everywhere else. */
function suitLedBy(card) {
    return isSpecial(card) ? state.trump : card.suit;
}

/**
 * Opening a basa: while no King has appeared the leader must open with a suit not yet led
 * this round, unless they hold nothing untouched. Mirrors MoveValidator.validateLead.
 */
function isPlayableAsLead(card) {
    const round = state.round;
    const led = (round && round.ledSuits) || [];
    if (!round || round.mode || led.length === 0) {
        return true;
    }
    if (!led.includes(suitLedBy(card))) {
        return true;
    }
    return !state.hand.some((held) => !led.includes(suitLedBy(held)));
}

/** Only a special card outranking the card led may be kept back from a trump lead. */
function mayWithhold(card, led) {
    const index = trumpIndex(card);
    return index >= 0 && index <= 2 && index < trumpIndex(led);
}

/**
 * Mirrors the server's MoveValidator so the UI only offers legal cards.
 * The server stays authoritative - this just avoids clicks it would reject.
 */
function isPlayable(card) {
    const led = ledCard();
    if (led === null) {
        return isPlayableAsLead(card);
    }
    if (isTrumpCard(led)) {
        // A trump was led: play a trump unless every trump held may be withheld.
        return isTrumpCard(card)
            || !state.hand.some((held) => isTrumpCard(held) && !mayWithhold(held, led));
    }
    // A plain suit was led: follow it if you hold it - the specials are trumps, not an escape.
    const canFollow = state.hand.some((held) => !isSpecial(held) && held.suit === led.suit);
    return !canFollow || (card.suit === led.suit && !isSpecial(card));
}

function renderHand(myTurn) {
    const container = el('hand');
    container.innerHTML = '';

    const playing = myTurn && state.round.status === 'IN_PROGRESS';
    for (const card of state.hand) {
        container.appendChild(cardElement(card, playing && isPlayable(card)));
    }
    if (state.hand.length === 0) {
        container.innerHTML = '<p class="empty">Hand is empty.</p>';
    }

    const led = ledCard();
    if (!playing) {
        el('follow-hint').textContent = '';
    } else if (led === null) {
        const round = state.round;
        const untouched = ['COPAS', 'OROS', 'ESPADAS', 'BASTOS']
            .filter((suit) => !(round.ledSuits || []).includes(suit));
        el('follow-hint').textContent = (!round.mode && untouched.length > 0
                && (round.ledSuits || []).length > 0)
            ? 'You lead — a suit not led yet, until a King comes out: '
                + untouched.map((suit) => SUIT_SYMBOL[suit] + ' ' + SUIT_LABEL[suit]).join(', ')
            : '';
    } else if (isTrumpCard(led)) {
        el('follow-hint').textContent = 'Trump led - must play a trump if you can';
    } else {
        el('follow-hint').textContent =
            'Must follow ' + SUIT_SYMBOL[led.suit] + ' ' + SUIT_LABEL[led.suit] + ' if you can';
    }
}

function renderScores(round) {
    const scores = el('scores');
    scores.innerHTML = '';
    const basasWon = round.basasWon || {};
    for (const [playerId, seat] of state.seats.entries()) {
        const row = document.createElement('li');
        const isMe = playerId === state.me.playerId;
        row.className = isMe ? 'me' : '';
        row.textContent = (isMe ? 'you' : seat) + ' - ' + (basasWon[playerId] || 0) + ' basas, '
            + (state.coins[playerId] ?? '-') + ' coins';
        scores.appendChild(row);
    }
}

// === Help panel ===

/** Which trump the help is currently illustrating; follows the round once trump is chosen. */
let helpTrump = 'COPAS';

function toggleHelp() {
    const panel = el('help-panel');
    const opening = panel.classList.contains('hidden');
    panel.classList.toggle('hidden', !opening);
    el('help-toggle').setAttribute('aria-expanded', String(opening));
    if (opening) {
        renderHelp(state.trump || helpTrump);
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

function renderHelp(trump) {
    helpTrump = trump;

    const picker = el('help-trump-buttons');
    picker.innerHTML = '';
    for (const suit of Object.keys(SUIT_LABEL)) {
        const button = document.createElement('button');
        button.className = 'suit-chip' + (suit === trump ? ' selected' : '');
        button.textContent = SUIT_SYMBOL[suit] + ' ' + SUIT_LABEL[suit];
        button.onclick = () => renderHelp(suit);
        picker.appendChild(button);
    }

    const tables = el('help-tables');
    tables.innerHTML = '';
    // Trump first, then the others in their usual order.
    const suits = [trump, ...Object.keys(SUIT_LABEL).filter((suit) => suit !== trump)];
    for (const suit of suits) {
        tables.appendChild(helpColumn(suit, trump));
    }
}

function helpColumn(suit, trump) {
    const column = document.createElement('div');
    column.className = 'help-column' + (suit === trump ? ' is-trump' : '');

    const heading = document.createElement('h3');
    heading.textContent = SUIT_SYMBOL[suit] + ' ' + SUIT_LABEL[suit] + (suit === trump ? ' — trump' : '');
    column.appendChild(heading);

    const list = document.createElement('ol');
    for (const entry of suitOrder(suit, trump)) {
        const item = document.createElement('li');

        const chip = document.createElement('span');
        chip.className = 'mini-card suit-' + entry.card.suit.toLowerCase();
        chip.textContent = RANK_LABEL[entry.card.rank] + ' ' + SUIT_SYMBOL[entry.card.suit];
        item.appendChild(chip);

        if (entry.note) {
            const note = document.createElement('span');
            note.className = 'mini-note';
            note.textContent = entry.note;
            item.appendChild(note);
        }
        list.appendChild(item);
    }
    column.appendChild(list);
    return column;
}

// === Teams ===

function renderTeams(round) {
    const panel = el('teams-panel');
    const going = round.goingSide || [];
    if (!round.mode || going.length === 0) {
        panel.classList.add('hidden');
        return;
    }
    panel.classList.remove('hidden');

    const opposing = round.opposingSide || [];
    el('teams-label').textContent = MODE_LABEL[round.mode] || round.mode;
    fillTeam(el('team-two'), going, round);
    fillTeam(el('team-three'), opposing, round);

    const basasWon = round.basasWon || {};
    const total = (side) => side.reduce((sum, id) => sum + (basasWon[id] || 0), 0);
    const mine = going.includes(state.me.playerId) ? 'the side that goes' : 'the opposing side';
    el('teams-score').textContent =
        'Basas: goes ' + total(going) + ' - ' + total(opposing) + ' against'
        + ' · you are on ' + mine
        + ' · the side that goes needs 5, the other side blocks with 4';
}

function fillTeam(list, memberIds, round) {
    list.innerHTML = '';
    for (const playerId of memberIds) {
        const item = document.createElement('li');
        const isMe = playerId === state.me.playerId;
        item.className = isMe ? 'me' : '';

        const labels = [];
        if (playerId === round.playerWhoGoes) {
            labels.push('goes');
        }
        if (round.soledadPlayer === playerId) {
            labels.push('soledad');
        }
        const basas = (round.basasWon || {})[playerId] || 0;
        item.textContent = (isMe ? 'you' : seatOf(playerId))
            + (labels.length ? ' (' + labels.join(', ') + ')' : '')
            + ' — ' + basas + (basas === 1 ? ' basa' : ' basas');
        list.appendChild(item);
    }
}

function cardElement(card, playable) {
    const node = document.createElement('button');
    node.className = 'card suit-' + card.suit.toLowerCase() + (playable ? ' playable' : '');
    node.disabled = !playable;
    node.title = cardName(card);
    node.innerHTML =
        '<span class="rank">' + RANK_LABEL[card.rank] + '</span>' +
        '<span class="suit">' + SUIT_SYMBOL[card.suit] + '</span>';
    if (playable) {
        node.onclick = () => playCard(card);
    }
    return node;
}

// === Wiring ===

window.addEventListener('DOMContentLoaded', async () => {
    el('create-room').onclick = () => createRoom().catch((e) => log(e.message, 'bad'));
    el('refresh-rooms').onclick = () => refreshRooms().catch((e) => log(e.message, 'bad'));
    el('help-toggle').onclick = toggleHelp;
    el('pass-soledad').onclick = passSoledad;
    el('declare-soledad').onclick = declareSoledad;
    for (const suit of Object.keys(SUIT_LABEL)) {
        const button = document.createElement('button');
        button.textContent = SUIT_SYMBOL[suit] + ' ' + SUIT_LABEL[suit];
        button.onclick = () => selectTrump(suit);
        el('trump-buttons').appendChild(button);
    }

    const name = new URLSearchParams(location.search).get('name')
        || 'Player ' + Math.floor(Math.random() * 1000);
    await createIdentity(name);
    await refreshRooms();
});
