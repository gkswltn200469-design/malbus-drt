// 예약 이해 엔진: 정류소 데이터, 별칭 사전, 규칙 기반 추출. index.html(AI 접수)과 agent.html(상담원 보조)이 함께 쓴다.

const STOPS = [
  { id: 'S01', name: '방림리 마을회관', alias: ['방림리 회관', '방림 회관', '회관', '마을회관'] },
  { id: 'S02', name: '계촌리 마을회관', alias: ['계촌리 회관', '계촌 회관', '회관', '마을회관'] },
  { id: 'S03', name: '방림 보건지소', alias: ['보건지소', '보건소', '보건'] },
  { id: 'S04', name: '방림 농협 하나로마트', alias: ['하나로마트', '하나로', '농협', '마트'] },
  { id: 'S05', name: '방림면 행정복지센터', alias: ['행정복지센터', '면사무소', '면사무'] },
  { id: 'S06', name: '계촌리 경로당', alias: ['경로당'] },
  { id: 'S07', name: '평창 시외버스터미널', alias: ['시외버스터미널', '터미널', '읍내'] },
];
const LOW_ALIASES = new Set(['읍내', '마트', '보건']); // 뜻이 넓어 추정이 들어가는 말
const byId = Object.fromEntries(STOPS.map(s => [s.id, s]));
const aliasMap = new Map();
let ALIASES = [];

function addAlias(a, id) {
  a = a.trim();
  if (!a || !byId[id]) return false;
  if (!aliasMap.has(a)) aliasMap.set(a, new Set());
  if (aliasMap.get(a).has(id)) return false;
  aliasMap.get(a).add(id);
  ALIASES = [...aliasMap.keys()].sort((x, y) => y.length - x.length);
  return true;
}
for (const s of STOPS) for (const a of [s.name, ...s.alias]) addAlias(a, s.id);

// 상담원이 가르쳐 준 별칭(선순환). 이 브라우저에만 저장된다.
const LEARNED_KEY = 'malbus-learned-aliases';
function loadLearned() {
  try { return JSON.parse(localStorage.getItem(LEARNED_KEY)) || []; } catch (e) { return []; }
}
function learnAlias(a, id) {
  if (!addAlias(a, id)) return false;
  const list = loadLearned();
  list.push([a.trim(), id]);
  try { localStorage.setItem(LEARNED_KEY, JSON.stringify(list)); } catch (e) { /* 저장 못 해도 이번 화면에서는 동작 */ }
  return true;
}
for (const [a, id] of loadLearned()) addAlias(a, id);

const KNUM = [['열두', 12], ['열한', 11], ['다섯', 5], ['여섯', 6], ['일곱', 7], ['여덟', 8], ['아홉', 9], ['한', 1], ['두', 2], ['세', 3], ['네', 4], ['열', 10]];
const knum = w => /^\d+$/.test(w) ? Number(w) : (KNUM.find(([k]) => k === w) || [, null])[1];
const NUMWORD = '(\\d{1,2}|열두|열한|다섯|여섯|일곱|여덟|아홉|한|두|세|네|열)';

// said: 어르신이 실제로 쓴 표현 (from/to/place). 상담원 화면에 "어르신 표현"으로 보여 준다.
function localUnderstand(text) {
  const r = { from: [], to: [], place: [], said: {}, placeLow: false, date: null, time: null, timeGuessed: false, people: null, mobilityAid: false, wantsAgent: false, confirm: null };
  const t = text.trim();
  if (/상담원|사람\s*(바꿔|좀|이랑)|직원/.test(t)) r.wantsAgent = true;
  if (/^(네|예|응|어|맞아요?|맞어|그래요?|좋아요?)[\s.!요]*$/.test(t)) r.confirm = 'yes';
  if (/^(아니|아녀|틀려|틀렸)/.test(t)) r.confirm = 'no';
  if (/휠체어|유모차|보행기/.test(t)) r.mobilityAid = true;
  if (/내일/.test(t)) r.date = '내일'; else if (/모레/.test(t)) r.date = '모레'; else if (/오늘/.test(t)) r.date = '오늘';

  // 장소: 긴 말부터 찾고, 찾은 자리는 지워서 겹치지 않게
  let masked = t;
  const high = new Set(), low = new Set();
  for (const a of ALIASES) {
    let i = masked.indexOf(a);
    while (i !== -1) {
      const after = masked.slice(i + a.length, i + a.length + 6);
      const ids = [...aliasMap.get(a)];
      for (const id of ids) (LOW_ALIASES.has(a) ? low : high).add(id);
      const role = /^\s*(앞|옆)?\s*에서/.test(after) ? 'from' : /^\s*(앞|옆)?\s*(까지|으로|로|에\s*가|가|갈)/.test(after) ? 'to' : 'place';
      r[role].push(...ids);
      r.said[role] = r.said[role] ? r.said[role] + ', ' + a : a;
      masked = masked.slice(0, i) + ' '.repeat(a.length) + masked.slice(i + a.length);
      i = masked.indexOf(a);
    }
  }
  r.placeLow = [...low].some(id => !high.has(id)); // "읍내 터미널"처럼 정확한 말도 함께 했으면 추정 아님
  // "방림리요", "계촌이요"처럼 마을 이름만 말한 경우: 그 마을의 정류소 후보로
  if (!r.from.length && !r.to.length && !r.place.length) {
    for (const s of STOPS) { const head = s.name.split(' ')[0].replace(/리$/, ''); if (head.length >= 2 && t.includes(head)) r.place.push(s.id); }
  }

  const tm = t.match(new RegExp('(오전|오후|아침|저녁|낮|밤)?\\s*' + NUMWORD + '\\s*시\\s*(반|(\\d{1,2})\\s*분)?'));
  if (tm) {
    let h = knum(tm[2]); const m = tm[3] === '반' ? 30 : (tm[4] ? Number(tm[4]) : 0);
    const part = tm[1];
    if (part === '오후' || part === '저녁' || part === '밤' || part === '낮') { if (h < 12) h += 12; }
    else if (!part) { if (h >= 1 && h <= 6) { h += 12; r.timeGuessed = true; } }
    if (h !== null && h >= 0 && h <= 23) r.time = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
  }
  const pm = t.match(new RegExp(NUMWORD + '\\s*(명|사람)')) || t.match(/(둘|셋|넷)이/);
  if (pm) r.people = { 둘: 2, 셋: 3, 넷: 4 }[pm[1]] || knum(pm[1]);
  return r;
}

const fmtTime = t => { if (!t) return ''; const [h, m] = t.split(':').map(Number); const ap = h < 12 ? '오전' : '오후'; const hh = h % 12 || 12; return `${ap} ${hh}시${m ? ' ' + m + '분' : ''}`; };
