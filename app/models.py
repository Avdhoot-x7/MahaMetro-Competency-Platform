"""Database schema (PostgreSQL in production; SQLite works for unit tests)."""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import (
    JSON, Boolean, DateTime, Float, ForeignKey, Index, Integer, String, Text, UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


# ---------------------------------------------------------------- roles
ROLES = ["employee", "supervisor", "hod", "sme", "reviewer", "cell", "management", "superadmin"]

# ---------------------------------------------------------------- item types
QTYPES = [
    "single",        # single correct answer
    "multi",         # multiple correct answers
    "truefalse",     # true / false
    "sjt",           # situational judgement: weighted options (best 1.0, acceptable 0.5, ...)
    "sequence",      # put steps in the right order
    "match",         # match the pairs
    "numeric",       # numeric answer with tolerance
    "fill_blank",    # short typed answer matched against accepted answers
    "short_text",    # written answer, scored manually against a rubric
    "audio_response",  # spoken answer recorded in the browser, scored manually
]
AUTO_SCORED = {"single", "multi", "truefalse", "sjt", "sequence", "match", "numeric", "fill_blank"}
BLOOM = ["Remember", "Understand", "Apply", "Analyse"]
TEST_KINDS = ["PRACTICE", "T0", "T1", "T2", "T3", "EVENT"]
CYCLE_ORDER = {"T0": 0, "T1": 1, "T2": 2, "T3": 3, "EVENT": 4}


class RoleFamily(Base):
    __tablename__ = "role_family"
    code: Mapped[str] = mapped_column(String(8), primary_key=True)
    name_en: Mapped[str] = mapped_column(String(120))
    name_mr: Mapped[str] = mapped_column(String(160), default="")
    group: Mapped[str] = mapped_column(String(8), default="NT")  # OPS | MNT | PJS | NT


class Grade(Base):
    __tablename__ = "grade"
    code: Mapped[str] = mapped_column(String(4), primary_key=True)
    band_en: Mapped[str] = mapped_column(String(60))
    band_mr: Mapped[str] = mapped_column(String(80), default="")
    core_offset: Mapped[int] = mapped_column(Integer, default=0)
    behavioural_offset: Mapped[int] = mapped_column(Integer, default=0)
    domain_offset: Mapped[int] = mapped_column(Integer, default=0)


class Competency(Base):
    __tablename__ = "competency"
    code: Mapped[str] = mapped_column(String(8), primary_key=True)
    name_en: Mapped[str] = mapped_column(String(160))
    name_mr: Mapped[str] = mapped_column(String(200), default="")
    category: Mapped[str] = mapped_column(String(20))  # Core | Behavioural | Domain
    safety_critical: Mapped[bool] = mapped_column(Boolean, default=False)
    description_en: Mapped[str] = mapped_column(Text, default="")
    description_mr: Mapped[str] = mapped_column(Text, default="")
    methods: Mapped[str] = mapped_column(String(200), default="")


class RoleProfile(Base):
    __tablename__ = "role_profile"
    role_family_code: Mapped[str] = mapped_column(ForeignKey("role_family.code"), primary_key=True)
    competency_code: Mapped[str] = mapped_column(ForeignKey("competency.code"), primary_key=True)
    base_level: Mapped[int] = mapped_column(Integer, default=0)
    approved_by: Mapped[str | None] = mapped_column(String(30), nullable=True)
    approved_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class CutScore(Base):
    __tablename__ = "cut_score"
    level: Mapped[int] = mapped_column(Integer, primary_key=True)
    min_score: Mapped[float] = mapped_column(Float)
    label_en: Mapped[str] = mapped_column(String(40))
    label_mr: Mapped[str] = mapped_column(String(60), default="")


class DesignationMap(Base):
    __tablename__ = "designation_map"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    designation: Mapped[str] = mapped_column(String(160), unique=True)
    role_family_code: Mapped[str | None] = mapped_column(ForeignKey("role_family.code"), nullable=True)
    grade_code: Mapped[str | None] = mapped_column(ForeignKey("grade.code"), nullable=True)


class Employee(Base):
    __tablename__ = "employee"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    emp_id: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    name_en: Mapped[str] = mapped_column(String(160))
    name_mr: Mapped[str] = mapped_column(String(200), default="")
    email: Mapped[str] = mapped_column(String(160), default="")
    mobile: Mapped[str] = mapped_column(String(20), default="")
    designation: Mapped[str] = mapped_column(String(160), default="")
    department: Mapped[str] = mapped_column(String(120), default="", index=True)
    location: Mapped[str] = mapped_column(String(120), default="")
    role_family_code: Mapped[str | None] = mapped_column(ForeignKey("role_family.code"), nullable=True)
    grade_code: Mapped[str | None] = mapped_column(ForeignKey("grade.code"), nullable=True)
    reporting_officer: Mapped[str] = mapped_column(String(30), default="", index=True)  # emp_id
    roles: Mapped[list] = mapped_column(JSON, default=lambda: ["employee"])
    preferred_lang: Mapped[str] = mapped_column(String(2), default="en")
    extra_time_pct: Mapped[int] = mapped_column(Integer, default=0)  # accommodation for divyangjan
    password_hash: Mapped[str | None] = mapped_column(String(100), nullable=True)
    active: Mapped[bool] = mapped_column(Boolean, default=True)
    consent_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    last_login: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    source: Mapped[str] = mapped_column(String(20), default="manual")  # sap_hr | sso | manual
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)

    role_family: Mapped[RoleFamily | None] = relationship()
    grade: Mapped[Grade | None] = relationship()

    def has(self, *roles: str) -> bool:
        r = set(self.roles or [])
        return "superadmin" in r or bool(r.intersection(roles))

    def name(self, lang: str) -> str:
        return (self.name_mr if lang == "mr" and self.name_mr else self.name_en)


