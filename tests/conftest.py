import os
import tempfile

_tmp = tempfile.mkdtemp()
os.environ["DATABASE_URL"] = f"sqlite:///{_tmp}/test.db"
os.environ["MEDIA_DIR"] = f"{_tmp}/media"
os.environ["DEMO_MODE"] = "true"
os.environ["AUTH_MODES"] = "local,portal_jwt"
os.environ["PORTAL_JWT_SECRET"] = "test-portal-secret-key-32-bytes-long!!"

import pytest  # noqa: E402


@pytest.fixture(scope="session")
def seeded():
    from seed.seed import main
    main(["--demo"])
    return True


@pytest.fixture()
def db(seeded):
    from app.db import SessionLocal
    s = SessionLocal()
    yield s
    s.close()


@pytest.fixture()
def client(seeded):
    from fastapi.testclient import TestClient
    from app.main import app
    with TestClient(app) as c:
        yield c
