# Running the game for a play-test

The whole thing is one Spring Boot process serving both the API and the web page. There is
no database, no build step for the frontend, and nothing to configure.

---

## 1. Locally, five tabs on one machine

```sh
./gradlew bootRun
```

Then open **http://localhost:8080** in **five browser tabs**. Each tab asks for a name
before it shows the lobby. Put `?name=` in the URL to fill it in and skip the box, which
is what makes five tabs on one machine bearable:

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

### Railway

There is deliberately **no `railway.json`** in the repo: Railway detects the `Dockerfile`
on its own, and every setting that matters is in the dashboard, where it cannot be wrong.

1. **New Project → Deploy from GitHub repo**, pick `dts1234/ginebra` and the branch.
   It will find the `Dockerfile` and start building. The first build is slow — it resolves
   the whole Gradle dependency tree.
2. **Variables → New Variable**: `JWT_SECRET`, set to a long random string
   (`openssl rand -base64 48`). Do this **before** you hand the URL to anyone: the default
   in `application.yml` is a placeholder committed to this repo.
   Do **not** set `PORT` — Railway injects it, and the app reads it.
3. **Settings → Networking → Generate Domain.** That is the URL people open. WebSockets
   work through it untouched.

   It will ask which port to route the domain to: answer **8080**. That is `ENV PORT=8080`
   / `EXPOSE 8080` in the `Dockerfile` and `port: ${PORT:8080}` in `application.yml`.
   The app listens on `$PORT` when Railway injects it and falls back to 8080 otherwise, so
   the two agree — unless you set a `PORT` variable by hand in step 2, in which case delete
   it or enter that value here instead.
4. **Settings → Deploy → replicas: 1.** More than one and players would land on different
   instances that cannot see each other's games.
5. **Check the deploy strategy.** Railway's default is to bring the new instance up before
   taking the old one down, which briefly runs two — the same problem as replicas. If there
   is an overlap or health-check-before-switch option, turn it off, and treat every redeploy
   as ending the games in progress either way.

Health checks can point at **`/`**, which returns the page with a 200. There is no actuator
dependency, so `/health` does not exist.

To redeploy, push to the branch. To roll back, use Railway's deployment history — but both
drop every game in progress, so do it when the table is empty.

*Written from the Railway dashboard as it works generally; their docs are blocked from the
machine this was written on, so treat the exact menu names as a guide rather than a
transcript.*

### Render

Point it at the repo; it detects the `Dockerfile`. Then set **`JWT_SECRET`**, set instances
to **1**, and leave `PORT` alone.

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