class Media(Base):
    __tablename__ = "media"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    kind: Mapped[str] = mapped_column(String(10))  # audio | video | image | webcam | response
    filename: Mapped[str] = mapped_column(String(200))
    stored_name: Mapped[str] = mapped_column(String(120), unique=True)
    mime: Mapped[str] = mapped_column(String(80))
    size: Mapped[int] = mapped_column(Integer, default=0)
    restricted: Mapped[bool] = mapped_column(Boolean, default=False)  # webcam / answers: staff only
    owner_emp_id: Mapped[str] = mapped_column(String(30), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Passage(Base):
    __tablename__ = "passage"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(30), unique=True)
    title_en: Mapped[str] = mapped_column(String(200), default="")
    title_mr: Mapped[str] = mapped_column(String(260), default="")
    body_en: Mapped[str] = mapped_column(Text, default="")
    body_mr: Mapped[str] = mapped_column(Text, default="")
    media_id: Mapped[int | None] = mapped_column(ForeignKey("media.id"), nullable=True)
    media: Mapped[Media | None] = relationship()


class Item(Base):
    __tablename__ = "item"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(30), unique=True, index=True)
    competency_code: Mapped[str] = mapped_column(ForeignKey("competency.code"), index=True)
    role_families: Mapped[list] = mapped_column(JSON, default=lambda: ["ALL"])
    target_level: Mapped[int] = mapped_column(Integer, default=3)
    bloom: Mapped[str] = mapped_column(String(12), default="Understand")
    qtype: Mapped[str] = mapped_column(String(20), default="single")
    stem_en: Mapped[str] = mapped_column(Text)
    stem_mr: Mapped[str] = mapped_column(Text, default="")
    media_id: Mapped[int | None] = mapped_column(ForeignKey("media.id"), nullable=True)
    passage_id: Mapped[int | None] = mapped_column(ForeignKey("passage.id"), nullable=True, index=True)
    passage_order: Mapped[int] = mapped_column(Integer, default=0)
    shuffle_options: Mapped[bool] = mapped_column(Boolean, default=True)
    marks: Mapped[float] = mapped_column(Float, default=1.0)
    negative_marks: Mapped[float] = mapped_column(Float, default=0.0)
    partial_credit: Mapped[bool] = mapped_column(Boolean, default=False)
    answer_numeric: Mapped[float | None] = mapped_column(Float, nullable=True)
    numeric_tolerance: Mapped[float] = mapped_column(Float, default=0.0)
    accepted_answers: Mapped[list] = mapped_column(JSON, default=list)  # fill_blank
    rubric_en: Mapped[str] = mapped_column(Text, default="")  # short_text / audio_response
    rubric_mr: Mapped[str] = mapped_column(Text, default="")
    rationale_en: Mapped[str] = mapped_column(Text, default="")
    rationale_mr: Mapped[str] = mapped_column(Text, default="")
    source_ref: Mapped[str] = mapped_column(String(300), default="")
    pool: Mapped[str] = mapped_column(String(10), default="exam")  # exam | practice | both
    status: Mapped[str] = mapped_column(String(12), default="draft", index=True)
    # draft | review | approved | retired
    needs_sme_validation: Mapped[bool] = mapped_column(Boolean, default=False)
    author_emp_id: Mapped[str] = mapped_column(String(30), default="")
    reviewer_emp_id: Mapped[str] = mapped_column(String(30), default="")
    review_comment: Mapped[str] = mapped_column(Text, default="")
    version: Mapped[int] = mapped_column(Integer, default=1)
    exposure_count: Mapped[int] = mapped_column(Integer, default=0)
    p_value: Mapped[float | None] = mapped_column(Float, nullable=True)
    point_biserial: Mapped[float | None] = mapped_column(Float, nullable=True)
    stats_n: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)

    competency: Mapped[Competency] = relationship()
    media: Mapped[Media | None] = relationship()
    passage: Mapped[Passage | None] = relationship()
    options: Mapped[list[ItemOption]] = relationship(
        back_populates="item", cascade="all, delete-orphan", order_by="ItemOption.ord"
    )


