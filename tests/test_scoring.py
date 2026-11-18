from app.models import Item, ItemOption
from app.services import framework as fw
from app.services.scoring import CompRow, readiness_for, score_item


def mk(qtype, opts=(), **kw):
    it = Item(code="X", competency_code="C01", qtype=qtype, stem_en="q", marks=kw.pop("marks", 1.0), **kw)
    for i, o in enumerate(opts):
        op = ItemOption(id=i + 1, ord=i, text_en=str(i), **o)
        it.options.append(op)
    return it


def test_single_and_negative():
    it = mk("single", [dict(is_correct=False), dict(is_correct=True)], negative_marks=0.25)
    assert score_item(it, {"choice": 2})[:2] == (1.0, True)
    assert score_item(it, {"choice": 1})[0] == -0.25
    assert score_item(it, None)[0] == 0.0


def test_multi_partial():
    it = mk("multi", [dict(is_correct=True), dict(is_correct=True), dict(is_correct=False)], partial_credit=True)
    assert score_item(it, {"choices": [1, 2]})[0] == 1.0
    assert score_item(it, {"choices": [1]})[0] == 0.5
    assert score_item(it, {"choices": [1, 3]})[0] == 0.0
    it.partial_credit = False
    assert score_item(it, {"choices": [1]})[0] == 0.0


def test_sjt_weights():
    it = mk("sjt", [dict(weight=0.0), dict(weight=1.0), dict(weight=0.5)], marks=2)
    assert score_item(it, {"choice": 3})[0] == 1.0
    assert score_item(it, {"choice": 2})[:2] == (2.0, True)


def test_sequence_and_match():
    seq = mk("sequence", [dict(seq_position=2), dict(seq_position=1), dict(seq_position=3)], partial_credit=True)
    assert score_item(seq, {"order": [2, 1, 3]})[:2] == (1.0, True)
    assert round(score_item(seq, {"order": [1, 2, 3]})[0], 3) == round(1 / 3, 3)
    m = mk("match", [dict(match_en="a"), dict(match_en="b")], partial_credit=True)
    assert score_item(m, {"pairs": {"1": 1, "2": 2}})[1] is True
    assert score_item(m, {"pairs": {"1": 2, "2": 1}})[0] == 0.0


def test_numeric_fill_manual():
    n = mk("numeric", answer_numeric=0.8, numeric_tolerance=0.01)
    assert score_item(n, {"value": "0.805"})[1] is True
    assert score_item(n, {"value": "abc"})[0] == 0.0
    f = mk("fill_blank", accepted_answers=["48", "४८"])
    assert score_item(f, {"text": " ४८ "})[1] is True
    w = mk("short_text", rubric_en="r")
    assert score_item(w, {"text": "hello"}) == (None, None, True)
    assert score_item(w, {"text": ""})[2] is False


def test_levels_and_required():
    cuts = fw.DEFAULT_CUTS
    assert [fw.level_for(s, cuts) for s in (0, 39.9, 40, 55, 70, 85, 100)] == [1, 1, 2, 3, 4, 5, 5]
    class G: core_offset, behavioural_offset, domain_offset = 0, -1, -1
    assert fw.required_level(3, G, "Domain") == 2
    assert fw.required_level(1, G, "Domain") == 1
    assert fw.required_level(0, G, "Domain") == 0


def test_composite_redistributes():
    w = {"knowledge": 40, "sjt": 15, "practical": 35, "rating": 10}
    assert fw.composite({"knowledge": 80}, w) == 80
    assert fw.composite({"knowledge": 80, "practical": 40}, w) == round((80 * 40 + 40 * 35) / 75, 1)


def test_readiness_rules():
    def row(safety, req, lvl):
        r = CompRow("X", "", "", "Domain", safety, req)
        r.latest, r.level, r.gap = 60, lvl, max(0, req - lvl)
        return r
    assert readiness_for([row(True, 4, 3), row(False, 2, 3)])[0] == "Red"
    assert readiness_for([row(False, 4, 2), row(False, 2, 3)])[0] == "Amber"
    assert readiness_for([row(False, 3, 3)] * 9 + [row(False, 3, 2)])[0] == "Green"
