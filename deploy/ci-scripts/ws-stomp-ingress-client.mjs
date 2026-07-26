// Minimal hand-rolled WebSocket client over a manual HTTP Upgrade,
// connecting to 127.0.0.1:8081 (the k3d Traefik LB) while sending a Host
// header (ooredoo.localhost) for name-based Ingress routing - avoids
// needing a hosts-file entry or admin rights on the runner. Proven in
// Phase 4's Ingress WebSocket re-verification; reused as-is for the
// automated staging deployment workflow.
//
// Usage: node ws-stomp-ingress-client.mjs <path> <token-or-empty> <label> [--subscribe-notif]
import http from "node:http";
import crypto from "node:crypto";

const [,, path, token, label, mode] = process.argv;

function connectFrame(tok) {
  let f = "CONNECT\naccept-version:1.2\nheart-beat:0,0\n";
  if (tok) f += `Authorization:Bearer ${tok}\n`;
  f += "\n\0";
  return f;
}
function subscribeFrame(id, dest) {
  return `SUBSCRIBE\nid:${id}\ndestination:${dest}\n\n\0`;
}

function encodeWsFrame(payload) {
  const data = Buffer.from(payload, "utf8");
  const mask = crypto.randomBytes(4);
  const maskedData = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) maskedData[i] = data[i] ^ mask[i % 4];
  let header;
  if (data.length < 126) {
    header = Buffer.from([0x81, 0x80 | data.length]);
  } else {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 0x80 | 126;
    header.writeUInt16BE(data.length, 2);
  }
  return Buffer.concat([header, mask, maskedData]);
}

function decodeWsFrame(buf) {
  const len2 = buf[1] & 0x7f;
  let offset = 2;
  let len = len2;
  if (len2 === 126) {
    len = buf.readUInt16BE(2);
    offset = 4;
  } else if (len2 === 127) {
    len = Number(buf.readBigUInt64BE(2));
    offset = 10;
  }
  return buf.slice(offset, offset + len).toString("utf8");
}

const req = http.request({
  host: "127.0.0.1",
  port: 8081,
  path,
  headers: {
    Host: "ooredoo.localhost",
    Connection: "Upgrade",
    Upgrade: "websocket",
    "Sec-WebSocket-Version": "13",
    "Sec-WebSocket-Key": crypto.randomBytes(16).toString("base64"),
  },
});

let done = false;
function finish(code, msg) {
  if (done) return;
  done = true;
  console.log(`[${label}] RESULT: ${msg}`);
  process.exit(code);
}

const hardTimeout = setTimeout(() => finish(1, "TIMEOUT waiting for a STOMP frame"), 15000);

req.on("upgrade", (res, socket) => {
  if (res.statusCode !== 101) {
    clearTimeout(hardTimeout);
    finish(1, `Upgrade failed, status ${res.statusCode}`);
    return;
  }
  const upgradeOk = res.headers.connection && res.headers.connection.toLowerCase() === "upgrade" && res.headers.upgrade === "websocket";
  console.log(`[${label}] Upgrade: 101, Connection=${res.headers.connection}, Upgrade=${res.headers.upgrade}, headersPreserved=${upgradeOk}`);
  if (!upgradeOk) {
    clearTimeout(hardTimeout);
    finish(1, "Traefik did not preserve Connection/Upgrade headers");
    return;
  }

  socket.write(encodeWsFrame(connectFrame(token)));

  socket.on("data", (chunk) => {
    const frame = decodeWsFrame(chunk);
    if (frame.startsWith("CONNECTED")) {
      if (mode === "--subscribe-notif") {
        socket.write(encodeWsFrame(subscribeFrame("sub-0", "/topic/notifications/ROLE_ADMIN")));
        console.log(`[${label}] SUBSCRIBED, waiting for a real push message...`);
      } else {
        clearTimeout(hardTimeout);
        finish(0, "CONNECTED (auth accepted)");
      }
    } else if (frame.startsWith("MESSAGE")) {
      clearTimeout(hardTimeout);
      finish(0, "RECEIVED real push message: " + frame.replace(/\n/g, " | ").slice(0, 300));
    } else if (frame.startsWith("ERROR")) {
      clearTimeout(hardTimeout);
      // For the negative-auth test cases, an ERROR frame IS the expected
      // (successful) outcome - the calling shell script decides pass/fail
      // based on which label/mode was requested, not this exit code alone.
      finish(0, "ERROR frame received (auth rejected): " + frame.split("\n")[0]);
    }
  });

  socket.on("close", () => {
    clearTimeout(hardTimeout);
    finish(0, "SOCKET CLOSED");
  });
  socket.on("error", (e) => {
    clearTimeout(hardTimeout);
    finish(1, "SOCKET ERROR: " + e.message);
  });
});

req.on("error", (e) => {
  clearTimeout(hardTimeout);
  finish(1, "HTTP REQUEST ERROR: " + e.message);
});

req.end();