class ItemOption(Base):
    __tablename__ = "item_option"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    item_id: Mapped[int] = mapped_column(ForeignKey("item.id", ondelete="CASCADE"), index=True)
    ord: Mapped[int] = mapped_column(Integer, default=0)
    text_en: Mapped[str] = mapped_column(Text)
    text_mr: Mapped[str] = mapped_column(Text, default="")
    is_correct: Mapped[bool] = mapped_column(Boolean, default=False)
    weight: Mapped[float | None] = mapped_column(Float, nullable=True)  # SJT credit 0..1
    seq_position: Mapped[int | None] = mapped_column(Integer, nullable=True)  # sequence: correct position
    match_en: Mapped[str] = mapped_column(Text, default="")  # match: right-hand side
    match_mr: Mapped[str] = mapped_column(Text, default="")
    item: Mapped[Item] = relationship(back_populates="options")


class Blueprint(Base):
    __tablename__ = "blueprint"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(160))
    role_family_code: Mapped[str | None] = mapped_column(ForeignKey("role_family.code"), nullable=True)
    grade_code: Mapped[str | None] = mapped_column(ForeignKey("grade.code"), nullable=True)
    # list of {"competency": "D01", "bloom": "Apply" | null, "count": 3}
    rules: Mapped[list] = mapped_column(JSON, default=list)
    notes: Mapped[str] = mapped_column(Text, default="")


