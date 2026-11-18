"""Response-time analysis: spots answers given too fast to have been read (rapid guessing).

Per question, an answer is "rapid" when the time spent on it is below a threshold:
  * once the question has 20+ timed answers in official tests: 10 % of its median time (normative threshold,
    Wise & Ma 2012), but never less than RAPID_MIN_SECONDS;
  * before that: RAPID_MIN_SECONDS plus a minimal skim-reading time of 0.15 s per word of the question
    (about 400 words a minute), capped at 15 s.
Response-time effort (RTE) = share of answered questions that were NOT rapid (Wise & Kong 2005).
An official attempt with more than RAPID_FLAG_SHARE rapid answers (RTE below 90 %) is sent to the Cell for review.
"""
from __future__ import annotations

import os
import statistics
from collections import defaultdict

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Attempt, AttemptItem, Item

RAPID_MIN_SECONDS = float(os.getenv("RAPID_MIN_SECONDS", "3"))
RAPID_FLAG_SHARE = float(os.getenv("RAPID_FLAG_SHARE", "0.10"))
MIN_NORM_N = 20


def item_norms(db: Session, item_ids: list[int]) -> dict[int, float]:
    """Median answering time per question from official, timed answers."""
    if not item_ids:
        return {}
    rows = db.execute(
        select(AttemptItem.item_id, AttemptItem.time_spent_s).join(Attempt)
        .where(AttemptItem.item_id.in_(item_ids), Attempt.kind != "PRACTICE",
               AttemptItem.time_spent_s > 0, AttemptItem.response.is_not(None))
    ).all()
    by: dict[int, list[int]] = defaultdict(list)
    for iid, t in rows:
        by[iid].append(t)
    return {iid: statistics.median(v) for iid, v in by.items() if len(v) >= MIN_NORM_N}


def threshold(item: Item, norms: dict[int, float]) -> float:
    if item.id in norms:
        return max(RAPID_MIN_SECONDS, 0.10 * norms[item.id])
    words = len((item.stem_en or "").split())
    if item.passage_id:
        words += 20  # the passage was usually read on an earlier question of the set
    return min(15.0, RAPID_MIN_SECONDS + 0.15 * words)


def analyse(db: Session, att: Attempt, norms: dict[int, float] | None = None) -> dict:
    """Timing summary for one attempt and per-question verdicts {attempt_item_id: (seconds, limit, rapid)}."""
    items = list(att.items)
    if norms is None:
        norms = item_norms(db, [ai.item_id for ai in items])
    per = {}
    answered = rapid = 0
    times = []
    for ai in items:
        lim = threshold(ai.item, norms)
        has_answer = bool(ai.response)
        is_rapid = has_answer and ai.time_spent_s < lim
        per[ai.id] = (ai.time_spent_s, round(lim, 1), is_rapid)
        if has_answer:
            answered += 1
            rapid += 1 if is_rapid else 0
            times.append(ai.time_spent_s)
    rte = round(100 * (answered - rapid) / answered, 1) if answered else None
    return {
        "total_s": sum(ai.time_spent_s for ai in items),
        "median_s": statistics.median(times) if times else None,
        "answered": answered, "rapid": rapid, "rte": rte,
        "low_effort": answered > 0 and rapid / answered > RAPID_FLAG_SHARE,
        "per": per,
    }
