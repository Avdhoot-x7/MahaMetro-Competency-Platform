"""Runtime configuration. Every value can be overridden with an environment variable."""
import os
from functools import lru_cache
from pathlib import Path


def _bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


def _list(name: str, default: str) -> list[str]:
    return [x.strip() for x in os.getenv(name, default).split(",") if x.strip()]


BASE_DIR = Path(__file__).resolve().parent.parent


class Settings:
    def __init__(self) -> None:
        self.app_name = os.getenv("APP_NAME", "Maha Metro CoE – Competency Assessment")
        self.environment = os.getenv("ENVIRONMENT", "development")  # development | production
        self.database_url = os.getenv(
            "DATABASE_URL", "postgresql+psycopg://mm:mm@localhost:5432/competency"
        )
        self.secret_key = os.getenv("SECRET_KEY", "change-me-in-production-please-32+chars")
        self.session_max_age = int(os.getenv("SESSION_MAX_AGE", str(8 * 3600)))
        self.https_only_cookies = _bool("HTTPS_ONLY_COOKIES", self.environment == "production")
        self.media_dir = Path(os.getenv("MEDIA_DIR", str(BASE_DIR / "data" / "media")))
        self.max_upload_mb = int(os.getenv("MAX_UPLOAD_MB", "50"))
        self.default_lang = os.getenv("DEFAULT_LANG", "en")

        # Authentication: any combination of oidc, portal_jwt, local
        self.auth_modes = _list("AUTH_MODES", "local")
        # Demo mode shows a one-click role picker on the login page. NEVER enable in production.
        self.demo_mode = _bool("DEMO_MODE", False)
        # In production, password login is only allowed for these roles (break-glass)
        self.local_login_roles = _list("LOCAL_LOGIN_ROLES", "superadmin,cell")

        # OIDC (Keycloak or any OpenID Connect provider)
        self.oidc_issuer = os.getenv("OIDC_ISSUER", "")  # e.g. https://sso.mahametro.org/realms/mahametro
        self.oidc_client_id = os.getenv("OIDC_CLIENT_ID", "competency-portal")
        self.oidc_client_secret = os.getenv("OIDC_CLIENT_SECRET", "")
        self.oidc_redirect_uri = os.getenv("OIDC_REDIRECT_URI", "")  # https://host/auth/callback
        self.oidc_emp_id_claim = os.getenv("OIDC_EMP_ID_CLAIM", "employee_id")
        self.oidc_fallback_claim = os.getenv("OIDC_FALLBACK_CLAIM", "preferred_username")
        self.oidc_roles_claim = os.getenv("OIDC_ROLES_CLAIM", "groups")
        # Map IdP group/role names to application roles: "cell=CoE-Assessment-Cell;hod=HOD"
        self.oidc_role_map = {
            k.strip(): v.strip()
            for k, v in (p.split("=", 1) for p in os.getenv("OIDC_ROLE_MAP", "").split(";") if "=" in p)
        }
        self.oidc_auto_create = _bool("OIDC_AUTO_CREATE", False)

        # Signed hand-off token from the Maha Metro digital portal (HS256 secret or RS256 public key)
        self.portal_jwt_secret = os.getenv("PORTAL_JWT_SECRET", "")
        self.portal_jwt_public_key_file = os.getenv("PORTAL_JWT_PUBLIC_KEY_FILE", "")
        self.portal_jwt_issuer = os.getenv("PORTAL_JWT_ISSUER", "")
        self.portal_jwt_audience = os.getenv("PORTAL_JWT_AUDIENCE", "competency-portal")
        self.portal_jwt_emp_claim = os.getenv("PORTAL_JWT_EMP_CLAIM", "sub")

        # Assessment policy
        self.autosave_seconds = int(os.getenv("AUTOSAVE_SECONDS", "5"))
        self.max_tab_switches_default = int(os.getenv("MAX_TAB_SWITCHES", "5"))
        self.readiness_index_amber = float(os.getenv("READINESS_INDEX_AMBER", "70"))
        self.practice_items_per_competency = int(os.getenv("PRACTICE_ITEMS_PER_COMPETENCY", "2"))

    @property
    def is_production(self) -> bool:
        return self.environment == "production"


@lru_cache
def get_settings() -> Settings:
    return Settings()