class Test(Base):
    __tablename__ = "test"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(40), unique=True)
    title_en: Mapped[str] = mapped_column(String(200))
    title_mr: Mapped[str] = mapped_column(String(260), default="")
    instructions_en: Mapped[str] = mapped_column(Text, default="")
    instructions_mr: Mapped[str] = mapped_column(Text, default="")
    kind: Mapped[str] = mapped_column(String(10), default="T0")
    blueprint_id: Mapped[int | None] = mapped_column(ForeignKey("blueprint.id"), nullable=True)
    items_per_competency: Mapped[int] = mapped_column(Integer, default=3)
    max_items: Mapped[int] = mapped_column(Integer, default=80)
    competency_filter: Mapped[list] = mapped_column(JSON, default=list)  # T1/T2 targeted tests
    category_filter: Mapped[list] = mapped_column(JSON, default=list)  # e.g. ["Domain"] for Paper A
    duration_min: Mapped[int] = mapped_column(Integer, default=90)
    start_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    end_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    max_attempts: Mapped[int] = mapped_column(Integer, default=1)  # 0 = unlimited
    shuffle_questions: Mapped[bool] = mapped_column(Boolean, default=True)
    shuffle_options: Mapped[bool] = mapped_column(Boolean, default=True)
    show_feedback: Mapped[bool] = mapped_column(Boolean, default=False)
    allow_back: Mapped[bool] = mapped_column(Boolean, default=True)
    # {"role_families": [], "grades": [], "departments": [], "locations": [], "emp_ids": []}
    target: Mapped[dict] = mapped_column(JSON, default=dict)
    # {"fullscreen": true, "webcam": false, "max_tab_switches": 5, "snapshot_seconds": 60}
    proctoring: Mapped[dict] = mapped_column(JSON, default=dict)
    status: Mapped[str] = mapped_column(String(12), default="draft")  # draft | published | closed
    created_by: Mapped[str] = mapped_column(String(30), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    blueprint: Mapped[Blueprint | None] = relationship()

    @property
    def is_practice(self) -> bool:
        return self.kind == "PRACTICE"

    def title(self, lang: str) -> str:
        return self.title_mr if lang == "mr" and self.title_mr else self.title_en


class Attempt(Base):
    __tablename__ = "attempt"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    test_id: Mapped[int] = mapped_column(ForeignKey("test.id"), index=True)
    employee_id: Mapped[int] = mapped_column(ForeignKey("employee.id"), index=True)
    kind: Mapped[str] = mapped_column(String(10))
    seed: Mapped[int] = mapped_column(Integer)
    lang: Mapped[str] = mapped_column(String(2), default="en")
    started_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    deadline_at: Mapped[datetime] = mapped_column(DateTime)
    submitted_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # in_progress | pending_manual | scored | flagged | void
    status: Mapped[str] = mapped_column(String(16), default="in_progress", index=True)
    score: Mapped[float] = mapped_column(Float, default=0)
    max_score: Mapped[float] = mapped_column(Float, default=0)
    percent: Mapped[float | None] = mapped_column(Float, nullable=True)
    tab_switches: Mapped[int] = mapped_column(Integer, default=0)
    flag_reason: Mapped[str] = mapped_column(Text, default="")
    reviewed_by: Mapped[str] = mapped_column(String(30), default="")
    client_info: Mapped[str] = mapped_column(String(300), default="")
    summary: Mapped[dict] = mapped_column(JSON, default=dict)  # per-competency results
    test: Mapped[Test] = relationship()
    employee: Mapped[Employee] = relationship()
    items: Mapped[list[AttemptItem]] = relationship(
        back_populates="attempt", cascade="all, delete-orphan", order_by="AttemptItem.position"
    )


class AttemptItem(Base):
    __tablename__ = "attempt_item"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    attempt_id: Mapped[int] = mapped_column(ForeignKey("attempt.id", ondelete="CASCADE"), index=True)
    item_id: Mapped[int] = mapped_column(ForeignKey("item.id"), index=True)
    position: Mapped[int] = mapped_column(Integer)
    option_order: Mapped[dict] = mapped_column(JSON, default=dict)  # {"options": [...], "right": [...]}
    response: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    answered_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    flagged_for_review: Mapped[bool] = mapped_column(Boolean, default=False)
    time_spent_s: Mapped[int] = mapped_column(Integer, default=0)
    auto_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    manual_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    final_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    max_score: Mapped[float] = mapped_column(Float, default=1)
    is_correct: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    needs_manual: Mapped[bool] = mapped_column(Boolean, default=False)
    scored_by: Mapped[str] = mapped_column(String(30), default="")
    scorer_comment: Mapped[str] = mapped_column(Text, default="")
    attempt: Mapped[Attempt] = relationship(back_populates="items")
    item: Mapped[Item] = relationship()


class ProctorEvent(Base):
    __tablename__ = "proctor_event"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    attempt_id: Mapped[int] = mapped_column(ForeignKey("attempt.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(30))  # tab_hidden | fullscreen_exit | copy | paste | snapshot | resume
    detail: Mapped[str] = mapped_column(String(300), default="")
    media_id: Mapped[int | None] = mapped_column(ForeignKey("media.id"), nullable=True)
    at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class CompetencyScore(Base):
    """One measured score (0-100) for an employee, competency, cycle and method."""
    __tablename__ = "competency_score"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    employee_id: Mapped[int] = mapped_column(ForeignKey("employee.id"), index=True)
    competency_code: Mapped[str] = mapped_column(ForeignKey("competency.code"))
    cycle: Mapped[str] = mapped_column(String(10))  # T0 | T1 | T2 | T3 | EVENT
    method: Mapped[str] = mapped_column(String(12))  # knowledge | sjt | practical | rating
    score: Mapped[float] = mapped_column(Float)
    attempt_id: Mapped[int | None] = mapped_column(ForeignKey("attempt.id"), nullable=True)
    recorded_by: Mapped[str] = mapped_column(String(30), default="")
    comment: Mapped[str] = mapped_column(Text, default="")
    recorded_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    __table_args__ = (Index("ix_cs_emp_comp_cycle", "employee_id", "competency_code", "cycle"),)


class Intervention(Base):
    __tablename__ = "intervention"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    competency_code: Mapped[str | None] = mapped_column(ForeignKey("competency.code"), nullable=True)
    category: Mapped[str | None] = mapped_column(String(20), nullable=True)  # generic by category
    gap_type: Mapped[str] = mapped_column(String(12))  # safety | major | minor | behavioural | strength
    title_en: Mapped[str] = mapped_column(String(300))
    title_mr: Mapped[str] = mapped_column(String(400), default="")
    provider: Mapped[str] = mapped_column(String(60), default="CoE")  # CoE | OEM | iGOT | OJT | Lab
    mode: Mapped[str] = mapped_column(String(40), default="Classroom")
    duration_hours: Mapped[float] = mapped_column(Float, default=4)
    link: Mapped[str] = mapped_column(String(300), default="")


class DevelopmentPlan(Base):
    __tablename__ = "development_plan"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    employee_id: Mapped[int] = mapped_column(ForeignKey("employee.id"), index=True)
    cycle: Mapped[str] = mapped_column(String(10))
    readiness: Mapped[str] = mapped_column(String(6))  # Red | Amber | Green
    competency_index: Mapped[float] = mapped_column(Float, default=0)
    status: Mapped[str] = mapped_column(String(12), default="proposed")  # proposed | confirmed | closed
    confirmed_by: Mapped[str] = mapped_column(String(30), default="")
    confirmed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    employee: Mapped[Employee] = relationship()
    items: Mapped[list[DevelopmentPlanItem]] = relationship(
        back_populates="plan", cascade="all, delete-orphan", order_by="DevelopmentPlanItem.priority"
    )


class DevelopmentPlanItem(Base):
    __tablename__ = "development_plan_item"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    plan_id: Mapped[int] = mapped_column(ForeignKey("development_plan.id", ondelete="CASCADE"), index=True)
    competency_code: Mapped[str] = mapped_column(ForeignKey("competency.code"))
    required_level: Mapped[int] = mapped_column(Integer)
    attained_level: Mapped[int] = mapped_column(Integer)
    score: Mapped[float] = mapped_column(Float)
    gap: Mapped[int] = mapped_column(Integer)
    priority: Mapped[int] = mapped_column(Integer)  # 1 safety, 2 major, 3 minor, 4 behavioural, 9 strength
    gap_type: Mapped[str] = mapped_column(String(12))
    intervention_id: Mapped[int | None] = mapped_column(ForeignKey("intervention.id"), nullable=True)
    target_date: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    status: Mapped[str] = mapped_column(String(12), default="open")  # open | scheduled | done
    plan: Mapped[DevelopmentPlan] = relationship(back_populates="items")
    competency: Mapped[Competency] = relationship()
    intervention: Mapped[Intervention | None] = relationship()


class AuditLog(Base):
    __tablename__ = "audit_log"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, index=True)
    actor: Mapped[str] = mapped_column(String(30), default="")
    action: Mapped[str] = mapped_column(String(60))
    entity: Mapped[str] = mapped_column(String(40), default="")
    entity_id: Mapped[str] = mapped_column(String(40), default="")
    detail: Mapped[dict] = mapped_column(JSON, default=dict)
    ip: Mapped[str] = mapped_column(String(60), default="")


class Setting(Base):
    __tablename__ = "app_setting"
    key: Mapped[str] = mapped_column(String(60), primary_key=True)
    value: Mapped[dict] = mapped_column(JSON, default=dict)
