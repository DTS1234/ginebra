# Running the game for a play-test

The whole thing is one Spring Boot process serving both the API and the web page. There is
no database, no build step for the frontend, and nothing to configure.

---

## 1. Locally, five tabs on one machine

```sh
./gradlew bootRun
```

Then open **http://localhost:8080** in **five browser tabs**. Add `?name=` to label them,
which makes the log readable:

```
http://localhost:8080/?name=Ada
http://localhost:8080/?name=Ben
http://localhost:8080/?name=Cleo
http://localhost:8080/?name=Dani
http://localhost:8080/?name=Eva
```

One tab creates a room, the other four join it from the list, and the game starts the
moment the fifth player is in.

Stop with `Ctrl-C`.

### If the build complains about Java

`build.gradle.kts` pins the toolchain to **Java 17**. If you get

```
Cannot find a Java installation on your machine ... languageVersion=17
```

either install a JDK 17, or point Gradle at one you already have:

```sh
./gradlew bootRun -Porg.gradle.java.installations.paths=/path/to/jdk-17
```

It runs fine on 21 as well — flip the pin in `build.gradle.kts` if that is all you have.

---

## 2. On your LAN — other people, same wifi

`bootRun` already listens on all interfaces, so nothing to change. Find your address and
hand it out:

```sh
# macOS
ipconfig getifaddr en0
# Linux
hostname -I | awk '{print $1}'
```

Everyone opens `http://<that-address>:8080`. Good enough for testing round a table.

---

## 3. Over the internet — a tunnel, no deployment

For a remote play-test, put a tunnel in front of the local process. Both of these carry
WebSockets, which the game needs.

**Cloudflare** (no account needed for a quick URL):

```sh
./gradlew bootRun          # in one terminal
cloudflared tunnel --url http://localhost:8080   # in another
```

It prints a `https://something.trycloudflare.com` URL. Send that round.

**ngrok** (needs a free account):

```sh
ngrok http 8080
```

Set a real JWT secret if the URL is going anywhere public:

```sh
JWT_SECRET="$(openssl rand -base64 48)" ./gradlew bootRun
```

---

## 4. Hosting it properly

There is no database and no build step for the frontend, so the whole game is one process.
That makes deployment easy — but it also means **every table lives in memory**, which
constrains how you run it:

- **One instance only.** A second one would not see the first one's games. No autoscaling,
  no load balancer spreading players across replicas.
- **A restart drops every game in progress.** Deploy when nobody is playing, and avoid
  rolling restarts. Persistence is Phase 5 and has not been started.
- **WebSockets must survive the proxy.** Whatever sits in front has to pass the upgrade
  through, or the page connects and then sits silent.

`Dockerfile` builds and runs the whole thing, so any platform that takes a container will
do. It listens on `$PORT`, which is what these platforms inject, and falls back to 8080.

### Fly.io — closest fit

Single machine, WebSockets work out of the box, and `fly.toml` is already in the repo with
autoscaling off and one machine pinned.

```sh
fly launch --no-deploy          # claims the app name, keeps the committed fly.toml
fly secrets set JWT_SECRET="$(openssl rand -base64 48)"
fly deploy
```

### Render / Railway — git push and forget

Point either at the repo; both detect the `Dockerfile`. Then:

- set **`JWT_SECRET`** to a long random string,
- set instances/replicas to **1**,
- leave `PORT` alone — the platform sets it.

### A small VPS

```sh
docker build -t ginebra .
docker run -d --name ginebra --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  ginebra
```

Then put Caddy in front for TLS — two lines, and it proxies WebSockets without being asked:

```
ginebra.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

With nginx you have to ask, or the game will connect and go quiet:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;   # a game runs for hours; do not time it out
}
```

### Without a container

```sh
./gradlew bootJar
JWT_SECRET="..." java -jar build/libs/ginebra-0.1.0-SNAPSHOT.jar
```

One file, one process. Needs a JDK 17+ on the host.

### Before it goes anywhere public

- **Set `JWT_SECRET`.** The default in `application.yml` is a placeholder and is committed
  to the repo — anyone could mint tokens for your server.
- **No rate limiting exists yet** (Phase 6). Anyone who finds the URL can create rooms.
- **Anonymous auth only.** There are no accounts to protect, but there is also nothing
  stopping someone joining a room they were not invited to.

A tunnel (section 3) is the better choice while you are only testing with people you know.

---

## What to expect while testing

- **Everything is in memory.** Restarting the server loses every room and game. Persistence
  is Phase 5 and has not been started.
- **No reconnection UI.** Closing a tab mid-game drops that player, and the round cannot
  continue without them.
- **No timeouts anywhere.** Nothing auto-passes the Soledad window or cleans up abandoned
  rooms, so a player who walks away stalls the table.
- **The `? Help` panel** in the header shows the full card order for the current trump plus
  the rules, generated from the same tables the engine uses.
- The page only offers cards you are allowed to play — it mirrors `MoveValidator` — but the
  server is authoritative and rejects anything illegal regardless.

## Smoke test without a browser

Useful for checking the server is actually up:

```sh
curl -s -X POST localhost:8080/api/auth/anonymous \
     -H 'Content-Type: application/json' -d '{"displayName":"Ada"}'
# -> {"token":"eyJ...","playerId":"...","displayName":"Ada"}

TOKEN=<paste the token>
curl -s -X POST localhost:8080/api/rooms \
     -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"test"}'
# -> {"roomId":"...","players":[...],"status":"WAITING"}
```
