/* Maha Metro CoE – test player: rendering, autosave, timer, proctoring, audio answers. No answer keys reach this page. */
(function () {
  "use strict";
  const D = JSON.parse(document.getElementById("attemptData").textContent);
  const T = JSON.parse(document.getElementById("i18n").textContent);
  const CSRF = document.querySelector('meta[name="csrf"]').content;
  const Q = D.questions;
  const P = D.proctoring || {};
  const official = !D.practice;
  let cur = 0, lang = D.lang || "en", dirty = new Set(), saving = false, failures = 0, submitted = false;
  let qStart = Date.now();
  const spent = {};
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const tx = (o) => (o && (o[lang] || o.en)) || "";
  const fmt = (s, n) => s.replace("{n}", n);
  const storeKey = "mm-attempt-" + D.attempt;
  Q.forEach((q) => { q._t = q.time || 0; }); // seconds already recorded on the server (resume keeps adding)

  // restore unsent answers kept in this browser (network loss)
  try {
    const kept = JSON.parse(localStorage.getItem(storeKey) || "{}");
    Q.forEach((q) => { if (kept[q.id] !== undefined) { q.response = kept[q.id]; dirty.add(q.id); } });
  } catch (e) { /* storage unavailable */ }
  function keep() {
    try { const o = {}; dirty.forEach((id) => { o[id] = Q.find((q) => q.id === id).response; }); localStorage.setItem(storeKey, JSON.stringify(o)); } catch (e) { /* ignore */ }
  }

  function answered(q) {
    const r = q.response;
    if (!r) return false;
    if (r.choice !== undefined && r.choice !== null) return true;
    if (r.choices && r.choices.length) return true;
    if (r.order && r.order.length) return true;
    if (r.pairs && Object.values(r.pairs).some((v) => v)) return true;
    if (r.value !== undefined && String(r.value).trim() !== "") return true;
    if (r.text && r.text.trim()) return true;
    if (r.media_id) return true;
    return false;
  }

  function setResp(q, r) { q.response = r; dirty.add(q.id); keep(); palette(); }

  function mediaHtml(m) {
    if (!m) return "";
    const src = "/media/" + m.id;
    if (m.kind === "image") return `<img src="${src}" alt="">`;
    if (m.kind === "audio") return `<div class="small muted">${esc(T.playOnce)}</div><audio controls preload="metadata" src="${src}" controlsList="nodownload"></audio>`;
    if (m.kind === "video") return `<video controls preload="metadata" src="${src}" controlsList="nodownload" disablePictureInPicture></video>`;
    return "";
  }

  function hint(q) {
    return { single: T.selectOne, truefalse: T.selectOne, sjt: T.selectOne, multi: T.selectAll, sequence: T.order,
      match: T.match, numeric: T.num, fill_blank: T.typeAns, short_text: T.write, audio_response: T.record }[q.qtype] || "";
  }

  function render() {
    const q = Q[cur];
    qStart = Date.now();
    $("qMeta").textContent = `${T.q} ${cur + 1} ${T.of} ${Q.length} · ${q.marks} ${T.marks} · ${hint(q)}`;
    $("passage").innerHTML = q.passage ? `<div class="passage"><b>${esc(tx(q.passage.title) || T.passage)}</b>\n${esc(tx(q.passage.body))}${q.passage.media ? '<div class="media-stem">' + mediaHtml(q.passage.media) + "</div>" : ""}</div>` : "";
    $("media").innerHTML = mediaHtml(q.media);
    $("stem").textContent = tx(q.stem);
    $("feedback").innerHTML = "";
    const A = $("answer");
    A.innerHTML = "";
    const r = q.response || {};
    if (["single", "truefalse", "sjt", "multi"].includes(q.qtype)) {
      const multi = q.qtype === "multi";
      q.options.forEach((o) => {
        const sel = multi ? (r.choices || []).includes(o.id) : r.choice === o.id;
        const lab = document.createElement("label");
        lab.className = "opt" + (sel ? " sel" : "");
        lab.innerHTML = `<input type="${multi ? "checkbox" : "radio"}" name="q${q.id}" value="${o.id}" ${sel ? "checked" : ""}><span>${esc(tx(o))}</span>`;
        lab.querySelector("input").addEventListener("change", () => {
          if (multi) {
            const ch = [...A.querySelectorAll("input:checked")].map((i) => +i.value);
            setResp(q, { choices: ch });
          } else setResp(q, { choice: o.id });
          A.querySelectorAll(".opt").forEach((l) => l.classList.toggle("sel", l.querySelector("input").checked));
        });
        A.appendChild(lab);
      });
    } else if (q.qtype === "sequence") {
      let order = r.order && r.order.length ? r.order.slice() : q.options.map((o) => o.id);
      const draw = () => {
        A.innerHTML = "";
        order.forEach((id, i) => {
          const o = q.options.find((x) => x.id === id);
          const d = document.createElement("div");
          d.className = "seq-item";
          d.innerHTML = `<span class="n">${i + 1}</span><span>${esc(tx(o))}</span><span class="mv"><button type="button" class="btn ghost sm" aria-label="up" ${i === 0 ? "disabled" : ""}>▲</button><button type="button" class="btn ghost sm" aria-label="down" ${i === order.length - 1 ? "disabled" : ""}>▼</button></span>`;
          const [up, dn] = d.querySelectorAll("button");
          up.onclick = () => { [order[i - 1], order[i]] = [order[i], order[i - 1]]; setResp(q, { order: order.slice() }); draw(); };
          dn.onclick = () => { [order[i + 1], order[i]] = [order[i], order[i + 1]]; setResp(q, { order: order.slice() }); draw(); };
          A.appendChild(d);
        });
      };
      draw();
    } else if (q.qtype === "match") {
      const pairs = Object.assign({}, r.pairs || {});
      q.options.forEach((o) => {
        const row = document.createElement("div");
        row.className = "row";
        row.style.alignItems = "center";
        row.innerHTML = `<div style="flex:1">${esc(tx(o))}</div><div style="flex:1"><select><option value="">${esc(T.choose)}</option>${q.right.map((rt) => `<option value="${rt.id}" ${String(pairs[o.id]) === String(rt.id) ? "selected" : ""}>${esc(tx(rt))}</option>`).join("")}</select></div>`;
        row.querySelector("select").addEventListener("change", (e) => { pairs[o.id] = e.target.value ? +e.target.value : ""; setResp(q, { pairs: Object.assign({}, pairs) }); });
        row.style.margin = ".4rem 0";
        A.appendChild(row);
      });
    } else if (q.qtype === "numeric" || q.qtype === "fill_blank") {
      const inp = document.createElement("input");
      inp.type = "text";
      inp.inputMode = q.qtype === "numeric" ? "decimal" : "text";
      inp.value = q.qtype === "numeric" ? (r.value || "") : (r.text || "");
      inp.style.maxWidth = "420px";
      inp.addEventListener("input", () => setResp(q, q.qtype === "numeric" ? { value: inp.value } : { text: inp.value }));
      A.appendChild(inp);
    } else if (q.qtype === "short_text") {
      const ta = document.createElement("textarea");
      ta.rows = 8;
      ta.value = r.text || "";
      ta.addEventListener("input", () => setResp(q, { text: ta.value }));
      A.appendChild(ta);
    } else if (q.qtype === "audio_response") {
      audioRecorder(q, A);
    }
    $("prevBtn").disabled = cur === 0 || !D.allow_back;
    $("flagBtn").textContent = q.flagged ? T.unmark : T.mark;
    $("nextBtn").style.visibility = cur === Q.length - 1 ? "hidden" : "visible";
    document.querySelectorAll(".langbtn").forEach((b) => b.classList.toggle("on", b.dataset.lang === lang));
    palette();
  }

  function audioRecorder(q, A) {
    const r = q.response || {};
    const box = document.createElement("div");
    box.innerHTML = `<div class="row" style="align-items:center"><div style="flex:0"><button type="button" class="btn" id="recBtn">${esc(r.media_id ? T.rerecord : T.record)}</button></div><div id="recState" class="small muted">${r.media_id ? esc(T.recorded) : ""}</div></div>${r.media_id ? `<audio controls src="/media/${r.media_id}" style="width:100%;margin-top:8px"></audio>` : ""}`;
    A.appendChild(box);
    let rec = null, chunks = [];
    box.querySelector("#recBtn").onclick = async (ev) => {
      const btn = ev.currentTarget;
      if (rec && rec.state === "recording") { rec.stop(); return; }
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        rec = new MediaRecorder(stream);
        chunks = [];
        rec.ondataavailable = (e) => chunks.push(e.data);
        rec.onstop = async () => {
          stream.getTracks().forEach((t) => t.stop());
          const blob = new Blob(chunks, { type: rec.mimeType || "audio/webm" });
          const fd = new FormData();
          fd.append("file", blob, "answer.webm");
          box.querySelector("#recState").textContent = T.saving;
          const res = await fetch(`/api/attempt/${D.attempt}/audio/${q.id}`, { method: "POST", body: fd, headers: { "X-CSRF-Token": CSRF } });
          if (res.ok) { const j = await res.json(); q.response = { media_id: j.media_id }; palette(); render(); }
          else box.querySelector("#recState").textContent = T.offline;
        };
        rec.start();
        btn.textContent = T.stop;
        btn.classList.add("danger");
        box.querySelector("#recState").textContent = "● REC";
      } catch (e) { box.querySelector("#recState").textContent = T.noMic; }
    };
  }

  function palette() {
    const pal = $("palette");
    pal.innerHTML = "";
    let a = 0, f = 0;
    Q.forEach((q, i) => {
      const b = document.createElement("button");
      b.type = "button";
      b.textContent = i + 1;
      const isA = answered(q);
      a += isA ? 1 : 0;
      f += q.flagged ? 1 : 0;
      b.className = (q.flagged ? "flag " : isA ? "ans " : "") + (i === cur ? "cur" : "");
      b.onclick = () => { if (D.allow_back || i > cur) go(i); };
      pal.appendChild(b);
    });
    $("counts").textContent = `${T.answered}: ${a} · ${T.review}: ${f} · ${T.unanswered}: ${Q.length - a}`;
  }

  function track() { const q = Q[cur]; spent[q.id] = (spent[q.id] || 0) + Math.round((Date.now() - qStart) / 1000); qStart = Date.now(); dirty.add(q.id); }
  function go(i) { track(); cur = Math.max(0, Math.min(Q.length - 1, i)); render(); save(); window.scrollTo(0, 0); }

  async function save() {
    if (saving || !dirty.size || submitted) return;
    saving = true;
    const ids = [...dirty];
    const answers = ids.map((id) => { const q = Q.find((x) => x.id === id); return { id, response: q.response || null, flagged: !!q.flagged, time: q._t = (q._t || 0) + (spent[id] || 0) }; });
    ids.forEach((id) => { spent[id] = 0; });
    $("saveState").textContent = T.saving;
    try {
      const res = await fetch(`/api/attempt/${D.attempt}/answer`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-Token": CSRF }, body: JSON.stringify({ answers }) });
      if (res.status === 409) { submitted = true; location.href = `/attempt/${D.attempt}/result`; return; }
      if (!res.ok) throw new Error(res.status);
      const j = await res.json();
      ids.forEach((id) => dirty.delete(id));
      keep();
      failures = 0;
      if (typeof j.remaining === "number") syncClock(j.remaining);
      $("saveState").textContent = dirty.size ? T.saving : T.saved;
    } catch (e) {
      failures++;
      $("saveState").textContent = T.offline;
    } finally { saving = false; }
  }
  setInterval(save, Math.max(2, D.autosave || 5) * 1000);

  // ---- timer (server is the authority; this only displays it)
  let endAt = performance.now() + D.remaining * 1000;
  function syncClock(rem) { endAt = performance.now() + rem * 1000; }
  function tick() {
    const left = Math.max(0, Math.round((endAt - performance.now()) / 1000));
    const h = Math.floor(left / 3600), m = Math.floor((left % 3600) / 60), s = left % 60;
    $("timer").textContent = (h ? h + ":" : "") + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
    $("timer").classList.toggle("low", left <= 300);
    if (left <= 0 && !submitted) { submitted = true; warn(T.timeUp); track(); dirty.size ? save().finally(doSubmit) : doSubmit(); }
  }
  setInterval(tick, 1000);
  tick();
  function doSubmit() { try { localStorage.removeItem(storeKey); } catch (e) { /* */ } $("submitForm").submit(); }

  // ---- controls
  $("nextBtn").onclick = () => go(cur + 1);
  $("prevBtn").onclick = () => go(cur - 1);
  $("flagBtn").onclick = () => { const q = Q[cur]; q.flagged = !q.flagged; dirty.add(q.id); render(); };
  $("clearBtn").onclick = () => { setResp(Q[cur], null); render(); };
  document.querySelectorAll(".langbtn").forEach((b) => (b.onclick = () => { lang = b.dataset.lang; render(); }));
  $("submitForm").addEventListener("submit", async (e) => {
    if (submitted && e.isTrusted === false) return;
    if (!submitted) {
      const un = Q.filter((q) => !answered(q)).length;
      if (!window.confirm(fmt(T.confirm, un))) { e.preventDefault(); return; }
      e.preventDefault();
      submitted = true;
      track();
      await save();
      doSubmit();
    }
  });
  window.addEventListener("beforeunload", () => { if (dirty.size && navigator.sendBeacon) { /* best effort */ } });
  window.addEventListener("online", save);

  // ---- proctoring (official tests)
  function warn(msg) { const w = $("warnBar"); w.textContent = msg; w.style.display = "block"; }
  function event(kind, detail) {
    return fetch(`/api/attempt/${D.attempt}/event`, { method: "POST", headers: { "Content-Type": "application/json", "X-CSRF-Token": CSRF }, body: JSON.stringify({ kind, detail: detail || "" }) })
      .then((r) => r.json()).catch(() => ({}));
  }
  if (official) {
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden" && !submitted) event("tab_hidden").then((j) => j.tab_switches && warn(fmt(T.tabWarn, j.tab_switches)));
    });
    ["copy", "paste", "cut"].forEach((k) => document.addEventListener(k, (e) => { if (!(e.target.tagName === "TEXTAREA" && k !== "paste")) { e.preventDefault(); event(k === "cut" ? "copy" : k); } }));
    document.addEventListener("contextmenu", (e) => { e.preventDefault(); event("context_menu"); });
    if (P.fullscreen && document.documentElement.requestFullscreen) {
      const fsBar = document.createElement("div");
      fsBar.className = "flash info noprint";
      fsBar.innerHTML = `${esc(T.fsWarn)} <button class="btn sm" type="button">${esc(T.fsBtn)}</button>`;
      fsBar.querySelector("button").onclick = () => document.documentElement.requestFullscreen().catch(() => {});
      document.querySelector("main").prepend(fsBar);
      const upd = () => { fsBar.style.display = document.fullscreenElement ? "none" : "block"; };
      document.addEventListener("fullscreenchange", () => { if (!document.fullscreenElement && !submitted) event("fullscreen_exit"); upd(); });
      upd();
    }
    if (P.webcam && navigator.mediaDevices) {
      navigator.mediaDevices.getUserMedia({ video: { width: 320, height: 240 } }).then((stream) => {
        const v = $("cam"), c = $("camCanvas");
        if (!v) return;
        v.srcObject = stream;
        const snap = () => {
          if (submitted) return;
          c.getContext("2d").drawImage(v, 0, 0, 320, 240);
          c.toBlob((b) => { if (!b) return; const fd = new FormData(); fd.append("file", b, "snap.jpg"); fetch(`/api/attempt/${D.attempt}/snapshot`, { method: "POST", body: fd, headers: { "X-CSRF-Token": CSRF } }); }, "image/jpeg", 0.7);
        };
        setTimeout(snap, 4000);
        setInterval(snap, Math.max(20, P.snapshot_seconds || 60) * 1000);
      }).catch(() => event("blur", "webcam denied"));
    }
  }
  render();
  if (dirty.size) save();
})();
