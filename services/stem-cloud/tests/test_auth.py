from datetime import datetime, timedelta, timezone

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from rajtune_stem_cloud.app import create_app
from rajtune_stem_cloud.auth import (
    AuthConfigurationError, AuthSettings, InvalidBearerToken, TokenVerificationUnavailable, TokenVerifier,
)


class FakeJwks:
    def __init__(self, key):
        self.key = key

    def get_signing_key_from_jwt(self, _token):
        return type("SigningKey", (), {"key": self.key})()


@pytest.fixture
def keypair():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


def settings():
    return AuthSettings("https://identity.example.test", "rajtune-stem-api", "https://identity.example.test/.well-known/jwks.json")


def issue_token(key, **changes):
    now = datetime.now(timezone.utc)
    claims = {
        "iss": "https://identity.example.test",
        "aud": "rajtune-stem-api",
        "sub": "user-123",
        "iat": now,
        "exp": now + timedelta(minutes=5),
    }
    claims.update(changes)
    return jwt.encode(claims, key, algorithm="RS256", headers={"kid": "test-key"})


def test_configuration_fails_closed_when_issuer_fields_missing():
    assert AuthSettings.from_env({}) is None
    trailing_slash_issuer = AuthSettings.from_env({
        "RAJTUNE_AUTH_ISSUER": "https://identity.example.test/",
        "RAJTUNE_AUTH_AUDIENCE": "rajtune-stem-api",
        "RAJTUNE_AUTH_JWKS_URL": "https://identity.example.test/keys",
    })
    assert trailing_slash_issuer.issuer == "https://identity.example.test/"
    with pytest.raises(AuthConfigurationError):
        AuthSettings.from_env({"RAJTUNE_AUTH_ISSUER": "https://id.test", "RAJTUNE_AUTH_AUDIENCE": "api", "RAJTUNE_AUTH_JWKS_URL": "http://id.test/jwks"})


def test_verifies_valid_token_and_returns_only_subject(keypair):
    verifier = TokenVerifier(settings(), FakeJwks(keypair.public_key()))
    assert verifier.verify(issue_token(keypair)) == "user-123"


@pytest.mark.parametrize("claims", [
    {"iss": "https://other.test"},
    {"aud": "another-api"},
    {"sub": ""},
    {"exp": datetime.now(timezone.utc) - timedelta(minutes=1)},
])
def test_rejects_wrong_claims(keypair, claims):
    verifier = TokenVerifier(settings(), FakeJwks(keypair.public_key()))
    with pytest.raises(InvalidBearerToken):
        verifier.verify(issue_token(keypair, **claims))


def test_rejects_non_rs256(keypair):
    token = jwt.encode({"sub": "user-123"}, keypair, algorithm="RS256", headers={"kid": "x"})
    # Alter the declared algorithm without changing the signature; verifier must reject before key lookup.
    parts = token.split(".")
    import base64, json
    header = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).decode().rstrip("=")
    forged = ".".join([header, parts[1], parts[2]])
    with pytest.raises(InvalidBearerToken):
        TokenVerifier(settings(), FakeJwks(keypair.public_key())).verify(forged)


def test_api_never_accepts_upload_without_configured_auth_or_processor():
    client = TestClient(create_app())
    assert client.get("/healthz").json() == {"status": "ok", "stem_processing_ready": False}
    assert client.post("/v1/stem-jobs").status_code == 401
    unconfigured = TestClient(create_app(settings=None))
    response = unconfigured.post("/v1/stem-jobs", headers={"Authorization": "Bearer test"})
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "auth_unconfigured"


def test_jwks_outage_fails_closed_as_service_unavailable(keypair):
    class OfflineJwks:
        def get_signing_key_from_jwt(self, _token):
            raise jwt.PyJWKClientConnectionError("simulated offline issuer")

    with pytest.raises(TokenVerificationUnavailable):
        TokenVerifier(settings(), OfflineJwks()).verify(issue_token(keypair))
